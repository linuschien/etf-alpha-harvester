---
name: dbml-generator
description: A deterministic generator that transforms API/Entity metadata into DBML. Enforces UUID primary keys, mandatory cascade deletes for join tables, and automated infrastructure field injection.
---

# Skill: Universal DBML Schema Generator (Hardened Edition)

## ℹ️ Objective
To transform logical entity metadata into a physical database schema using DBML. This skill ensures that the database structure strictly follows architectural integrity rules, specifically regarding UUID usage and referential cleanup.

## 🛠️ Transformation Logic

### 1. Table & Column Mapping
- **Tables**: Every `<<Entity>>` maps to a `Table`.
- **Primary Key**: Every table MUST have a Primary Key named `id` with type `uuid`.
- **Value Object Flattening**: Flatten `<<ValueObject>>` attributes into the parent Entity table.
- **Naming**: Convert all names to **snake_case**.

### 2. Standard Relationships (One-to-Many)
- **Composition (*--)**: `Ref: parent.id < child.parent_id [delete: cascade]`
- **Aggregation (o--)**: `Ref: parent.id < child.parent_id [delete: set null]`

### 3. Many-to-Many (M:M) Resolution
- **Detection**: Triggered by `*--*` relationships in metadata.
- **Table Generation**: Automatically generate an alphabetical Join Table (e.g., `role_user`).
- **Column Mapping**:
    - `{table_a}_id uuid`
    - `{table_b}_id uuid`
    - **Primary Key**: `pk: ({table_a}_id, {table_b}_id)`
- **Mandatory Referential Integrity**:
    - Both foreign keys MUST implement cascade delete:
        - `Ref: table_a.id < join_table.table_a_id [delete: cascade]`
        - `Ref: table_b.id < join_table.table_b_id [delete: cascade]`
- **Audit**: Inject `created_at timestamp`.

### 4. Automated Infrastructure Injection
For every table, inject:
- **ID**: `id uuid [pk]`
- **Versioning**: If `409_required`, add `version integer [default: 1]`.
- **Audit**: `created_at timestamp`, `updated_at timestamp`, `deleted_at timestamp`.
- **Sync**: If `put_sync`, add `external_id varchar [unique]`.

## ⚠️ Output Requirements
- **Format**: Pure DBML code.
- **Constraints**: Use `FIXME_TYPE` for unmapped types. Group Enums at the top.
***