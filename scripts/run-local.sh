#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

export MODULES_DIR="${MODULES_DIR:-build/runtime/modules}"
export MODULES_CONFIG_DIR="${MODULES_CONFIG_DIR:-config}"

./gradlew prepareRuntime
exec java ${JAVA_OPTS:-} -jar build/runtime/app.jar
