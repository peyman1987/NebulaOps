# Start here — NebulaOps v14

1. Install Docker Desktop and enable WSL integration if you use Windows.
2. Copy the project into the WSL filesystem for better performance.
3. Start the stack with `./scripts/local-up.sh`.
4. Open `http://localhost:4200`.
5. Use the tabs in this order: Overview, Tasks, Kubernetes, Observability, CI/CD, Security, Infra.

```bash
cd nebulaops-v14
./scripts/wsl/check-wsl.sh
./scripts/local-up.sh
./scripts/smoke-test.sh
```

The project is intentionally runnable on a personal machine. Kubernetes, Helm, GitLab and Argo CD artifacts are included
as portfolio-grade examples and can be applied locally with kind/minikube when needed.
