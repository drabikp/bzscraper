#!/bin/sh
# The database's backup (compose service "backup"): pg_dump in PostgreSQL's custom format on
# start and then every day into /backups, keeping the newest $KEEP. Restore into an empty
# database: pg_restore -d bzscraper --no-owner bzscraper-<day>.dump
set -eu
KEEP="${KEEP:-14}"
PGPASSWORD="$(cat /run/secrets/db_password)"
export PGPASSWORD
while true; do
    day="$(date +%F)"
    if pg_dump --format=custom --file="/backups/bzscraper-$day.dump.part"; then
        mv "/backups/bzscraper-$day.dump.part" "/backups/bzscraper-$day.dump"
        echo "backup: /backups/bzscraper-$day.dump"
        # ISO dates in the names sort by age; drop all but the newest $KEEP
        ls -1 /backups/bzscraper-*.dump | sort | head -n "-$KEEP" | while read -r old; do rm -f "$old"; done
    else
        rm -f "/backups/bzscraper-$day.dump.part"
        echo "backup: pg_dump failed, next try in an hour" >&2
        sleep 3600
        continue
    fi
    sleep 86400
done
