# Start Here - NebulaOps v11

1. Read `README.md`.
2. Review diagrams in `docs/diagrams/`.
3. On WSL, run:

```bash
chmod +x scripts/*.sh scripts/wsl/*.sh
./scripts/wsl/check-wsl.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

4. For validation only:

```bash
python3 scripts/validate-package.py
python3 scripts/validate-yaml.py
find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}
```

5. For GitOps story, read `docs/GITLAB_ARGOCD.md`.

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
