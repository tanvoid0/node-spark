#!/usr/bin/env bash
# Re-encrypts the signing key under a fresh random passphrase and updates .env with it.
#
# The key itself is unchanged, so the publisher identity and the certificate stay valid - only the
# passphrase protecting the file on disk is replaced. Run this if the old one was ever printed,
# logged, or shared.
#
#   & "C:\Program Files\Git\bin\bash.exe" scripts/rotate-key-passphrase.sh
#
# Nothing here echoes a passphrase: the new one is generated, used, and written to .env in this
# process. Never run a signing or publishing task with --info or --debug, which makes Gradle log the
# signer's whole command line, passphrase argument included.
set -euo pipefail

cd "$(dirname "$0")/.."

key="secrets/private.pem"
env_file=".env"

command -v openssl >/dev/null || { echo "openssl is not on PATH." >&2; exit 1; }
[ -f "$key" ] || { echo "$key does not exist - run scripts/new-signing-key.sh first." >&2; exit 1; }
[ -f "$env_file" ] || { echo "$env_file does not exist." >&2; exit 1; }

old="$(sed -n 's/^PRIVATE_KEY_PASSWORD=//p' "$env_file" | head -n 1)"
[ -n "$old" ] || { echo "PRIVATE_KEY_PASSWORD is not set in $env_file." >&2; exit 1; }

new="$(openssl rand -base64 32)"

rotated="secrets/.rotated-private.pem"
trap 'rm -f "$rotated"' EXIT

openssl pkey -in "$key" -passin "pass:$old" \
  -aes-256-cbc -passout "pass:$new" -out "$rotated"

# Prove the new file opens with the new passphrase before anything is replaced.
openssl pkey -in "$rotated" -passin "pass:$new" -noout

mv "$rotated" "$key"
chmod 600 "$key" 2>/dev/null || true

tmp="$(mktemp "${env_file}.XXXXXX")"
NEW_PASSPHRASE="$new" awk '
  /^PRIVATE_KEY_PASSWORD=/ { print "PRIVATE_KEY_PASSWORD=" ENVIRON["NEW_PASSPHRASE"]; next }
  { print }
' "$env_file" > "$tmp"
mv "$tmp" "$env_file"

echo "Passphrase rotated. $key re-encrypted, $env_file updated. The certificate is unchanged."
