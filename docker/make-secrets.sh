#!/usr/bin/env bash
# Builds ./secrets (git-ignored, readable by the owner only) for compose.yaml from the
# git-ignored login files of a local checkout. Prints only the names, never a value.
# Keeps a secret that already exists; delete its file to make it again.
#   .bz-creds          line 1 Bandzone login, line 2 password
#   .bit-test-creds    line 1 Bandsintown login, line 2 password, line 3 authenticator secret
#   calendar           line 1 the band calendar's private iCal address
#   .app-login         the app's sign-in password
# Missing sources give empty files (that platform / the calendar stays unconfigured).
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p secrets
chmod 700 secrets

# secret name, source file, line ("" = the whole file)
put() {
    local name="$1" source="$2" line="$3" target="secrets/$1"
    if [[ -s "$target" ]]; then
        echo "kept    $target"
        return
    fi
    if [[ -f "$source" ]]; then
        if [[ -n "$line" ]]; then
            sed -n "${line}p" "$source" | tr -d '\r\n' > "$target"
        else
            tr -d '\r\n' < "$source" > "$target"
        fi
        echo "made    $target (from $source)"
    else
        : > "$target"
        echo "empty   $target ($source not found)"
    fi
}

if [[ -s secrets/db_password ]]; then
    echo "kept    secrets/db_password"
else
    head -c 32 /dev/urandom | base64 | tr -d '/+=\n' > secrets/db_password
    echo "made    secrets/db_password (random)"
fi
if [[ -f .app-login ]]; then
    put app_password .app-login ""
elif [[ ! -s secrets/app_password ]]; then
    head -c 18 /dev/urandom | base64 | tr -d '/+=\n' > secrets/app_password
    echo "made    secrets/app_password (random — read it there to sign in)"
fi
put bandzone_login .bz-creds 1
put bandzone_password .bz-creds 2
put bandsintown_login .bit-test-creds 1
put bandsintown_password .bit-test-creds 2
put bandsintown_totp .bit-test-creds 3
put calendar_url calendar 1
chmod 600 secrets/*
