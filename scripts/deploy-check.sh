#!/usr/bin/env bash
# Pre-deploy check for the backend's production configuration.
#
# Why this exists: the uploaded photos live on a Railway volume, not in the image, and
# the volume attachment is configured outside this repository. A deploy that loses the
# attachment still boots cleanly — the app creates the directory and the database still
# has the media_asset rows — so the gallery goes quietly blank with no error anywhere.
# This check turns that silent failure into a failed deploy step.
#
# Run it against the *deployment* environment (locally exported, or in CI with the
# service's variables), not against a developer shell. It only reads and writes empty
# directories under the target path; it never touches application data.
#
#   ./scripts/deploy-check.sh              # check only
#   VOLUME_ROOT=/app/data ./scripts/deploy-check.sh
#
# Exits non-zero if the environment is not safe to deploy to.
set -euo pipefail

say() { echo "[deploy-check] $*"; }
fail() { echo "[deploy-check] FAIL: $*" >&2; failures=$((failures + 1)); }
failures=0

# The Railway volume must be attached here. This is the single value that, if wrong,
# means every upload is written into the container image layer and lost on redeploy.
VOLUME_ROOT="${VOLUME_ROOT:-/app/data}"

# Injected automatically by Railway when a volume is attached; its presence is the only
# reliable proof the mount actually happened, since a missing mount is still writable.
RAILWAY_VOLUME_MOUNT_PATH="${RAILWAY_VOLUME_MOUNT_PATH:-}"

required_vars=(
  SPRING_PROFILES_ACTIVE
  APP_UPLOAD_DIR
  APP_CORS_ALLOWED_ORIGINS
  PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    fail "required variable $var is unset or empty"
  fi
done

if [[ "${SPRING_PROFILES_ACTIVE:-}" != "production" ]]; then
  fail "SPRING_PROFILES_ACTIVE must be 'production' (got '${SPRING_PROFILES_ACTIVE:-<unset>}')"
fi

upload_dir="${APP_UPLOAD_DIR:-}"
if [[ -n "$upload_dir" && "$upload_dir" != "$VOLUME_ROOT"/* ]]; then
  fail "APP_UPLOAD_DIR must sit under the volume mount $VOLUME_ROOT (got '$upload_dir')"
fi

origins="${APP_CORS_ALLOWED_ORIGINS:-}"
if [[ "$origins" == *localhost* || "$origins" == *127.0.0.1* ]]; then
  fail "APP_CORS_ALLOWED_ORIGINS lists a local origin in production: '$origins'"
fi

if [[ -z "$RAILWAY_VOLUME_MOUNT_PATH" ]]; then
  fail "no volume is attached to this service: RAILWAY_VOLUME_MOUNT_PATH is unset, so uploads are ephemeral"
elif [[ "$RAILWAY_VOLUME_MOUNT_PATH" != "$VOLUME_ROOT" ]]; then
  fail "volume is mounted at '$RAILWAY_VOLUME_MOUNT_PATH' but APP_UPLOAD_DIR expects '$VOLUME_ROOT'"
fi

if [[ $failures -gt 0 ]]; then
  echo "[deploy-check] $failures problem(s); do not deploy. See docs/DESIGN.md#deploying-to-railway" >&2
  exit 1
fi

for dir in "$upload_dir" "${APP_ORIGINALS_DIR:-${VOLUME_ROOT}/originals}"; do
  mkdir -p "$dir"
  if [[ ! -w "$dir" ]]; then
    fail "not writable: $dir"
  fi
done

if [[ $failures -gt 0 ]]; then
  echo "[deploy-check] $failures problem(s); do not deploy." >&2
  exit 1
fi

say "ok: profile, volume mount, CORS and media directories are all deployable"
