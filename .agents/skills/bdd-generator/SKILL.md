---
name: bdd-generator
description: Logic engine for translating requirements into Gherkin features with EDA awareness.
---

# Skill: Hardened BDD Generator

## ℹ️ Objective
To produce pure Gherkin (.feature) files that strictly adhere to the technical naming conventions and architectural side-effects defined in the design specs.

## 🧪 EDA Behavior Logic
When generating Gherkin, follow these semantic rules:

### 1. For User Behavior (Primary Port)
- **Given**: The initial system state (derived from UML/DBML).
- **When**: An action is received via a Primary Port (e.g., "An 'OrderCreated' event is received" OR "A GET request is made").
- **Then**: The external promise is fulfilled (e.g., "The response code is 200" OR "The service state changes").

### 2. For System Behavior (Secondary Port & Side-Effects)
- **Given**: A Domain logic condition is met.
- **When**: A process is internally triggered.
- **Then**: Verify the side-effect on the specific Secondary Port defined in the Manifest.
  - *Example*: "Then the 'PostgreSQLAdapter' should persist the record with UUID format."
  - *Example*: "Then the 'EventPublisher' should emit a 'BatteryWarning' event."

### 3. Example Mappings
- **Scenario Outlines**: When producing Gherkins, utilize the `Examples` keyword within `Scenario Outline` blocks to explicitly describe example mappings.
- Clearly map distinct input permutations, state conditions, and expected outcomes/side-effects using structured markdown data tables under `Examples:`.

## ⚠️ Hard Constraints
- **Naming**: NEVER invent names. Use `glossary.md` for terms and `openapi.yaml` for methods.
- **Language**: English only for Gherkin keywords; use Traditional Chinese for descriptions if requested, but maintain Technical English for domain nouns.
- **No Vibe**: If a mapping is missing in the Manifest, stop and ask the user to update `external-integrations/`.
***