# Operations

## Health check

`GET /actuator/health` (public, no details) → `{"status":"UP"}`. Used by the Docker `HEALTHCHECK`. No other actuator
endpoints are exposed.

## Logs

Production logging is `INFO` (`WARN` for Spring Security). The `dev` profile logs SQL and `DEBUG`. Passwords, session
ids and tokens are never logged. Security-relevant events are stored in the database audit log (Admin → Audit Logs).

## Backup

Back up **before every upgrade** and on a schedule.

```bash
# plain SQL dump (consistent snapshot, no table locks for InnoDB)
mysqldump --single-transaction --routines --triggers -h <host> -u <user> -p workpulse_db | gzip > workpulse-$(date +%F).sql.gz

# Docker Compose
docker compose exec db sh -c 'mysqldump --single-transaction -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip > workpulse-$(date +%F).sql.gz
```

Store dumps encrypted and outside the repository (`*.sql.gz` and `backups/` are git-ignored).

## Restore

```bash
# 1. stop the application
# 2. recreate an empty database, then load the dump
mysql -h <host> -u <user> -p -e "DROP DATABASE IF EXISTS workpulse_db; CREATE DATABASE workpulse_db CHARACTER SET utf8mb4;"
gunzip -c workpulse-2026-03-04.sql.gz | mysql -h <host> -u <user> -p workpulse_db
# 3. start the application: Flyway sees its own history table in the dump and applies only newer migrations
```

Practise a restore on a scratch database at least once.

## Upgrading

1. Back up. 2. Deploy the new version. 3. Flyway applies new `V*.sql` files automatically at startup.
Migrations are additive/non-destructive by policy; never edit an already-released migration — add a new `V<n+1>`.

## Rotating the first admin password

Sign in → Profile → Change password. If locked out, another admin can reset it (Employees → Reset password). If no
admin can sign in, stop the app and start it once with `WORKPULSE_ADMIN_EMAIL`/`WORKPULSE_ADMIN_PASSWORD` set to a
**new** e-mail (bootstrap only runs when no active admin exists, so first deactivate the lost admin in the database:
`UPDATE users SET enabled = 0 WHERE email = '...'` and `UPDATE employees SET status = 'INACTIVE' WHERE user_id = ...`).

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `Could not resolve placeholder 'DB_USERNAME'` | `DB_USERNAME`/`DB_PASSWORD` are required (no defaults). Export them or use `.env` with Compose. |
| `Public Key Retrieval is not allowed` | MySQL 8 with `caching_sha2_password` over a non-TLS link: set `DB_ALLOW_PUBLIC_KEY_RETRIEVAL=true` (private networks only) or enable TLS (`DB_USE_SSL=true`). |
| `Unable to obtain connection` at startup | MySQL not reachable / wrong host or credentials; with Compose wait for the `db` health check. |
| Flyway: `Found non-empty schema(s) ... without schema history table` | Only on schemas that are not the old WorkPulse. Use an empty database, or set `spring.flyway.baseline-version` deliberately. |
| `WORKPULSE_ADMIN_PASSWORD rejected: ...` | The bootstrap password must meet the policy (12–128 chars, upper/lower/digit/special). |
| Login works but you are logged out immediately over HTTPS | Set `COOKIE_SECURE=true` only when actually using HTTPS; the reverse proxy must forward the scheme. |
| 403 on every POST from your own client | Send the `X-XSRF-TOKEN` header with the value of the `XSRF-TOKEN` cookie (Postman collection does this). |
| Times look off by hours | Check `APP_TIMEZONE`; all stored timestamps are UTC and displayed in the company zone. |
| "Too many failed login attempts" | Wait 5 minutes (or restart the app to clear the in-memory counter). |
