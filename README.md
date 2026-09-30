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
| `ADMIN_USER` | `bloodwolf` | Seeded admin username, first boot only |
| `ADMIN_PASSWORD` | `NeedToChange` | Seeded admin password, first boot only |
| `OPENAI_API_KEY` | *(empty)* | Enables the AI writing helpers; empty → 503 |
| `PORT` | `8080` | HTTP port |
| `APP_ORIGINALS_DIR` | `./originals` | Full-resolution originals, never served |
| `PGHOST` `PGPORT` `PGUSER` `PGPASSWORD` `PGDATABASE` | — | Production Postgres (profile `production`) |

The admin credential is seeded **once**, on an empty database, and then lives in
the DB. Change it from the admin console's Account tab, or
`POST /api/admin/change-password`. The `bloodwolf`/`NeedToChange` fallback only
applies before that first change — treat it as a to-do, not a credential.

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

## Deployment

`Dockerfile` builds an API-only image (multi-stage Maven → JRE) with
`SPRING_PROFILES_ACTIVE=production` for a Railway service, paired with a
separate nginx service that serves the frontend bundle and proxies `/api` and
`/uploads`. See [`docs/DESIGN.md`](docs/DESIGN.md) for the layout, the
production config, and the outstanding volume-attachment risk.

## CI

GitHub Actions (`.github/workflows/java-ci.yml`) runs `./mvnw -B test` on every
push to `master` and `develop` and on every PR into either — so the 90% coverage
floor is enforced on every build.
