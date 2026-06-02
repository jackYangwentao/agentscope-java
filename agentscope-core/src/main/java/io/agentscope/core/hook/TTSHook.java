/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.core.hook;

import io.agentscope.core.message.AudioBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.tts.AudioPlayer;
import io.agentscope.core.model.tts.DashScopeRealtimeTTSModel;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Agent 执行期间实时文本转语音合成的 Hook。
 *
 * <p>此 Hook 通过监听流式推理事件并实时合成语音,
 * 实现了"边生成边朗读"功能。
 *
 * <p><b>两种使用模式:</b>
 * <ul>
 *   <li><b>本地播放(CLI/桌面):</b> 使用 audioPlayer 直接播放</li>
 *   <li><b>服务端模式(Web/SSE):</b> 使用 audioCallback 将音频返回给前端</li>
 * </ul>
 *
 * <p>Hook for real-time Text-to-Speech synthesis during agent execution.
 *
 * <p>This hook implements "speak as you generate" by listening to streaming
 * reasoning events and synthesizing speech in real-time.
 *
 * <p><b>Two Usage Modes:</b>
 * <ul>
 *   <li><b>Local Playback (CLI/Desktop):</b> Use audioPlayer for direct playback</li>
 *   <li><b>Server Mode (Web/SSE):</b> Use audioCallback to return audio to frontend</li>
 * </ul>
 */
public class TTSHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(TTSHook.class);

    private final DashScopeRealtimeTTSModel ttsModel;
    private final AudioPlayer audioPlayer;
    private final boolean autoStartPlayer;
    private final boolean realtimeMode;
    private final Consumer<AudioBlock> audioCallback;

    // 用于外部消费者的响应式音频流(如 SSE/WebSocket 到前端)
    // 当新的推理开始时,此 sink 不会被中断 — 前端控制播放
    // Reactive audio stream for external consumers (e.g., SSE/WebSocket to frontend)
    // This sink is NOT interrupted when new reasoning starts - frontend controls playback
    private final Sinks.Many<AudioBlock> audioSink =
            Sinks.many().multicast().onBackpressureBuffer();

    private boolean playerStarted = false;
    private boolean sessionStarted = false;

    private TTSHook(Builder builder) {
        this.ttsModel = builder.ttsModel;
        this.audioPlayer = builder.audioPlayer;
        this.autoStartPlayer = builder.autoStartPlayer;
        this.realtimeMode = builder.realtimeMode;
        this.audioCallback = builder.audioCallback;
    }

    /**
     * 获取响应式音频流。
     *
     * <p>使用此流订阅音频块的生成。适用于 SSE/WebSocket 向前端推送。
     *
     * @return 音频生成时发出的 AudioBlock Flux
     *
     * <p>Gets the reactive audio stream.
     *
     * <p>Use this to subscribe to audio blocks as they are generated.
     * This is useful for SSE/WebSocket streaming to frontend.
     *
     * @return Flux of AudioBlock that emits audio as it's synthesized
     */
    public Flux<AudioBlock> getAudioStream() {
        return audioSink.asFlux();
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (realtimeMode) {
            return handleRealtimeMode(event);
        } else {
            return handleBatchMode(event);
        }
    }

    /**
     * 处理实时模式:在每个块上合成语音。
     *
     * <p>Handle real-time mode: synthesize on each chunk.
     */
    private <T extends HookEvent> Mono<T> handleRealtimeMode(T event) {
        if (event instanceof PreReasoningEvent) {
            // New reasoning is starting - interrupt current playback if any
            interruptCurrentPlayback();
        } else if (event instanceof ReasoningChunkEvent) {
            ReasoningChunkEvent e = (ReasoningChunkEvent) event;
            Msg incrementalChunk = e.getIncrementalChunk();

            if (incrementalChunk != null) {
                String text = incrementalChunk.getTextContent();
                if (text != null && !text.isEmpty()) {
                    if (!sessionStarted) {
                        // New session starting - interrupt any ongoing playback first
                        if (audioPlayer != null && playerStarted) {
                            audioPlayer.interrupt();
                        }

                        ttsModel.startSession();
                        sessionStarted = true;
                        ensurePlayerStarted();

                        // Subscribe to audio stream ONCE at session start
                        // Audio arrives asynchronously via WebSocket callback
                        ttsModel.getAudioStream().doOnNext(this::emitAudio).subscribe();
                    }

                    // Push text - audio delivered via getAudioStream subscription
                    ttsModel.push(text);
                }
            }
        } else if (event instanceof PostReasoningEvent) {
            if (sessionStarted) {
                // finish() commits pending text and closes session
                // Audio continues to arrive via getAudioStream subscription
                ttsModel.finish().doOnComplete(this::drainPlayerAsync).blockLast();
                sessionStarted = false;
            }
        }

        return Mono.just(event);
    }

    /**
     * 处理批量模式:等待完整响应后再合成。
     *
     * <p>Handle batch mode: wait for complete response then synthesize.
     */
    private <T extends HookEvent> Mono<T> handleBatchMode(T event) {
        if (event instanceof PreReasoningEvent) {
            // New reasoning is starting - interrupt current playback if any
            // (In batch mode, this handles cases where audio is still playing from previous
            // response)
            if (audioPlayer != null && playerStarted) {
                audioPlayer.interrupt();
            }
        } else if (event instanceof PostReasoningEvent) {
            PostReasoningEvent e = (PostReasoningEvent) event;
            Msg msg = e.getReasoningMessage();

            if (msg != null) {
                String text = msg.getTextContent();
                if (text != null && !text.isEmpty()) {
                    synthesizeAndEmit(text);
                }
            }
        }

        return Mono.just(event);
    }

    /**
     * 向所有消费者(播放器、回调、流)发送音频。
     *
     * <p>Emit audio to all consumers (player, callback, stream).
     */
    private void emitAudio(AudioBlock audio) {
        // 1. Emit to reactive stream (for SSE/WebSocket consumers)
        // Note: FAIL_ZERO_SUBSCRIBER is normal when no external subscribers
        // (e.g., when only using audioPlayer for local playback)
        Sinks.EmitResult result = audioSink.tryEmitNext(audio);
        if (result.isFailure()) {
            if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                // Normal case when no external subscribers (local playback only)
                log.debug(
                        "No subscribers for audio stream (normal when using local playback only)");
            } else {
                log.warn("Failed to emit audio to sink: {}", result);
            }
        }

        // 2. Call callback if provided
        if (audioCallback != null) {
            audioCallback.accept(audio);
        }

        // 3. Play locally if player is configured
        if (audioPlayer != null) {
            audioPlayer.play(audio);
        }
    }

    /**
     * 确保音频播放器已启动。
     *
     * <p>Ensure audio player is started.
     */
    private void ensurePlayerStarted() {
        if (audioPlayer != null && autoStartPlayer && !playerStarted) {
            audioPlayer.start();
            playerStarted = true;
        }
    }

    /**
     * 当新的推理开始时中断当前播放。
     *
     * <p>此方法:
     * <ul>
     *   <li>中断本地 AudioPlayer — 清空队列并停止当前播放
     *       (即使 TTS 会话已结束,AudioPlayer 可能仍在播放)</li>
     *   <li>如果 TTS 会话活跃则关闭 — 停止从 WebSocket 接收新音频</li>
     *   <li>不中断 audioSink — 前端流继续,允许前端独立控制播放</li>
     * </ul>
     *
     * <p>注意:audioSink(用于前端/SSE 消费者)不会被中断,
     * 因为前端应用可以自行控制音频播放。只有本地播放被中断。
     *
     * <p>Interrupts current playback when a new reasoning starts.
     *
     * <p>This method:
     * <ul>
     *   <li>Interrupts local AudioPlayer - clears queue and stops current playback</li>
     *   <li>Closes current TTS session if active</li>
     *   <li>Does NOT interrupt audioSink - frontend stream continues</li>
     * </ul>
     */
    private void interruptCurrentPlayback() {
        // Always interrupt AudioPlayer if it's started, even if TTS session has ended
        // This handles the case where AudioPlayer is still playing audio from previous response
        if (audioPlayer != null && playerStarted) {
            audioPlayer.interrupt();
            log.debug("Interrupted AudioPlayer (cleared queue, ready for new audio)");
        }

        // Close TTS session only if it's still active
        if (sessionStarted) {
            // Close current TTS session (stops receiving new audio from WebSocket)
            if (ttsModel != null) {
                ttsModel.close();
            }
            sessionStarted = false;
            log.debug("Closed TTS session for new reasoning");
        }

        // Note: audioSink is NOT interrupted - it continues to send audio to frontend
        // Frontend can control playback independently (pause, stop, etc.)
    }

    /**
     * 异步排空音频播放器。
     *
     * <p>确保音频完整播放而不阻塞调用方。排空操作在单独的线程中运行,
     * 使 Agent 在合成完成后能立即返回。
     *
     * <p>Drain the audio player asynchronously.
     *
     * <p>This ensures audio plays completely without blocking the caller.
     */
    private void drainPlayerAsync() {
        if (audioPlayer != null) {
            Mono.fromRunnable(() -> audioPlayer.drain())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe();
        }
    }

    /**
     * 合成完整文本并发送(批量模式)。
     *
     * <p>Synthesize complete text and emit (for batch mode).
     */
    private void synthesizeAndEmit(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        log.debug("Synthesizing text: {}...", text.substring(0, Math.min(50, text.length())));

        ensurePlayerStarted();

        ttsModel.synthesizeStream(text)
                .doOnNext(this::emitAudio)
                .doOnComplete(this::drainPlayerAsync)
                .blockLast();
    }

    /**
     * 停止音频播放器并清理资源。
     *
     * <p>Stop the audio player and clean up resources.
     */
    public void stop() {
        // Close TTS WebSocket connection
        if (ttsModel != null) {
            ttsModel.close();
        }

        if (audioPlayer != null && playerStarted) {
            audioPlayer.stop();
            playerStarted = false;
        }
        sessionStarted = false;
        Sinks.EmitResult result = audioSink.tryEmitComplete();
        if (result.isFailure()) {
            log.warn("Failed to complete audio sink: {}", result);
        }
    }

    /**
     * 创建 TTSHook 的新 Builder。
     *
     * @return 新的 Builder 实例
     *
     * <p>Creates a new builder for TTSHook.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 用于构建 TTSHook 实例的 Builder。
     *
     * <p>Builder for constructing TTSHook instances.
     */
    public static class Builder {
        private DashScopeRealtimeTTSModel ttsModel;
        private AudioPlayer audioPlayer;
        private boolean autoStartPlayer = true;
        private boolean realtimeMode = true;
        private Consumer<AudioBlock> audioCallback;

        /**
         * 设置用于语音合成的 TTS 模型。(必需)
         *
         * @param ttsModel 实时 TTS 模型
         * @return 此 Builder
         *
         * <p>Sets the TTS model for speech synthesis. (Required)
         *
         * @param ttsModel the realtime TTS model
         * @return this builder
         */
        public Builder ttsModel(DashScopeRealtimeTTSModel ttsModel) {
            this.ttsModel = ttsModel;
            return this;
        }

        /**
         * 设置用于本地播放的音频播放器。(可选)
         *
         * <p>如果未设置:
         * <ul>
         *   <li>如果 audioCallback 也未设置:将自动创建默认的 AudioPlayer
         *       (24000 Hz 采样率,单声道,16 位 PCM)用于本地播放。</li>
         *   <li>如果设置了 audioCallback:音频将只能通过 audioCallback
         *       或 getAudioStream() 获取,适合服务端使用。</li>
         * </ul>
         *
         * @param audioPlayer 音频播放器,或 null 以使用默认或服务端模式
         * @return 此 Builder
         *
         * <p>Sets the audio player for local playback. (Optional)
         *
         * <p>If not set:
         * <ul>
         *   <li>If audioCallback is also not set: A default AudioPlayer will be created
         *       automatically (24000 Hz sample rate, mono, 16-bit PCM) for local playback.</li>
         *   <li>If audioCallback is set: Audio will only be available via audioCallback
         *       or getAudioStream(), suitable for server-side usage.</li>
         * </ul>
         *
         * @param audioPlayer the audio player, or null to use default or server mode
         * @return this builder
         */
        public Builder audioPlayer(AudioPlayer audioPlayer) {
            this.audioPlayer = audioPlayer;
            return this;
        }

        /**
         * 设置是否自动启动音频播放器。
         *
         * @param autoStartPlayer true 为自动启动(默认: true)
         * @return 此 Builder
         *
         * <p>Sets whether to auto-start the audio player.
         *
         * @param autoStartPlayer true to auto-start (default: true)
         * @return this builder
         */
        public Builder autoStartPlayer(boolean autoStartPlayer) {
            this.autoStartPlayer = autoStartPlayer;
            return this;
        }

        /**
         * 设置是否使用实时模式。
         *
         * <p>为 true(默认)时,TTS 在 LLM 生成每个文本块时触发。
         * 为 false 时,TTS 等待完整响应后再合成。
         *
         * @param realtimeMode true 为实时"边生成边朗读"(默认)
         * @return 此 Builder
         *
         * <p>Sets whether to use real-time mode.
         *
         * <p>When true (default), TTS is triggered on each text chunk as LLM generates.
         * When false, TTS waits for complete response before synthesis.
         *
         * @param realtimeMode true for real-time "speak as you generate" (default)
         * @return this builder
         */
        public Builder realtimeMode(boolean realtimeMode) {
            this.realtimeMode = realtimeMode;
            return this;
        }

        /**
         * 设置用于接收音频块的回调。(可选)
         *
         * <p>这是服务端处理音频的推荐方式。
         * 回调在合成出每个音频块时被调用。
         *
         * @param audioCallback 接收音频块的回调
         * @return 此 Builder
         *
         * <p>Sets a callback for receiving audio blocks. (Optional)
         *
         * <p>This is the recommended way for server-side usage to handle audio.
         * The callback is invoked for each audio block as it's synthesized.
         *
         * @param audioCallback callback to receive audio blocks
         * @return this builder
         */
        public Builder audioCallback(Consumer<AudioBlock> audioCallback) {
            this.audioCallback = audioCallback;
            return this;
        }

        /**
         * 构建 TTSHook 实例。
         *
         * <p>如果既未提供 audioPlayer 也未提供 audioCallback,
         * 将自动创建默认的 AudioPlayer 用于本地播放(24000 Hz 采样率)。
         *
         * @return 配置完成的 TTSHook
         * @throws IllegalArgumentException 如果未设置 ttsModel
         *
         * <p>Builds the TTSHook instance.
         *
         * <p>If neither audioPlayer nor audioCallback is provided, a default AudioPlayer
         * will be created automatically for local playback (24000 Hz sample rate).
         *
         * @return configured TTSHook
         * @throws IllegalArgumentException if ttsModel is not set
         */
        public TTSHook build() {
            if (ttsModel == null) {
                throw new IllegalArgumentException("TTS model is required");
            }

            // If neither audioPlayer nor audioCallback is set, create a default AudioPlayer
            // for local playback (CLI/desktop mode)
            if (audioPlayer == null && audioCallback == null) {
                audioPlayer =
                        AudioPlayer.builder()
                                .sampleRate(24000) // Default sample rate for TTS models
                                .sampleSizeInBits(16)
                                .channels(1) // Mono
                                .signed(true)
                                .bigEndian(false) // Little-endian
                                .build();
            }

            return new TTSHook(this);
        }
    }
}
