#!/usr/bin/env python3
import sys
from pathlib import Path
try:
    import yaml
except ModuleNotFoundError:
    print('PyYAML is required for YAML validation: pip install pyyaml')
    sys.exit(1)
for arg in sys.argv[1:]:
    path = Path(arg)
    with path.open('r', encoding='utf-8') as f:
        list(yaml.safe_load_all(f))
    print(f'YAML OK: {path}')
