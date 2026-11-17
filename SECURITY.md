# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take the security of WorkPulse seriously. If you believe you have discovered a security vulnerability in this project, please follow these steps:

1. **Do NOT report security vulnerabilities via public GitHub issues, discussions, or pull requests.**
2. Send an email to `security@example.com` (or contact the project lead privately) with details of the vulnerability.
3. Include the following details in your report:
   - Type of issue (e.g., authentication bypass, IDOR, SQL injection, CSRF)
   - Step-by-step instructions to reproduce the issue
   - A proof-of-concept payload or HTTP request transcript, if applicable
   - The affected endpoints, parameters, and roles
   - Potential impact and suggested mitigations
4. You will receive an acknowledgment within 48 hours and regular updates on triage and remediation.
5. Please allow a reasonable disclosure window before any public coordination.

## Security Controls Overview

WorkPulse implements defense-in-depth controls across all application tiers:
- **Authentication**: BCrypt hashed passwords, strict complexity policy (12-128 chars, mixed case, digit, symbol).
- **Session Protection**: Session fixation rotation upon login, explicit invalidation on logout, password change, and deactivation.
- **Throttling**: Temporary login throttling on repeated failed attempts per account/IP key without revealing account existence.
- **CSRF**: Cookie-based CSRF repository with `X-XSRF-TOKEN` validation on all state-changing endpoints (`POST`, `PUT`, `PATCH`, `DELETE`).
- **Authorization**: Method-level security (`@PreAuthorize`) paired with centralized domain ownership validation (`AccessService`) to eliminate Insecure Direct Object References (IDOR).
- **Headers**: Strict Content Security Policy (CSP), `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and HSTS.
- **Data Protection**: Append-only immutable audit log without update/delete access; formula-injection sanitization on CSV exports.
