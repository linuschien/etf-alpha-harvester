---
name: oas-generator
description: A deterministic generator that transforms API metadata into OpenAPI 3.2 YAML. Enforces strict standards for return codes, payload examples, PATCH/PUT concurrency, and GraphQL redirection.
---

# Skill: Hardened OpenAPI Specification Generator

## ℹ️ Objective
Receives structured metadata to produce a standardized OpenAPI 3.2.0 contract. This generator acts as a "Strict Compiler," ensuring that functional details like status codes, PATCH operations, and payload examples are baked into every output.

## 🛠️ Generator Rules

### 1. Response & Return Code Mapping
- **Success Patterns**:
    - `POST` (Creation) -> `201 Created`.
    - `GET` (Element) -> `200 OK`.
    - `PUT` (Sync/Replace) -> `200 OK`.
    - `PATCH` (Partial Update) -> `200 OK`.
    - `DELETE` -> `204 No Content`.
    - `Custom Action` -> `200 OK` with an `OperationStatus` schema.
- **Error Patterns (Mandatory)**:
    - **400 Bad Request**: For validation failures.
    - **404 Not Found**: For resource missing.
    - **409 Conflict**: MANDATORY for both `PUT` and `PATCH` if `409_required` is flagged in metadata.

### 2. Payload & Schema Examples
- **Automatic Example Generation**: Every attribute MUST include an `example` field based on its type (e.g., `String` -> "string_val", `DateTime` -> "2026-04-07T00:00:00Z").
- **Partial Schemas for PATCH**: Ensure the `PATCH` operation uses a schema where all properties are optional (nullable or omitted) to support delta updates.

### 3. Endpoint Architecture
- **Collection GET Ban**: For any Collection `GET` path, set `deprecated: true` and inject a description directing users to a GraphQL gateway.
- **Custom Action Syntax**: Implement `POST {path}/{id}:{action}` for business behaviors.
- **Path Parameters**: Use unique, descriptive parameters in camelCase (e.g., `{inverterId}`) for nested hierarchies.
- **Default Base Path**: If no base path is specified in metadata, default to `/api/v1` in the `servers` block.

### 4. Concurrency & Sync Guardrails
- **409 Conflict Injection**: When `409_required` is true, both `PUT` and `PATCH` MUST include the `409 Conflict` response definition with the `ConflictError` schema.
- **Idempotency**: Document `PUT` as the primary synchronization point for `externalId` if sync is flagged.

### 5. Naming Conventions (camelCase)
- **Strict camelCase**: All attributes/properties, path parameters, query parameters, and `operationId`s MUST strictly use `camelCase` (e.g., `passingThreshold` instead of `passing_threshold`, and `{semesterId}` instead of `{semester_id}`).

## ⚠️ Output Requirements
- **Format**: Pure YAML (OpenAPI 3.2.0).
- **Constraints**: No conversational filler. Use `FIXME_REQUIRED` placeholders for missing mandatory metadata.
***