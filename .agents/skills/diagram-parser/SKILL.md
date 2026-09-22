---
name: diagram-parser
description: High-precision transpiler for PlantUML content. Extracts API metadata by identifying <<Entity>> resources, locking verbs via <<Repository>> interfaces, and mapping relationships to URI hierarchies.
---

# Skill: Entity-Repository Context & Recursive Hierarchy Parser

## ℹ️ Objective
A stateless utility to transform PlantUML text into structured API metadata. This skill focuses strictly on the logic of domain-to-API mapping, relying on the calling Agent to provide the relevant file context.

## 🛠️ Extraction & Hardening Logic

### 1. URI Hierarchy & Relationship Mapping
- **Composition (*--)**: 
    - Maps to a **Sub-resource Path**. 
    - Syntax: `/parents/{id}/children`. 
    - **Recursive Support**: Faithfully reflects the UML ownership chain regardless of depth. 
- **Aggregation (o--) & Association (--)**: 
    - Maps to a **Top-level Resource Path** (e.g., `/independent-resources`).
- **Pluralization**: Resource segments in the URI MUST be pluralized (e.g., `Battery` -> `/batteries`).

### 2. Capability Mapping (Repository-Driven)
- **Rule**: For every `<<Entity>>`, the Parser requires the definition of a corresponding `<<Repository>>` interface to determine allowed HTTP verbs.
- **Verb Assignment**:
    - `Create()` -> Enables **POST** (Collection).
    - `GetById()` -> Enables **GET** (Element).
    - `Update()` -> Enables **PUT** (Element).
    - `Delete()` -> Enables **DELETE** (Element).
- **Collection GET Policy**: Explicitly flag all Collection `GET` operations as `DEPRECATED_TO_GRAPHQL`.

### 3. Custom Action Mapping
- **Rule**: Map public `+` methods in an `<<Entity>>` (excluding standard CRUD) to Custom Methods: `POST /.../{id}:{action}`.

### 4. Hardening Flags
- **Optimistic Locking (409)**: Set if `version` or `last_updated` attribute is present.
- **Data Sync (Idempotent PUT)**: Set if `external_id` attribute is present.

## ⚙️ Output Metadata Schema
Produce structured JSON:
- `entity_name`: Singular/Plural strings.
- `uri_structure`: `{ "path": string, "is_sub_resource": bool }`.
- `allowed_verbs`: Derived from Repository context.
- `custom_actions`: List of public Entity methods.
- `hardening_flags`: `{ "409_required": bool, "put_sync": bool }`.

## ⚠️ Hard Constraints
- **Scope**: Process ONLY classes marked `<<Entity>>`. Ignore all other classes unless they serve as Value Objects for Schema definition.
- **No Hallucination**: If an attribute type is missing, mark as `UNKNOWN`.
- **Stateless Execution**: Do not assume the existence of any specific directory structure.
***