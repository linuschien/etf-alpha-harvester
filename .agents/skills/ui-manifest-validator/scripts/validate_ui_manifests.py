#!/usr/bin/env python3
import os
import sys
import json
import jsonschema

def main():
    # Paths are relative to the workspace root
    schema_path = ".agents/skills/ui-manifest-validator/resources/ui-manifest-schema.json"
    target_dir = "docs/02-design-specs/ui-schemas"
    
    if not os.path.exists(schema_path):
        print(f"Error: Schema not found at {schema_path}")
        sys.exit(1)
        
    try:
        with open(schema_path, "r", encoding="utf-8") as f:
            schema = json.load(f)
    except Exception as e:
        print(f"Failed to load schema from {schema_path}: {e}")
        sys.exit(1)

    try:
        validator = jsonschema.Draft202012Validator(schema)
    except Exception as e:
        print(f"Failed to initialize jsonschema validator: {e}")
        sys.exit(1)
    
    if not os.path.exists(target_dir):
        print(f"Error: Target directory not found at {target_dir}")
        sys.exit(1)

    files = sorted([f for f in os.listdir(target_dir) if f.endswith(".ui-manifest.json")])
    has_error = False

    for file in files:
        file_path = os.path.join(target_dir, file)
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            print(f"Failed to load JSON from {file_path}: {e}")
            has_error = True
            continue

        errors = sorted(validator.iter_errors(data), key=lambda e: e.path)
        if errors:
            print(f"❌ Validation failed for {file}:")
            for error in errors:
                path = ".".join(str(p) for p in error.path)
                if not path:
                    path = "root"
                print(f"  - [{path}] {error.message}")
            has_error = True
        else:
            print(f"✅ {file} is valid.")

    if has_error:
        print("\n❌ UI Manifest Validation Failed.")
        sys.exit(1)
    else:
        print("\n🎉 All UI manifests are valid!")

if __name__ == "__main__":
    main()
