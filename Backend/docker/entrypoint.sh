#!/bin/sh
set -eu

mode="${1:-serve}"
if [ "$#" -gt 0 ]; then shift; fi

case "$mode" in
  serve)
    exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar /app/app.jar "$@"
    ;;
  migrate)
    exec java -cp '/app/migrator.jar:/app/lib/*' com.hkv.AiTherapy.migration.MigrationMain "$@"
    ;;
  *)
    echo 'Usage: container [serve|migrate]' >&2
    exit 2
    ;;
esac
