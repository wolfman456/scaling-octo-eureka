# scaling-octo-eureka

Spring Boot back end for **Six Kids Crafts**, a small woodworking business. It
serves the public gallery, article journal and site settings, and exposes an
admin API so all content is managed at runtime — **no code redeploy needed to
add a piece, a photo or an article.**

No ecommerce. Sales and order-taking are explicitly out of scope.

The frontend lives in a separate repo: [`refactored-couscous`](../refactored-couscous)
(Vite + React + TypeScript). This service is the API and media origin only.

- **Design, schema and full API contract:** [`docs/DESIGN.md`](docs/DESIGN.md)

## Tech

Java 21 · Spring Boot 4.x · Spring Data JPA · Spring Security (HTTP Basic) ·
Maven wrapper · H2 file DB in development, PostgreSQL in production ·
filesystem media storage · Thumbnailator for image processing.

## Running locally

```sh
./run.sh            # sources ./.env then runs ./mvnw spring-boot:run
./mvnw -B test      # tests + JaCoCo 90% line/branch gate
```

The API listens on `http://localhost:8080`. Start the frontend
(`npm run dev` in the other repo, port 5173) to use the UI; the Vite dev server
proxies `/api` and `/uploads` here.

`run.sh` loads `./.env` if present. See [`.env.example`](.env.example).

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `ADMIN_USER` | `admin` | Seeded admin username, first boot only |
| `ADMIN_PASSWORD` | *(empty)* | Seeded admin password, first boot only; empty → a random one is generated and logged once |
| `ADMIN_RESET_PASSWORD` | *(empty)* | While set, overwrites the stored admin password on **every** boot. Unset it once you are back in |
| `OPENAI_API_KEY` | *(empty)* | Enables the AI writing helpers; empty → 503 |
| `OPENAI_TIMEOUT_MS` | `30000` | Caps an AI helper call; a stalled upstream returns 502 rather than hanging |
| `PORT` | `8080` | HTTP port |
| `APP_ORIGINALS_DIR` | `./originals` | Full-resolution originals, never served |
| `PGHOST` `PGPORT` `PGUSER` `PGPASSWORD` `PGDATABASE` | — | Production Postgres (profile `production`) |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated allowed origins; **required in production** |
| `ADMIN_MAX_LOGIN_ATTEMPTS` | `5` | Failed admin logins before the key is refused with 429 |
| `ADMIN_LOGIN_ATTEMPT_WINDOW` | `PT15M` | Window those failures must fall inside |
| `ADMIN_LOGIN_LOCKOUT` | `PT15M` | How long a locked key stays refused |

There is **no committed default credential.** The admin account is seeded once,
when the database has no admin row, and lives in the DB from then on:

- `ADMIN_PASSWORD` set → that password is used, and it must be at least 8
  characters (a shorter value aborts the boot rather than seeding a weak one).
- `ADMIN_PASSWORD` unset → a random password is generated and printed **once**
  in the startup log. Copy it out of the log, then change it from the admin
  console's Account tab or `POST /api/admin/change-password`.

Forgotten password: set `ADMIN_RESET_PASSWORD` to a new value **and** `ADMIN_USER`
to the existing username, redeploy, log in, then unset both. While
`ADMIN_RESET_PASSWORD` is set it overwrites the stored hash on every boot, so
leaving it behind re-applies the same password on later deploys. `ADMIN_USER`
must be set alongside it: the reset looks the user up by that name and defaults
to `admin`, so a reset without it matches no row and fails the boot.

Failed admin logins are counted per client address and per attempted username;
once either passes `ADMIN_MAX_LOGIN_ATTEMPTS` inside the window, that key gets
**429** with `Retry-After` for `ADMIN_LOGIN_LOCKOUT`. A successful login clears
the count. The counters are in memory and per instance, so a restart clears
them — which is why the lockout is time-boxed rather than permanent. Details and
the trade-offs are in
[`docs/DESIGN.md`](docs/DESIGN.md#admin-login-throttling).

Failed admin logins are counted per client address and per attempted username;
once either passes `ADMIN_MAX_LOGIN_ATTEMPTS` inside the window, that key gets
**429** with `Retry-After` for `ADMIN_LOGIN_LOCKOUT`. A successful login clears
the count. The counters are in memory and per instance, so a restart clears
them — which is why the lockout is time-boxed rather than permanent. Details and
the trade-offs are in
[`docs/DESIGN.md`](docs/DESIGN.md#admin-login-throttling).

## API surface

Public: `GET /api/gallery`, `/api/gallery/{id}`, `/api/categories`,
`/api/articles`, `/api/articles/{slug}`, `/api/settings`.

Admin (HTTP Basic) under `/api/admin/`: media library, gallery pieces, articles,
categories, settings, `change-password`, and the optional AI helpers
(`ai/describe-image`, `ai/draft-article`).

Every response error uses one envelope — `{ status, message, fieldErrors }` —
so the admin UI can surface per-field validation messages. Full details,
including status-code mappings, are in [`docs/DESIGN.md`](docs/DESIGN.md).

## Media handling

Uploaded photos are downscaled to a 2000px longest edge, given a 480px `_thumb`
variant for grids, and their untouched original is moved to a directory that is
never served. `/uploads/**` is cached immutably because filenames are
content-addressed.

A failed upload never leaves a file behind that nothing references, and a
delete is refused before anything is unlinked if the database finds a reference
the in-use check missed. The library never advertises a photo it cannot serve.
Ordering details are in
[`docs/DESIGN.md`](docs/DESIGN.md#media-storage-consistency).

## Deployment

`Dockerfile` builds an API-only image (multi-stage Maven → JRE) with
`SPRING_PROFILES_ACTIVE=production` for a Railway service, paired with a
separate nginx service that serves the frontend bundle and proxies `/api` and
`/uploads`. See [`docs/DESIGN.md`](docs/DESIGN.md) for the layout, the
production config, and the outstanding volume-attachment risk.

In the `production` profile the service **refuses to boot** if the H2 console is
enabled, the datasource is not PostgreSQL, or `APP_CORS_ALLOWED_ORIGINS` is
empty or loopback-only — a misconfigured deploy fails immediately with the list
of problems instead of coming up half-open. Before the first production deploy
of this version, confirm `APP_CORS_ALLOWED_ORIGINS` is set on the Railway API
service.

### Required Railway variables (API service)

| Variable | Example |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `production` |
| `APP_UPLOAD_DIR` | `/app/data/uploads` |
| `APP_ORIGINALS_DIR` | `/app/data/originals` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://<web-service>.up.railway.app` |
| `PGHOST` `PGPORT` `PGDATABASE` `PGUSER` `PGPASSWORD` | from the Postgres service |
| `ADMIN_USER` / `ADMIN_PASSWORD` | optional; only to choose the credential on first boot |

### Attach the volume, then check the deploy

The uploaded photos are **not** in the image. Attach a Railway volume at
`/app/data` to the API service, and confirm it took effect before deploying:

```bash
VOLUME_ROOT=/app/data ./scripts/deploy-check.sh
```

The script fails when the volume is missing, when `APP_UPLOAD_DIR` is not under
it, when the profile or CORS is wrong, or when the media directories are not
writable. This matters because a service with no volume still boots fine: the
database keeps the `media_asset` rows and every photo silently 404s after the
next deploy. Run it against the deployment environment, not a developer shell.

A Railway volume cannot be declared in `railway.json`, so the attachment is a
dashboard step that lives outside version control — the script is the guard, not
the fix. Full runbook, including the nginx DNS resolver and `API_HOST`/`API_PORT`
notes, is in [`docs/DESIGN.md`](docs/DESIGN.md#deploying-to-railway).

## CI

GitHub Actions (`.github/workflows/java-ci.yml`) runs `./mvnw -B test` on every
push to `master` and `develop` and on every PR into either — so the 90% coverage
floor is enforced on every build.
