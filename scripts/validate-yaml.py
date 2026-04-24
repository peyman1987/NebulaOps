#!/usr/bin/env python3
import sys
from pathlib import Path

try:
    import yaml
except ModuleNotFoundError:
    print('PyYAML is required for YAML validation: pip install pyyaml')
    sys.exit(1)


def depends_on_names(depends_on):
    if depends_on is None:
        return []
    if isinstance(depends_on, list):
        return [str(item) for item in depends_on]
    if isinstance(depends_on, dict):
        return [str(item) for item in depends_on.keys()]
    return []


def validate_compose_dependencies(path: Path, document):
    if not isinstance(document, dict) or not isinstance(document.get('services'), dict):
        return
    services = document['services']
    service_names = set(services.keys())
    missing = []
    graph = {}
    for service_name, service in services.items():
        if not isinstance(service, dict):
            graph[service_name] = []
            continue
        dependencies = depends_on_names(service.get('depends_on'))
        graph[service_name] = [dependency for dependency in dependencies if dependency in service_names]
        for dependency in dependencies:
            if dependency not in service_names:
                missing.append((service_name, dependency))
    if missing:
        print(f'Compose dependency validation FAILED: {path}')
        for service_name, dependency in missing:
            print(f' - service "{service_name}" depends on undefined service "{dependency}"')
        raise SystemExit(1)

    visiting = set()
    visited = set()
    stack = []

    def dfs(service_name):
        if service_name in visiting:
            cycle_start = stack.index(service_name) if service_name in stack else 0
            return stack[cycle_start:] + [service_name]
        if service_name in visited:
            return None
        visiting.add(service_name)
        stack.append(service_name)
        for dependency in graph.get(service_name, []):
            cycle = dfs(dependency)
            if cycle:
                return cycle
        stack.pop()
        visiting.remove(service_name)
        visited.add(service_name)
        return None

    for service_name in sorted(service_names):
        cycle = dfs(service_name)
        if cycle:
            print(f'Compose dependency validation FAILED: {path}')
            print(' - dependency cycle detected: ' + ' -> '.join(cycle))
            raise SystemExit(1)


def validate_compose_image_tags(path: Path, document):
    if not isinstance(document, dict) or not isinstance(document.get('services'), dict):
        return
    bad_images = []
    for service_name, service in document['services'].items():
        if not isinstance(service, dict):
            continue
        image = str(service.get('image', '')).strip()
        if image.startswith('openpolicyagent/opa:') and image.endswith('-rootless'):
            bad_images.append((service_name, image))
    if bad_images:
        print(f'Compose image validation FAILED: {path}')
        for service_name, image in bad_images:
            print(f' - service "{service_name}" uses unavailable OPA rootless image tag "{image}"')
            print('   Use the standard OPA image tag instead, for example openpolicyagent/opa:0.68.0.')
        raise SystemExit(1)


def validate_grafana_host_port(path: Path, document):
    if not isinstance(document, dict) or not isinstance(document.get('services'), dict):
        return
    grafana = document['services'].get('grafana')
    if not isinstance(grafana, dict):
        return
    bad_ports = []
    for port in grafana.get('ports') or []:
        value = str(port).strip().strip('"\'')
        if value == '3000:3000' or value.startswith('0.0.0.0:3000:'):
            bad_ports.append(value)
    if bad_ports:
        print(f'Compose Grafana port validation FAILED: {path}')
        for value in bad_ports:
            print(f' - grafana uses hardcoded host port "{value}"')
        print('   Use ${GRAFANA_HOST_PORT:-3300}:3000 so local port 3000 conflicts do not break startup.')
        raise SystemExit(1)


def validate_keycloak_admin_password(path: Path, document):
    if not isinstance(document, dict) or not isinstance(document.get('services'), dict):
        return
    keycloak = document['services'].get('keycloak')
    if not isinstance(keycloak, dict):
        return
    env = keycloak.get('environment') or {}
    if isinstance(env, list):
        env_map = {}
        for item in env:
            text = str(item)
            if '=' in text:
                key, value = text.split('=', 1)
                env_map[key] = value
        env = env_map
    if not isinstance(env, dict) or not env.get('KEYCLOAK_ADMIN_PASSWORD'):
        print(f'Compose Keycloak validation FAILED: {path}')
        print(' - keycloak service is missing KEYCLOAK_ADMIN_PASSWORD, so admin token bootstrap can fail')
        raise SystemExit(1)


def validate_keycloak_healthcheck(path: Path, document):
    if not isinstance(document, dict) or not isinstance(document.get('services'), dict):
        return
    keycloak = document['services'].get('keycloak')
    if not isinstance(keycloak, dict):
        return
    healthcheck = keycloak.get('healthcheck') or {}
    test = healthcheck.get('test') or []
    text = ' '.join(str(part) for part in test) if isinstance(test, list) else str(test)
    if 'bash -ec' not in text or '/health/ready' not in text:
        print(f'Compose Keycloak healthcheck validation FAILED: {path}')
        print(' - keycloak healthcheck must use explicit bash and /health/ready; sh-only /dev/tcp checks are brittle')
        raise SystemExit(1)
    if '\"status\":\"UP\"' in text and '[[:space:]]' not in text:
        print(f'Compose Keycloak healthcheck validation FAILED: {path}')
        print(' - keycloak healthcheck checks only compact JSON; it must tolerate whitespace in health response')
        raise SystemExit(1)
    services = document['services']
    for service_name in ('auth-service', 'gateway-service'):
        service = services.get(service_name)
        if not isinstance(service, dict):
            continue
        depends_on = service.get('depends_on')
        if isinstance(depends_on, dict) and isinstance(depends_on.get('keycloak'), dict):
            condition = depends_on['keycloak'].get('condition')
            if condition == 'service_healthy':
                print(f'Compose Keycloak dependency validation FAILED: {path}')
                print(f' - {service_name} must not hard-block on keycloak service_healthy; use service_started and script-level OIDC validation')
                raise SystemExit(1)



def validate_sso_proxy_ports(path: Path, document):
    if not isinstance(document, dict) or not isinstance(document.get('services'), dict):
        return
    services = document['services']
    checks = {
        'rabbitmq-management-sso': ('15672', 'RABBITMQ_SSO_HOST_PORT', '15673'),
        'mongo-express-sso': ('8088', 'MONGO_EXPRESS_SSO_HOST_PORT', '18088'),
        'redis-commander-sso': ('8089', 'REDIS_COMMANDER_SSO_HOST_PORT', '18089'),
    }
    failures = []
    for service_name, (native_port, env_name, default_port) in checks.items():
        service = services.get(service_name)
        if not isinstance(service, dict):
            continue
        ports = [str(item).strip().strip('"\'') for item in (service.get('ports') or [])]
        joined_ports = ' '.join(ports)
        if f'{native_port}:4180' in joined_ports or joined_ports.startswith(f'0.0.0.0:{native_port}:'):
            failures.append(f'{service_name} binds SSO proxy to native host port {native_port}')
        expected = f'${{{env_name}:-{default_port}}}:4180'
        if expected not in ports:
            failures.append(f'{service_name} must expose {expected} to avoid colliding with the native tool UI')
        env = service.get('environment') or {}
        if isinstance(env, list):
            env_map = {}
            for item in env:
                value = str(item)
                if '=' in value:
                    key, val = value.split('=', 1)
                    env_map[key] = val
            env = env_map
        redirect = env.get('OAUTH2_PROXY_REDIRECT_URL') if isinstance(env, dict) else ''
        if env_name not in str(redirect):
            failures.append(f'{service_name} redirect URL must use {env_name} so OAuth callback matches the configured SSO port')
    if failures:
        print(f'Compose SSO proxy port validation FAILED: {path}')
        for failure in failures:
            print(f' - {failure}')
        raise SystemExit(1)

def validate_gateway_runtime_wiring(path: Path, document):
    if not isinstance(document, dict) or not isinstance(document.get('services'), dict):
        return
    services = document['services']
    gateway = services.get('gateway-service')
    if not isinstance(gateway, dict):
        return
    root = path.parent
    if path.name != 'docker-compose.yml':
        root = path.parent.parent if (path.parent.name == 'infrastructure') else path.parent
    app_yml = root / 'backend' / 'gateway-service' / 'src' / 'main' / 'resources' / 'application.yml'
    proxy_controller = root / 'backend' / 'gateway-service' / 'src' / 'main' / 'java' / 'dev' / 'nebulaops' / 'gateway' / 'api' / 'ProxyController.java'
    if not app_yml.exists() or not proxy_controller.exists():
        return
    import re
    with app_yml.open('r', encoding='utf-8') as f:
        app = yaml.safe_load(f) or {}
    proxy = app.get('proxy') or {}
    required = sorted(set(re.findall(r'@Value\("\$\{proxy\.([^}:]+)', proxy_controller.read_text(encoding='utf-8'))))
    missing = [key for key in required if key not in proxy]
    if missing:
        print(f'Gateway proxy validation FAILED: {app_yml}')
        for key in missing:
            print(f' - ProxyController requires proxy.{key}, but application.yml does not define it')
        raise SystemExit(1)


for arg in sys.argv[1:]:
    path = Path(arg)
    with path.open('r', encoding='utf-8') as f:
        documents = list(yaml.safe_load_all(f))
    for document in documents:
        validate_compose_dependencies(path, document)
        validate_compose_image_tags(path, document)
        validate_grafana_host_port(path, document)
        validate_keycloak_admin_password(path, document)
        validate_keycloak_healthcheck(path, document)
        validate_sso_proxy_ports(path, document)
        validate_gateway_runtime_wiring(path, document)
    print(f'YAML OK: {path}')
