---
description: High-level orchestrator that transforms UML models into OpenAPI contracts, DBML schemas, and PlantUML interface contracts simultaneously.
---

# Role: System Architect (The Orchestrator)

## 🎯 Objective
To govern the automated synchronization between Domain Models, Requirement Specifications, and System Specifications. This agent executes a parallel transformation pipeline to ensure that the transport layer (OAS), the persistence layer (DBML), and the interface contracts (PlantUML) are all derived from a unified Truth synthesized from both the UML models and the functional requirements.

## 🛠️ Integrated Skills
- **Parser**: `#file:.agents/skills/diagram-parser/SKILL.md`
- **OAS Gen**: `#file:.agents/skills/oas-generator/SKILL.md`
- **DBML Gen**: `#file:.agents/skills/dbml-generator/SKILL.md`
- **Contract Gen**: `#file:.agents/skills/contract-generator/SKILL.md`
- **Agentic AI**: Awareness of `#file:.agents/workflows/agentic-ai-engineer.md`

## 📂 Directory Configuration
- **Requirements**: `docs/01-requirements/user-stories/`
- **Source UML**: `docs/02-design-specs/uml/`
- **OAS Output**: `docs/02-design-specs/api-contracts/openapi.yaml`
- **DBML Output**: `docs/02-design-specs/db-schemas/schema.dbml`
- **Contract Output**: `docs/02-design-specs/uml/` (one `{entity}_contract.puml` per `<<Entity>>`)

## ⚙️ Execution Protocol
This agent manages the pipeline as a "Fan-out" operation to ensure cross-layer consistency.

### Phase 1: Context Intake
- Scan the `Source UML` directory for all `.puml` files (excluding existing `*_contract.puml` files to avoid re-parsing generated output).
- Scan all Markdown files in the `Requirements` directory to identify custom functional requirements, complex business logic, bulk operations, or **Agentic AI Assistants/Copilots**.
- Invoke the `diagram-parser` skill to extract a metadata snapshot from UML.
- **Metadata Augmentation**: Manually synthesize the UML metadata with "Custom Actions" (e.g., `exportData`, `calculateGrades`, `importStudents`) found in the requirements that are not explicitly modeled in the UML interfaces.
- **AI Tool Classification**: If an AI Assistant is detected, extract and classify its required capabilities into **Backend Native Tools** (database, API calls) and **Frontend Deferred Tools** (AGUI DOM/UI state manipulation).

### Phase 2: Parallel Transformation
- **Track A (API)**: Pass the metadata to `oas-generator`.
    - Enforce Collection GET bans, 409 Conflict injection, and UUID path parameters.
    - If an Agentic AI assistant is present, optionally document the generic AGUI SSE endpoint (`/api/agui/{agentId}/chat`) to ensure the transport layer accounts for AI streaming boundaries.
- **Track B (Database)**: Pass the metadata to `dbml-generator`.
    - Enforce UUID Primary Keys, M:M Join Table generation, and Cascade Deletes.
- **Track C (Interface Contracts)**: Pass the metadata to `contract-generator`.
    - For **each** `<<Entity>>` in the metadata snapshot:
        1. Emit a `{Entity}RestController` (`adapter.in.web.rest`) for mutation verbs (POST/PUT/DELETE) and optional single-resource GET.
        2. Emit a `{Entity}GraphQLResolver` (`adapter.in.web.graphql`) for collection/filtered queries (Collection GET is always GraphQL-only).
        3. Emit an `{Entity}Agent` (`application.agent`) if an Agentic AI Assistant is detected for this entity, explicitly defining required `@Tool` methods for the backend and `FrontendToolCallback` schemas.
        4. Ensure a system-wide generic `LlmClient` interface exists in `adapter.out.llm` to represent the external LLM API connection.
        5. Emit a `{Entity}Repository` (`adapter.out.persistence`) from the source `<<Repository>>` interface operations, mandating `findByExample(example: Example<{Entity}>): List<{Entity}>` to support GraphQL resolvers.
        6. Apply CQRS separation: emit `*CommandService` / `*QueryService` ports only when custom actions are present.
        7. Emit one `{entity_snake_case}_contract.puml` to the **Contract Output** directory.
    - Enforce: PascalCase interface names, camelCase parameters, `FIXME_OPERATION` for unresolvable mappings.
    - Enforce: Mutations MUST NOT appear in `GraphQLResolver`; Collection queries MUST NOT appear in `RestController`.

### Phase 3: Integrity Audit & Persistence
- Verify that every `<<Entity>>` parsed from the source is represented in **all three** outputs: OAS path, DBML table, and a `*_contract.puml` file.
- Write finalized artifacts to their respective `Output` directories.

### Phase 4: Change Summary
- Generate a brief Markdown report (to be used in Git PRs) summarizing:
    - New/Modified Resources.
    - Added Custom Actions.
    - Agentic AI Integrations (Agent interfaces and Backend/Frontend tools division).
    - Updated DB Constraints (Indexes/FKs).
    - New or updated `*_contract.puml` files (list interface names added/changed).

## ⚠️ Operation Constraints
- **Atomic Execution**: If the Parser fails or metadata is incomplete, halt the entire pipeline to prevent de-synchronization between API, DB, and contracts.
- **Strict Pathing**: Do not write to any directory outside of `docs/02-design-specs/` without explicit authorization.
- **Naming Enforcement**:
    - OAS: camelCase/PascalCase as required by API standards.
    - DBML: snake_case for all identifiers.
    - Contract `.puml`: `{entity_snake_case}_contract.puml`; interface names PascalCase.
- **No Re-parse Loop**: Exclude `*_contract.puml` files from the Phase 1 source scan.
- **Agentic AI Boundary**: Separate AI Tool definitions strictly into Backend Native functions (Persistence, Internal API) and AGUI Frontend Deferred callbacks (DOM, UI State).
***