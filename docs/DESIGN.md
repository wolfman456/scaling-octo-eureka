# Backend design — scaling-octo-eureka

> **This file is the source of truth for the backend.** The workspace-root
> `drawdesign.md` is a cross-repo index and deliberately holds no detail here —
> if you need the schema or the API contract, it is in this file.

## Goal

Spring Boot back end for the Six Kids Crafts gallery: serve a public gallery,
articles, and site settings for a small woodworking business and let an admin
manage all of it over the API — **no code redeploy needed** for content.

No ecommerce. Sales/order-taking is explicitly out of scope.

## Tech

Java 21, Spring Boot 4.x (parent version **without** the `.RELEASE` suffix —
e.g. `4.1.1`, central does not publish `4.1.1.RELEASE`), Maven wrapper,
Spring Data JPA, Spring Security (HTTP Basic), filesystem media storage.

## Runtime storage

Media is filesystem-backed in every environment:

- Served images: `app.upload-dir` (default `./uploads`, production
  `/app/data/uploads`), mapped at `/uploads/**` by a resource handler. The URL is
  always derived: `/uploads/<stored_name>`.
- Full-resolution originals: `app.original-dir` (default `./originals`,
  production `/app/data/originals`). This directory is **outside** the served
  tree, so originals are never reachable over HTTP.
- Thumbnails: `<stored_name>_thumb.jpg`, written **beside** the display image
  inside `app.upload-dir` so it is served exactly like any other upload.
- `data/`, `uploads/` and `originals/` are gitignored — content is runtime,
  never committed.

The database differs by profile:

| | Database | Config |
|---|---|---|
| Dev / default | H2 embedded **file** | `jdbc:h2:file:./data/gallery`, `ddl-auto=update` |
| `production` | Railway-managed **PostgreSQL** | `jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}` |

`application-production.properties` disables the H2 console and reads
`PGHOST` / `PGPORT` / `PGUSER` / `PGPASSWORD` / `PGDATABASE`, which Railway
supplies automatically for a managed Postgres service. In dev,
`ddl-auto=update` lets the schema evolve in place across restarts; the H2
console is available at `/h2-console`.

## Security model

Two filter chains, ordered:

1. `h2ConsoleSecurityFilterChain` — `@Profile("!production")`, matches
   `/h2-console/**`, permits everything, disables CSRF (the console posts).
   Outside production only, so a dev database stays browsable.
2. `securityFilterChain` — every other request. Public reads, `/uploads/**` and
   the SPA routes are `permitAll`; `/api/admin/**` requires role `ADMIN`;
   everything else requires authentication. **`/h2-console/**` is deliberately
   not listed here** — in production it falls through to
   `anyRequest().authenticated()`, so even if the console were accidentally
   enabled it would still need credentials.

### Production startup guard

`ProductionConfigGuard` is an `ApplicationRunner` with `@Profile("production")`.
On boot it refuses to start (throwing with every problem listed at once) when:

- `spring.h2.console.enabled` is `true` — an unauthenticated database console;
- `spring.datasource.url` is not a `jdbc:postgresql:` URL — an embedded
  database starts empty on every deploy;
- `app.cors.allowed-origins` is empty, or lists only loopback origins
  (`localhost`, `127.0.0.1`, `0.0.0.0`, `::1`) — the admin UI would otherwise
  fail with opaque 403s.

It also logs the H2-console flag, the datasource (scheme only, credentials and
host redacted) and the effective CORS origins on every production boot, so a
misconfigured deploy is diagnosable from the logs alone. `WebConfig` logs the
parsed origin list in every profile.

### Admin login throttling

Basic auth on `/api/admin/**` is otherwise an unthrottled oracle: bcrypt cost 10
slows each guess but there is no limit on how many guesses arrive.
`AdminLoginThrottleFilter` sits ahead of the Basic filter so a locked key is
refused with **429** and a `Retry-After` header without paying for a bcrypt
comparison — otherwise the limiter would only make brute force slower, not stop
it.

Each failure is counted against **two keys**, and both must be clear to let a
request through:

- the client address, from `X-Forwarded-For` (then `X-Real-IP`, then
  `remoteAddr`), which stops password spraying — one password, many accounts,
  from one host;
- the attempted username, read from the Basic header before authentication runs,
  which stops a distributed attack on the single admin account.

A success clears both keys, so a mistyped password does not accumulate toward a
lockout. A 401 with no usable credentials still counts against the address key.

Configurable, all with the defaults shown:

| Property | Default |
|---|---|
| `app.admin.max-login-attempts` / `ADMIN_MAX_LOGIN_ATTEMPTS` | `5` |
| `app.admin.login-attempt-window` / `ADMIN_LOGIN_ATTEMPT_WINDOW` | `PT15M` |
| `app.admin.login-lockout` / `ADMIN_LOGIN_LOCKOUT` | `PT15M` |

Failures outside the window do not accumulate: someone who mistypes a password
once a month is not eventually locked out by it.

**Trade-offs, stated plainly:**

- The username bucket is **global, not per address**, so an attacker can lock the
  real admin out for the lockout period. That is the deliberate side of the same
  choice that stops a distributed attack — a per-address username bucket would
  provide no protection against the threat it exists for.
- State is **in memory and per instance**. A restart or a second replica clears
  it. That is why the lockout is time-boxed rather than permanent, and why no
  persistent store is involved; an admin login limiter is not worth another set of
  credentials to protect.
- Because the limiter reads `X-Forwarded-For`, it is only as trustworthy as the
  proxy in front. The nginx config does not currently forward that header, so in
  the deployed topology every request shares one address bucket and the username
  bucket does the real work. Forwarding it from nginx is a frontend-repo change
  and is not done here.

The 429 body uses the standard error envelope, but the 401 is left entirely to
Spring Security: it owns the `WWW-Authenticate` challenge that makes a browser
prompt for credentials.

## Admin credentials

There is no committed default credential. `AdminUserSeeder` runs on every boot
and does two things, both in `AdminUserService`:

- `seedIfEmpty(username, password)` — only when the database holds no admin row,
  so a later deploy can never silently rewrite a working password. A blank
  `ADMIN_PASSWORD` produces a random 18-byte password, logged once at WARN and
  never stored in plaintext. A non-blank value must be 8 characters or more and
  at most 72 UTF-8 bytes — a shorter or longer one aborts the boot rather than
  seeding a weak or silently truncated credential (BCrypt ignores everything
  past 72 bytes, so the upper bound is measured in bytes, not characters).
- `resetPasswordIfRequested(username, password)` — the recovery path. While
  `ADMIN_RESET_PASSWORD` is set it overwrites the stored hash on **every** boot
  and logs a WARN telling the operator to unset it. Unset (or blank) it and the
  call is a no-op, so a forgotten variable cannot re-apply a stale password on
  a later deploy. The lookup is by `ADMIN_USER`, so **a reset with `ADMIN_USER`
  unset fails the boot** — it defaults to `admin`, and an `IllegalStateException`
  from this `ApplicationRunner` takes the service down rather than silently
  doing nothing. Set `ADMIN_USER` to the stored username in the same change as
  `ADMIN_RESET_PASSWORD`.

Every write to `admin_user.password_hash` is logged: the seed, an env-driven
reset, and a change through `POST /api/admin/change-password` (which enforces
the same 8-char/72-byte bounds and returns 400 when they are violated). Nothing
else in the codebase writes that column.

## Image pipeline

Uploads are resized on the way in by `ImageProcessingService` (Thumbnailator),
driven by `application.properties`:

| Property | Default | Effect |
|---|---|---|
| `app.image.max-dimension` | `2000` | Display copies are downscaled to this longest edge |
| `app.image.thumb-dimension` | `480` | Grid thumbnail longest edge |
| `app.image.quality` | `0.82` | JPEG output quality |

`MediaStorageService.store` moves the uploaded file into the originals directory,
writes the downscaled display copy back into the upload directory, and generates
the 480px thumbnail. A thumbnail is only produced for JPEGs that are actually
larger than the thumbnail edge — otherwise `thumbnail_name` stays `null` and the
client simply renders the display image.

`MediaBackfillRunner` runs once on `ApplicationReadyEvent` and applies the same
shrink pass to assets uploaded **before** the pipeline existed. It is safe to run
on every boot: it skips assets that are already small enough and leaves the
preserved original in place.

## Schema

PKs are BIGINT identities. Generated by Hibernate.

```
gallery_item                  -- a finished piece
  id            BIGINT PK
  title         VARCHAR(200)
  description   VARCHAR(2000) NULL
  category_id   BIGINT FK -> category.id NULL
  sort_order    INTEGER DEFAULT 0
  published     BOOLEAN DEFAULT FALSE
  created_at    TIMESTAMP
  updated_at    TIMESTAMP

category                      -- gallery tab / grouping, admin-managed
  id            BIGINT PK
  name          VARCHAR(100)
  sort_order    INTEGER DEFAULT 0
  created_at    TIMESTAMP

media_asset                   -- photo-library asset on disk under uploads/
  id             BIGINT PK
  asset_type     VARCHAR(10)               -- IMAGE | VIDEO
  stored_name    VARCHAR(255)              -- URL = /uploads/<stored_name>
  thumbnail_name VARCHAR(255) NULL         -- URL = /uploads/<thumbnail_name>
  content_type   VARCHAR(100)
  size_bytes     BIGINT
  sort_order     INTEGER DEFAULT 0
  uploaded_at    TIMESTAMP

item_media                    -- join: many library photos per piece, ordered
  item_id       BIGINT FK -> gallery_item.id
  media_id      BIGINT FK -> media_asset.id
  PK (item_id, media_id)

article                       -- journal/news posts with attached photos
  id            BIGINT PK
  title         VARCHAR(200)
  slug          VARCHAR(200) UNIQUE
  body_md       TEXT
  featured_media_id BIGINT FK -> media_asset.id NULL
  published     BOOLEAN DEFAULT FALSE
  published_at  TIMESTAMP NULL
  created_at    TIMESTAMP
  updated_at    TIMESTAMP

article_media                 -- join: photos attached to an article
  article_id    BIGINT FK -> article.id
  media_id      BIGINT FK -> media_asset.id
  PK (article_id, media_id)

site_setting                  -- key/value site config
  key           VARCHAR(50) PK
  value         VARCHAR(500)
```

`media_asset` is the first-class photo-library object. Gallery items, articles
and the site background all reference library media by id, so the same photo can
be reused in several places. Deleting an item or article never deletes shared
library media.

`media_asset.sort_order` is only meaningful for a photo's position inside a
gallery item's own media list (set from the list index on the inline-cover
upload). **Library uploads carry no sort order at all** — the library is listed
`uploaded_at DESC`, so nothing derives an order from a row count.

## Media storage consistency

Files and rows are two stores that cannot share a transaction, so the ordering
is deliberate. Nothing here trusts the caller to clean up.

- **Write.** The file (and its thumbnail/original) lands on disk first, then the
  row. As soon as the file is stored, `MediaStorageService.deleteOnRollback`
  registers a transaction callback that removes it if the transaction rolls back.
  A try/catch around the save is not enough: the row is discarded by *any* later
  failure — the gallery item save, a constraint found at flush, even serialising
  the response — and rollback is the one moment both halves are known to be
  unwound. Cleanup itself never throws, because it runs while the original error
  is already propagating and must not replace it.
- **Delete.** The row is deleted and **flushed** first, then the files. Flushing
  forces the foreign keys to be checked before anything is unlinked: a reference
  that landed after the usage check fails the delete while the files are still
  intact. If the file delete then fails, the exception rolls the row back and its
  files are still there to match. Deleting files first — the old behaviour — put
  a rollback between the two and could leave a restored row pointing at a file
  that was already gone.
- **Concurrent reference.** `MediaUsageService.isInUse` is a read-then-write, so
  a reference committed in the gap would be missed by the check. The database
  foreign keys on `item_media.media_id`, `article_media.media_id` and
  `article.featured_media_id` are the backstop, and `ApiExceptionHandler` already
  maps that violation to 409. The site background is the one exception: it is
  stored as an id string in `site_setting`, not a foreign key, so that reference
  genuinely does rely on the usage check.

Not built: an `app_user` accounts table (admin is a single seeded credential) and
a `video` table (`media_asset.asset_type` anticipates it, but nothing creates or
serves video yet).

## API contract

### Public (no auth)

- `GET /api/gallery` → `[GalleryItemDto]` (published only, category then
  `sortOrder`)
- `GET /api/gallery/{id}` → single published item; 404 if missing/unpublished
- `GET /api/categories` → `[{ id, name, sortOrder }]`
- `GET /api/articles` → `[ArticleSummaryDto]` (published only)
- `GET /api/articles/{slug}` → `ArticleDto`; 404 if missing/unpublished
- `GET /api/settings` → `SiteSettingsDto`

`GalleryItemDto` is
`{ id, title, description, categoryId, categoryName, sortOrder, published, createdAt, images, thumbnails, mediaIds }`.
`images`, `thumbnails` and `mediaIds` are parallel lists over the same photo
sequence; `thumbnails[i]` is `null` when that photo has no generated thumbnail.

`ArticleSummaryDto` is
`{ id, title, slug, publishedAt, featuredImage, featuredThumbnail, images, thumbnails }`;
`ArticleDto` adds `bodyMd`, `published`, `createdAt`, `featuredMediaId` and
`mediaIds`.

`SiteSettingsDto` is
`{ siteTitle, backgroundMediaId, backgroundImage, contactEmail, etsyUrl, instagramUrl, facebookUrl }`,
where `backgroundImage` resolves the stored media to `/uploads/<name>`. There is
no background thumbnail — the site shell uses the full image as a CSS background.

### Admin (HTTP Basic; seeded on an empty database from `ADMIN_USER`/`ADMIN_PASSWORD`, stored in the DB afterwards, role `ADMIN`)

- Media library
  - `GET /api/admin/media` → list (newest first)
  - `POST /api/admin/media` multipart `file` → 201 asset
  - `DELETE /api/admin/media/{id}` → 204; **409 if in use** (gallery item, article media, article featured, or site background); 404 if missing. If the file cannot be deleted the row is kept and the request fails 500 — see "Media storage consistency"
- Gallery
  - `POST /api/admin/gallery` multipart: part `item` (JSON: title, description, categoryId, sortOrder, published, mediaIds[]), part `image` optional (uploads a new library asset **and persists it before** the item is saved)
  - `PUT /api/admin/gallery/{id}` multipart: same parts; replaces the media list via `mediaIds`
  - `DELETE /api/admin/gallery/{id}` → 204 (media stays in the library)
- Categories
  - `GET/POST /api/admin/categories`, `PUT /api/admin/categories/{id}`, `DELETE .../{id}` (204; **409 if a gallery item uses it**)
- Articles
  - `GET /api/admin/articles` → list (all, incl. drafts/private fields)
  - `POST /api/admin/articles` JSON: title, slug?, bodyMd, featuredMediaId?, published, mediaIds[]
  - `PUT /api/admin/articles/{id}` JSON: same; slug auto-uniquified unless unchanged
  - `DELETE /api/admin/articles/{id}` → 204
- Settings
  - `GET /api/admin/settings` → current settings
  - `PUT /api/admin/settings` JSON → merges only non-null fields; empty string clears the key
- Account
  - `POST /api/admin/change-password` JSON: currentPassword, newPassword (min 8 chars, max 72 UTF-8 bytes) → 200 `{ username }`; 400 if the current password is wrong or the new one is out of bounds. The new password takes effect on the next request.
- AI (optional — see below)
  - `POST /api/admin/ai/describe-image` multipart `file` (image content types only) → `{ description }`
  - `POST /api/admin/ai/draft-article` JSON `{ topic, mediaIds[] }` → `{ title, bodyMd }`; the referenced library photos are passed to the model as grounding context

## Error contract

`ApiExceptionHandler` is a `@RestControllerAdvice` that normalises every failure
into a single envelope:

```json
{ "status": 400, "message": "Title must not be blank", "fieldErrors": [{ "field": "title", "message": "must not be blank" }] }
```

`fieldErrors` is `null` for non-validation failures. Mappings:

| Status | Cause |
|---|---|
| 400 | `MethodArgumentNotValidException`, `ConstraintViolationException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`, `MissingServletRequestPartException`, `IllegalArgumentException` |
| 404 | missing/unpublished resource |
| 409 | `DataIntegrityViolationException` (e.g. duplicate slug), media or category still in use, `IncorrectResultSizeDataAccessException` |
| 429 | too many failed admin logins (throttle filter, with `Retry-After`) |
| 413 | `MaxUploadSizeExceededException` |
| 502 | OpenAI upstream failure (`AiCallException`) |
| 503 | AI helpers disabled — no `OPENAI_API_KEY` (`AiDisabledException`) |
| 500 | catch-all; the message is logged, not returned |

All admin form DTOs carry bean validation annotations, so invalid input is
rejected with a 400 and a per-field message rather than surfacing as a 500.

## AI helpers (optional)

Configured by `app.openai.*` in `application.properties`:

| Property | Default | Notes |
|---|---|---|
| `app.openai.api-key` | `${OPENAI_API_KEY:}` | **Empty disables the feature** |
| `app.openai.base-url` | `https://api.openai.com/v1` | any OpenAI-compatible endpoint |
| `app.openai.model` | `gpt-4o-mini` | |
| `app.openai.timeout-ms` | `${OPENAI_TIMEOUT_MS:30000}` | Connect **and** read timeout for the OpenAI client; must be positive |
| `app.admin.max-login-attempts` | `${ADMIN_MAX_LOGIN_ATTEMPTS:5}` | Failures before an admin login is refused with 429 |
| `app.admin.login-attempt-window` | `${ADMIN_LOGIN_ATTEMPT_WINDOW:PT15M}` | Window those failures must fall inside |
| `app.admin.login-lockout` | `${ADMIN_LOGIN_LOCKOUT:PT15M}` | How long a locked key stays refused |

The endpoints are admin-only and stateless. With no key configured,
`OpenAiService` throws `AiDisabledException` → **503**; an upstream failure
throws `AiCallException` → **502**.

The OpenAI client gets its own `JdkClientHttpRequestFactory` rather than the
auto-configured one, so the call is bounded by `app.openai.timeout-ms` instead of
running as long as the upstream cares to stall. Connect and read share that one
budget: the read timeout is what caps the wait, and the connect timeout stops an
unreachable host from consuming it. Without it a stalled call held the admin
request thread indefinitely — nothing upstream cancels it and no error handler
can rescue a request that never fails. A timeout surfaces as a normal **502**,
naming the budget it exceeded. Both are returned in the standard error
envelope, so the admin panels surface the message in their form-error area
while the rest of the form keeps working.

## Cache policy

`CacheHeaderWriter` is a Spring Security `HeaderWriter` with a two-branch
policy:

- `/uploads/**` → `public, max-age=31536000, immutable`. Uploaded files are
  content-addressed (UUID), so the bytes behind a URL never change.
- Everything else → `no-cache, no-store, max-age=0, must-revalidate`, including
  all API responses, so content edits appear immediately.

## Slug generation

`SlugService` lowercases, transliterates to `[a-z0-9]`, dashes spare characters,
and appends `-2`, `-3`, … until unique against `article.slug`.

`ArticleSlugIndexInitializer` is `@Profile("production")`: on boot it queries
PostgreSQL `information_schema` for a UNIQUE constraint on `article.slug` and
creates one if it is missing, so a production database that predates the index
gets it on the next deploy. It is Postgres-specific by design.

## Deployment

Two Railway services, split at the web tier (see the frontend repo's
`Dockerfile`/`nginx.conf.template`):

- **API** — this repo's `Dockerfile`: multi-stage `maven:3.9-eclipse-temurin-21`
  build → `eclipse-temurin:21-jre` runtime, `SPRING_PROFILES_ACTIVE=production`,
  `APP_UPLOAD_DIR=/app/data/uploads`, `APP_ORIGINALS_DIR=/app/data/originals`,
  port 8080. The image is API-only; it does not serve the frontend bundle.
- **Web** — nginx serving the static build and reverse-proxying `/api` and
  `/uploads` to the API service. It takes `API_HOST` (the API's private
  `.railway.internal` hostname) and `API_PORT` (8080), and resolves the upstream
  per request via `resolver [fd12::10]:53 valid=5s`. That resolver line is
  load-bearing: Railway reassigns the API container's IP on redeploy, so an nginx
  that resolved the name once at startup would 502 until it restarted.

`SpaController` forwards any non-file path to `forward:/index.html` so
client-side routes survive a hard refresh if the bundle is ever served by this
service instead.

### Deploying to Railway

The image sets `SPRING_PROFILES_ACTIVE=production`,
`APP_UPLOAD_DIR=/app/data/uploads` and `APP_ORIGINALS_DIR=/app/data/originals` as
`ENV`, so those hold even if the service variables are lost. The rest must be set
on the **API** service:

| Variable | Example | Required |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `production` | yes — set as a variable too, so a bad image default cannot boot non-production |
| `APP_UPLOAD_DIR` | `/app/data/uploads` | yes — must be **under the volume mount** |
| `APP_ORIGINALS_DIR` | `/app/data/originals` | yes — same volume |
| `APP_CORS_ALLOWED_ORIGINS` | `https://<web-service>.up.railway.app` | yes — must not list localhost |
| `ADMIN_USER` / `ADMIN_PASSWORD` | *(unset)* | only on first boot, to choose the admin credential |
| `PGHOST` `PGPORT` `PGDATABASE` `PGUSER` `PGPASSWORD` | Railway-provided | yes — from the Postgres service |

`ProductionConfigGuard` refuses to boot on a wrong profile, an H2 console, a
non-PostgreSQL datasource, or an empty/loopback-only CORS allow-list — it lists
every problem at once rather than failing on the first.

**The volume is the part that is not in this repo.** A Railway volume cannot be
declared in `railway.json`; it is attached to the service in the dashboard, and
Railway injects `RAILWAY_VOLUME_MOUNT_PATH` when it takes effect. Attach one at
`/app/data` before the first deploy that accepts uploads.

This matters more than it looks: a service with **no** volume still boots
cleanly. The app creates the upload directory, the `media_asset` rows persist in
Postgres, and every photo silently 404s after the next deploy. The old rows
cannot even be deleted, because `MediaUsageService` still sees the references.
So the deploy gate is a script, not a hope:

```bash
VOLUME_ROOT=/app/data ./scripts/deploy-check.sh
```

It verifies the profile, that `APP_UPLOAD_DIR` sits under the volume root, that
`RAILWAY_VOLUME_MOUNT_PATH` is present and matches, that CORS is not local, and
that the media directories are writable — then exits non-zero with every problem
listed. Run it against the deployment environment (exported variables, or a CI
step holding the service's) before promoting a deploy.

`RAILWAY_VOLUME_MOUNT_PATH` is the load-bearing check: a missing mount is still a
writable directory, so no amount of write-testing detects it.

> **Open risk:** the volume attachment lives in the Railway dashboard, not in
> version control. `scripts/deploy-check.sh` fails the build when it is absent,
> but nothing in this repository can create it — see #21 for the object-storage
> alternative that would remove the dependency entirely.

## Testing & coverage gate

- JaCoCo (`jacoco-maven-plugin 0.8.13`) `check` goal bound to the `test` phase:
  **≥ 0.90 LINE and ≥ 0.90 BRANCH** on the whole bundle, so a plain
  `./mvnw -B test` enforces the 90% floor in CI.
- Tests use an isolated H2 in-mem DB (`jdbc:h2:mem:gallerytest`) and
  `app.upload-dir=target/test-uploads`, never the dev `./data` DB or `./uploads`.
- `OpenAiServiceTest` runs against a stub server (`StubOpenAiServer`) rather
  than a real API call.
- Jackson 3 (Boot 4): test code imports `tools.jackson.databind.*`
  (`spring-boot-starter-jackson-test`); do not use `com.fasterxml.jackson.databind`.

## Config reference

- Multipart cap ~20MB per image, ~25MB request.
- Admin credentials come from `ADMIN_USER` (default `admin`) and `ADMIN_PASSWORD`
  (no default — unset means a generated password logged once); see *Admin
  credentials*. `ADMIN_RESET_PASSWORD` is the opt-in recovery path.
- CORS allows `http://localhost:5173` (dev frontend). Production **must** set
  `APP_CORS_ALLOWED_ORIGINS` to the deployed frontend origin — a loopback-only
  or empty value now aborts the production boot
  (`ProductionConfigGuard`).
- H2 console enabled at `/h2-console` outside production only; in production it
  is disabled by config *and* unreachable without authentication.
- `APP_UPLOAD_DIR` / `APP_ORIGINALS_DIR` must be on a persistent volume; the
  service checks at boot that the upload directory is creatable and writable.

## Gotchas

- **`start.spring.io` reports a `.RELEASE` suffix that does not exist on Maven
  Central.** Strip it (`4.1.1.RELEASE` → `4.1.1`) or the build cannot resolve
  the parent POM.
- Spring Boot 4 renamed test starters/packages: e.g. `AutoConfigureMockMvc` is in
  `org.springframework.boot.webmvc.test.autoconfigure`.
- With `spring.jpa.open-in-view=false`, request handlers that touch lazy
  `media` / `category` / `featuredMedia` collections must be `@Transactional`.
- Always run `./mvnw`, not the system Maven (3.6.3 is too old for this build).
- A second `SecurityFilterChain` bean needs an explicit `@Order`; without it the
  two chains race and the H2 console chain can shadow the API chain.

## Known decisions

- Gallery item `published` defaults to **false** at the API (drafts stay hidden
  until explicitly published); the frontend always sends an explicit boolean.
- Category is a real table (tabs); there are no hard-coded default tabs.
- Media is shared: deleting a gallery item or article never deletes library
  photos, and deleting a photo in use returns 409 rather than orphaning a
  reference.
- Failing a production boot on a misconfigured security setting was chosen over
  serving a wide-open service: a refused deploy is recoverable, an exposed
  database is not.
- Out of scope: a real admin accounts table (single seeded Basic-auth
  credential), video hosting (`asset_type` is `IMAGE | VIDEO` but large-video
  handling is unresolved), and any ecommerce/ordering.
