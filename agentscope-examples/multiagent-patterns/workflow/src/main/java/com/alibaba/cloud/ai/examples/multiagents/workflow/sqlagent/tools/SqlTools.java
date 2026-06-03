/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.examples.multiagents.workflow.sqlagent.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AgentScope {@link io.agentscope.core.tool.Tool @Tool} 注解定义的 SQL 数据库操作工具集。
 *
 * <p><b>提供的工具：</b>
 * <ul>
 *   <li>{@code sql_db_list_tables} —— 列出 PUBLIC schema 中的所有基表</li>
 *   <li>{@code sql_db_schema} —— 返回给定表的 CREATE TABLE 语法 + 3 条样本行</li>
 *   <li>{@code sql_db_query} —— 执行 SELECT 查询（出于安全考虑拒绝 DML）</li>
 * </ul>
 *
 * <p>这些工具封装了 {@link JdbcTemplate}，并注册到 {@code generate_query}
 * ReActAgent 中，使其能够自主发现、检查和查询数据库。
 *
 * <p><b>安全：</b>{@code sql_db_query} 阻止 INSERT/UPDATE/DELETE/DROP 操作以防止
 * 意外的数据修改。仅允许 SELECT 查询。
 */
public final class SqlTools {

    private final JdbcTemplate jdbcTemplate;

    public SqlTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(
            name = "sql_db_list_tables",
            description =
                    "Input is an empty string, output is a comma-separated list of tables in the"
                            + " database.")
    public String listTables(
            @ToolParam(name = "ignored", description = "Empty string", required = false)
                    String ignored) {
        // 查询 H2 INFORMATION_SCHEMA 获取所有基表（非系统表）
        List<String> tables =
                jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA ="
                                + " 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'",
                        String.class);
        return String.join(", ", tables);
    }

    @Tool(
            name = "sql_db_schema",
            description =
                    "Input is a comma-separated list of tables, output is the schema and sample"
                            + " rows for those tables.")
    public String getSchema(
            @ToolParam(name = "tableNames", description = "Comma-separated table names")
                    String tableNames) {
        String[] tables = tableNames.split(",");
        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            String t = table.trim();
            if (t.isEmpty()) continue;
            try {
                List<Map<String, Object>> rows =
                        jdbcTemplate.queryForList("SELECT * FROM " + t + " LIMIT 3");
                sb.append("CREATE TABLE \"").append(t).append("\" (");
                List<Map<String, Object>> columns =
                        jdbcTemplate.queryForList(
                                "SELECT COLUMN_NAME, TYPE_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                                        + " WHERE TABLE_NAME = ?",
                                t.toUpperCase());
                sb.append(
                        columns.stream()
                                .map(c -> "\"" + c.get("COLUMN_NAME") + "\" " + c.get("TYPE_NAME"))
                                .collect(Collectors.joining(", ")));
                sb.append(")\n\n");
                if (!rows.isEmpty()) {
                    sb.append("Sample rows:\n");
                    rows.forEach(r -> sb.append(r.toString()).append("\n"));
                }
                sb.append("\n");
            } catch (Exception e) {
                sb.append("Error for table ")
                        .append(t)
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    @Tool(
            name = "sql_db_query",
            description =
                    "Execute a SQL query. Input is a detailed and correct SQL query. Output is the"
                            + " result.")
    public String runQuery(
            @ToolParam(name = "query", description = "SQL query to execute") String query) {
        // 安全守卫：拒绝任何 DML/DDL 语句以防止数据损坏。
        // 此示例中仅允许 SELECT 查询。
        if (query.toUpperCase().contains("INSERT")
                || query.toUpperCase().contains("UPDATE")
                || query.toUpperCase().contains("DELETE")
                || query.toUpperCase().contains("DROP")) {
            return "Error: Only SELECT queries are allowed.";
        }
        try {
            // 执行查询并以字符串形式返回结果
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
            return rows.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
