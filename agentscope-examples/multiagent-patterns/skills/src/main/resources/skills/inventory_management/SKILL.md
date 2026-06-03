---
name: inventory_management
description: Database schema and business logic for inventory tracking including products, warehouses, and stock levels.
---

# Inventory Management Schema
# 库存管理 —— 数据库 Schema 与业务逻辑

本技能提供库存管理领域的数据库表结构、业务规则和示例查询。
当用户询问与产品、仓库、库存水平、补货相关的问题时，Agent 应加载此技能。

## Tables
## 数据库表结构

### products
### 产品表
- product_id (PRIMARY KEY) — 产品唯一标识
- product_name — 产品名称
- sku — 库存单位编码
- category — 产品分类
- unit_cost — 单位成本
- reorder_point (minimum stock level before reordering) — 再订购点（触发补货的最低库存水平）
- discontinued (boolean) — 是否已停产

### warehouses
### 仓库表
- warehouse_id (PRIMARY KEY) — 仓库唯一标识
- warehouse_name — 仓库名称
- location — 仓库位置
- capacity — 仓库容量

### inventory
### 库存表
- inventory_id (PRIMARY KEY) — 库存记录唯一标识
- product_id (FOREIGN KEY -> products) — 产品 ID，关联产品表
- warehouse_id (FOREIGN KEY -> warehouses) — 仓库 ID，关联仓库表
- quantity_on_hand — 现有库存数量
- last_updated — 最后更新时间

### stock_movements
### 库存变动记录表
- movement_id (PRIMARY KEY) — 变动记录唯一标识
- product_id (FOREIGN KEY -> products) — 产品 ID，关联产品表
- warehouse_id (FOREIGN KEY -> warehouses) — 仓库 ID，关联仓库表
- movement_type (inbound/outbound/transfer/adjustment) — 变动类型（入库/出库/调拨/调整）
- quantity (positive for inbound, negative for outbound) — 数量（入库为正数，出库为负数）
- movement_date — 变动日期
- reference_number — 参考单号

## Business Logic
## 业务规则

**Available stock / 可用库存定义**:
quantity_on_hand from inventory where quantity_on_hand > 0
从库存表中取现有库存数量大于 0 的记录。

**Products needing reorder / 需要补货的产品**:
total quantity_on_hand across warehouses <= product's reorder_point
所有仓库的库存总量小于或等于该产品的再订购点。

**Active products only / 仅查询在售产品**:
Exclude discontinued = true unless analyzing discontinued items
默认排除已停产产品（discontinued = true），除非用户明确要求分析停产产品。

## Example Query
## 示例查询

```sql
-- Find products below reorder point
-- 查询：查找库存低于再订购点、需要补货的产品
SELECT p.product_id, p.product_name, p.reorder_point, SUM(i.quantity_on_hand) as total_stock
FROM products p
JOIN inventory i ON p.product_id = i.product_id
WHERE p.discontinued = false
GROUP BY p.product_id, p.product_name, p.reorder_point
HAVING SUM(i.quantity_on_hand) <= p.reorder_point;
```
