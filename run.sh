#!/usr/bin/env bash
# Run the backend with local secrets loaded from ./.env (gitignored).
set -a
[ -f .env ] && source .env
set +a
exec ./mvnw spring-boot:run "$@"
