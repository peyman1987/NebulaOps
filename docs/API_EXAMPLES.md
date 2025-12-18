# API examples

All browser-facing API examples use the same gateway origin:

```text
http://nebulaops.localhost/api
```

## Health

```bash
curl -i http://nebulaops.localhost/actuator/health
```

## Tasks

```bash
curl -i "http://nebulaops.localhost/api/tasks?organizationId=default-org" \
  -H "Authorization: Bearer $TOKEN"
```

## Docker runtime

```bash
curl -i http://nebulaops.localhost/api/runtime/docker/containers \
  -H "Authorization: Bearer $TOKEN"
```

## Kubernetes snapshot

```bash
curl -i http://nebulaops.localhost/api/kubernetes/snapshot \
  -H "Authorization: Bearer $TOKEN"
```

## Releases

```bash
curl -i http://nebulaops.localhost/api/releases \
  -H "Authorization: Bearer $TOKEN"
```

## Policies

```bash
curl -i http://nebulaops.localhost/api/policies \
  -H "Authorization: Bearer $TOKEN"
```
