# NebulaOps Shared Kernel

Shared Java library for micro backend services.

It centralizes stable cross-service contracts:

- API response envelope
- error envelope
- security constants
- service identity records
- REST path helper

New controllers and services should depend on this module instead of redefining the same DTOs or constants inside each micro backend.
