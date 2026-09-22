---
name: ui-manifest-validator
description: Validates UI Manifest JSON files against the Universal SDD UI PIM Meta-Schema.
---

# UI Manifest Validator

This skill provides a script to validate UI Manifest JSON files (`*.ui-manifest.json`) against their strict schema. 

## Validation Schema

The JSON schema governing the output is located at:
`.agents/skills/ui-manifest-validator/resources/ui-manifest-schema.json`

## Validation Script

A standalone Python script is provided to automatically check all manifest files in the `docs/02-design-specs/ui-schemas` directory.

To run the validation, execute the following command from the workspace root:
```bash
python3 .agents/skills/ui-manifest-validator/scripts/validate_ui_manifests.py
```
