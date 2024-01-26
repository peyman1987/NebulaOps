# API Examples

## Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "organizationName":"Acme Cloud",
    "name":"Peyman",
    "email":"peyman@example.com",
    "password":"Password123!"
  }'
```

## Login

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"peyman@example.com","password":"Password123!"}' | jq -r .accessToken)
```

## Create project

```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Cloud Migration","description":"Move legacy workload to cloud-native infrastructure"}'
```

## List projects

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/projects
```

## Create task

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId":1,
    "title":"Create Terraform module",
    "description":"Provision VPC, EKS, MongoDB-compatible data layer and object storage",
    "priority":"HIGH",
    "dueDate":"2026-06-30"
  }'
```

## Change task status

```bash
curl -X PATCH http://localhost:8080/api/tasks/1/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}'
```

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
