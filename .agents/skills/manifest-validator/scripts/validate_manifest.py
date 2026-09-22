import yaml
import sys
import re
import os

def validate():
    current_dir = os.path.dirname(os.path.abspath(__file__))
    schema_path = os.path.abspath(os.path.join(current_dir, '..', 'resources', 'hexagonal-service-manifest-schema.yaml'))
    
    if len(sys.argv) < 2:
        print("Usage: python3 validate_manifest.py <path-to-manifest.yaml>")
        sys.exit(1)
        
    manifest_path = sys.argv[1]
    
    if not os.path.exists(manifest_path):
        print(f"Error: Manifest file not found at '{manifest_path}'")
        sys.exit(1)
    if not os.path.exists(schema_path):
        print(f"Error: Schema file not found at '{schema_path}'")
        sys.exit(1)
        
    try:
        with open(manifest_path, 'r', encoding='utf-8') as f:
            manifest = yaml.safe_load(f)
    except Exception as e:
        print(f"Error loading manifest YAML: {e}")
        sys.exit(1)
        
    try:
        with open(schema_path, 'r', encoding='utf-8') as f:
            schema = yaml.safe_load(f)
    except Exception as e:
        print(f"Error loading schema YAML: {e}")
        sys.exit(1)

    errors = []
    
    # 1. Root fields
    allowed_root_fields = {'service_name', 'architecture', 'adapters'}
    actual_root_fields = set(manifest.keys())
    extra_root = actual_root_fields - allowed_root_fields
    if extra_root:
        errors.append(f"Root level contains forbidden additional properties: {extra_root}")
        
    for req in ['service_name', 'architecture', 'adapters']:
        if req not in manifest:
            errors.append(f"Root field '{req}' is required but missing.")
            
    if 'service_name' in manifest:
        if not isinstance(manifest['service_name'], str) or not manifest['service_name'].strip():
            errors.append("service_name must be a non-empty string.")
            
    if 'architecture' in manifest:
        if manifest['architecture'] != 'Hexagonal':
            errors.append(f"architecture must be 'Hexagonal', got '{manifest['architecture']}'.")
            
    if 'adapters' in manifest:
        adapters = manifest['adapters']
        if not isinstance(adapters, list):
            errors.append("adapters must be a list.")
        elif len(adapters) == 0:
            errors.append("adapters must contain at least one item.")
        else:
            allowed_adapter_fields = {'name', 'type', 'tech', 'stereotype', 'port'}
            for idx, adapter in enumerate(adapters):
                prefix = f"adapters[{idx}]"
                if not isinstance(adapter, dict):
                    errors.append(f"{prefix} must be an object.")
                    continue
                
                # Check for forbidden fields
                actual_adapter_fields = set(adapter.keys())
                extra_fields = actual_adapter_fields - allowed_adapter_fields
                if extra_fields:
                    errors.append(f"{prefix} contains forbidden additional properties: {extra_fields}")
                    
                # Required fields
                for req in ['name', 'type', 'tech', 'port']:
                    if req not in adapter:
                        errors.append(f"{prefix} is missing required field '{req}'.")
                
                if 'name' in adapter:
                    name = adapter['name']
                    if not isinstance(name, str) or not re.match(r'^.*Adapter$', name):
                        errors.append(f"{prefix}.name '{name}' must end with 'Adapter'.")
                        
                if 'type' in adapter:
                    atype = adapter['type']
                    if atype not in ['Primary', 'Secondary']:
                        errors.append(f"{prefix}.type '{atype}' must be 'Primary' or 'Secondary'.")
                        
                if 'tech' in adapter:
                    tech = adapter['tech']
                    if not isinstance(tech, str) or not tech.strip():
                        errors.append(f"{prefix}.tech must be a non-empty string.")
                    elif tech == 'FIXME_TECH':
                        errors.append(f"{prefix}.tech contains FIXME_TECH.")
                        
                if 'port' in adapter:
                    port = adapter['port']
                    if not isinstance(port, dict):
                        errors.append(f"{prefix}.port must be an object.")
                    else:
                        allowed_port_fields = {'interface', 'layer'}
                        actual_port_fields = set(port.keys())
                        extra_port_fields = actual_port_fields - allowed_port_fields
                        if extra_port_fields:
                            errors.append(f"{prefix}.port contains forbidden additional properties: {extra_port_fields}")
                            
                        for req in ['interface', 'layer']:
                            if req not in port:
                                errors.append(f"{prefix}.port is missing required field '{req}'.")
                                
                        if 'interface' in port:
                            if not isinstance(port['interface'], str) or not port['interface'].strip():
                                errors.append(f"{prefix}.port.interface must be a non-empty string.")
                                
                        if 'layer' in port:
                            layer = port['layer']
                            if layer not in ['domain.ports.input', 'domain.ports.output']:
                                errors.append(f"{prefix}.port.layer '{layer}' must be 'domain.ports.input' or 'domain.ports.output'.")

    if errors:
        print("Schema Validation FAILED:")
        for err in errors:
            print(f"- {err}")
        sys.exit(1)
    else:
        print("Schema Validation SUCCESS!")

if __name__ == '__main__':
    validate()
