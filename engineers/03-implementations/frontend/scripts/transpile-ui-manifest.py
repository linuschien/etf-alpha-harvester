#!/usr/bin/env python3
import json
import os

manifest_path = '../../../docs/02-design-specs/ui-schemas/global-market-intelligence.ui-manifest.json'
output_path = 'src/schemas/global-market-intelligence.render-schema.json'

with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = json.load(f)

elements = {}

def get_options_for_selection(node_id, label):
    if 'perspective-mode-selector' in node_id:
        return ['⚡ 夏農幾何收割', '🧩 分群去冗餘族群']
    if 'asset-class-selector' in node_id:
        return ['核心大盤 (Core)', '動能衛星 (Satellite)', '防禦債券 (Defensive)']
    if 'window' in node_id or 'Window' in node_id:
        return ['1M', '3M', '6M', '1Y']
    if 'freq-filter' in node_id:
        return ['全部', '月配', '季配', '半年配', '年配']
    if 'benchmark' in node_id:
        return ['^TWII', '^GSPC', '^NDX', '^SOX', '^N225']
    return ['選項A', '選項B']

def process_node(node):
    node_id = node['id']
    abstract_type = node.get('abstract_type')
    semantic_variant = node.get('semantic_variant')
    label = node.get('label', '')
    children = [c['id'] for c in node.get('children', [])]
    
    props = {}
    component_type = 'div'
    on_handlers = None
    
    if abstract_type == 'Container':
        if semantic_variant == 'page':
            component_type = 'Container:page'
            props = {'id': node_id}
        elif semantic_variant in ['card', 'panel']:
            component_type = 'Card'
            props = {
                'title': label if semantic_variant == 'card' else None,
                'description': None,
                'maxWidth': None,
                'centered': None,
                'className': None
            }
        else:
            component_type = 'div'
            props = {'id': node_id}
            
    elif abstract_type == 'Section':
        component_type = 'div'
        props = {'id': node_id}
        
    elif abstract_type == 'Stack':
        component_type = 'Stack'
        direction = 'horizontal' if semantic_variant in ['action-bar', 'filter-bar', 'horizontal'] else 'vertical'
        props = {
            'direction': direction,
            'gap': 'md',
            'align': 'center' if direction == 'horizontal' else None,
            'justify': 'between' if semantic_variant == 'action-bar' else None,
            'className': None
        }
        
    elif abstract_type == 'Grid':
        component_type = 'Grid'
        hint = node.get('ux_hints', {}).get('group_size_hint', 4)
        props = {
            'columns': int(hint),
            'gap': 'md',
            'className': None
        }
        
    elif abstract_type == 'Heading':
        component_type = 'Heading'
        level = 'h1' if 'page-title' in node_id else ('h2' if 'heading' in node_id and ('benchmark' in node_id or 'leaderboard' in node_id or 'calendar' in node_id) else 'h3')
        props = {
            'text': label,
            'level': level
        }
        
    elif abstract_type == 'Text':
        component_type = 'Text'
        props = {
            'text': label,
            'variant': None
        }
        
    elif abstract_type == 'Metric':
        component_type = 'MetricCard'
        props = {
            'id': node_id,
            'label': label,
            'value': {'$bindState': f'/metrics/{node_id}'},
            'data_ref': node.get('data_ref')
        }
        
    elif abstract_type == 'Chart':
        component_type = 'Chart'
        props = {
            'id': node_id,
            'label': label,
            'data_ref': node.get('data_ref')
        }
        
    elif abstract_type == 'Table':
        component_type = 'DataTable'
        props = {
            'id': node_id,
            'label': label,
            'columns': node.get('columns', []),
            'data': {'$bindState': f'/data/{node.get("data_ref", node_id)}'}
        }
        
    elif abstract_type == 'Selection':
        options = get_options_for_selection(node_id, label)
        if semantic_variant == 'radio':
            component_type = 'Radio'
            props = {
                'label': label,
                'name': node_id,
                'options': options,
                'value': {'$bindState': f'/filters/{node_id}'},
                'checks': None,
                'validateOn': None
            }
        else:
            component_type = 'Select'
            props = {
                'label': label,
                'name': node_id,
                'options': options,
                'placeholder': f'請選擇 {label}',
                'value': {'$bindState': f'/filters/{node_id}'},
                'checks': None,
                'validateOn': None
            }
            
    elif abstract_type == 'Switch':
        component_type = 'Switch'
        props = {
            'label': label,
            'name': node_id,
            'checked': {'$bindState': f'/filters/{node_id}'},
            'checks': None,
            'validateOn': None
        }
        
    elif abstract_type == 'Trigger':
        component_type = 'Button'
        variant = 'secondary' if semantic_variant == 'secondary' else ('primary' if semantic_variant == 'primary' else 'secondary')
        props = {
            'label': label,
            'variant': variant,
            'disabled': None
        }
        # determine action from interactions
        interactions = node.get('interactions', [])
        if interactions:
            target = interactions[0].get('target_id')
            if 'modal' in target:
                if 'close' in node_id:
                    on_handlers = {'press': [{'action': 'closeModal', 'params': {'id': target}}]}
                else:
                    on_handlers = {'press': [{'action': 'openModal', 'params': {'id': target}}]}
            elif 'drawer' in target:
                if 'close' in node_id:
                    on_handlers = {'press': [{'action': 'closeModal', 'params': {'id': target}}]}
                else:
                    on_handlers = {'press': [{'action': 'openModal', 'params': {'id': target}}]}
            elif 'section' in target:
                on_handlers = {'press': [{'action': 'selectTab', 'params': {'tab': target}}]}
            else:
                on_handlers = {'press': [{'action': 'executeBehavior', 'params': {'ref': node_id, 'id': node_id}}]}
        else:
            on_handlers = {'press': [{'action': 'executeBehavior', 'params': {'ref': node_id, 'id': node_id}}]}
            
    elif abstract_type == 'Overlay':
        if semantic_variant == 'drawer':
            component_type = 'Drawer'
            props = {
                'title': label,
                'description': None,
                'openPath': f'/modals/{node_id}'
            }
        else:
            component_type = 'Dialog'
            props = {
                'title': label,
                'description': None,
                'openPath': f'/modals/{node_id}'
            }
    else:
        component_type = 'div'
        props = {'id': node_id}

    el_def = {
        'type': component_type,
        'props': props,
        'children': children
    }
    if on_handlers:
        el_def['on'] = on_handlers
    if abstract_type == 'Overlay':
        el_def['visible'] = {'$state': f'/modals/{node_id}'}

    elements[node_id] = el_def

    for child in node.get('children', []):
        process_node(child)

process_node(manifest['root_element'])

render_spec = {
    'root': manifest['root_element']['id'],
    'elements': elements
}

os.makedirs('src/schemas', exist_ok=True)
with open(output_path, 'w', encoding='utf-8') as out:
    json.dump(render_spec, out, indent=2, ensure_ascii=False)

print(f'Successfully transpiled {len(elements)} elements to {output_path}')
