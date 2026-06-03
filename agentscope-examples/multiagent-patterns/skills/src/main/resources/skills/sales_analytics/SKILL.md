---
name: sales_analytics
description: Database schema and business logic for sales data analysis including customers, orders, and revenue.
---

# Sales Analytics Schema
# 销售分析 —— 数据库 Schema 与业务逻辑

本技能提供销售分析领域的数据库表结构、业务规则和示例查询。
当用户询问与客户、订单、营收相关的问题时，Agent 应加载此技能。

## Tables
## 数据库表结构

### customers
### 客户表
- customer_id (PRIMARY KEY) — 客户唯一标识
- name — 客户姓名
- email — 客户邮箱
- signup_date — 注册日期
- status (active/inactive) — 客户状态（活跃/非活跃）
- customer_tier (bronze/silver/gold/platinum) — 客户等级（青铜/白银/黄金/铂金）

### orders
### 订单表
- order_id (PRIMARY KEY) — 订单唯一标识
- customer_id (FOREIGN KEY -> customers) — 客户 ID，关联客户表
- order_date — 订单日期
- status (pending/completed/cancelled/refunded) — 订单状态（待处理/已完成/已取消/已退款）
- total_amount — 订单总金额
- sales_region (north/south/east/west) — 销售区域（北/南/东/西）

### order_items
### 订单明细表
- item_id (PRIMARY KEY) — 明细项唯一标识
- order_id (FOREIGN KEY -> orders) — 订单 ID，关联订单表
- product_id — 产品 ID
- quantity — 数量
- unit_price — 单价
- discount_percent — 折扣百分比

## Business Logic
## 业务规则

**Active customers / 活跃客户定义**:
status = 'active' AND signup_date <= CURRENT_DATE - INTERVAL '90 days'

**Revenue calculation / 营收计算规则**:
Only count orders with status = 'completed'. Use total_amount from orders table.
仅统计状态为「已完成」的订单，使用 orders 表中的 total_amount 字段。

**High-value orders / 高价值订单定义**:
Orders with total_amount > 1000
订单总金额超过 1000 美元视为高价值订单。

## Example Query
## 示例查询

```sql
-- Get top 10 customers by revenue in the last quarter
-- 查询：过去一个季度营收排名前 10 的客户
SELECT c.customer_id, c.name, c.customer_tier, SUM(o.total_amount) as total_revenue
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
WHERE o.status = 'completed' AND o.order_date >= CURRENT_DATE - INTERVAL '3 months'
GROUP BY c.customer_id, c.name, c.customer_tier
ORDER BY total_revenue DESC LIMIT 10;
```
