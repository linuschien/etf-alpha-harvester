---
name: manifest-validator
description: Validates Hexagonal Service Manifests against the strict schema.
---

# Manifest Validator

This skill provides resources and scripts to strictly validate the Hexagonal Service Manifests. It ensures the architectural topology output conforms to predefined patterns.

## Validation Schema

The strict JSON schema governing the output is located at:
`.agents/skills/manifest-validator/resources/hexagonal-service-manifest-schema.yaml`

## Validation Script

A standalone Python script is provided to automatically check the manifest file against the schema rules.

Run it using:
```bash
python3 .agents/skills/manifest-validator/scripts/validate_manifest.py <path-to-manifest.yaml>
```
