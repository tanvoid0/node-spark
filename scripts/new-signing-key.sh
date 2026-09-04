#!/usr/bin/env bash
# Generates the plugin signing keypair with a random passphrase and records the passphrase in .env.
#
# Run once. The Marketplace ties a plugin's signature to this key, so every later release should be
# signed with the same one: back secrets/ up somewhere outside the repository. The script refuses to
# overwrite an existing key for that reason.
#
#   bash scripts/new-signing-key.sh
#
# On Windows, `bash` on PATH is usually C:\WINDOWS\system32\bash.exe - the WSL launcher, which
# fails with "execvpe(/bin/bash) failed" when no distro is installed. Name Git's own bash instead:
#
#   & "C:\Program Files\Git\bin\bash.exe" scripts/new-signing-key.sh
set -euo pipefail

# Git Bash rewrites an argument that looks like a Unix path, which turns the certificate subject
# "/CN=..." into "C:/Program Files/Git/CN=...". Both variables are ignored everywhere else.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

cd "$(dirname "$0")/.."

key="secrets/private.pem"
chain="secrets/chain.crt"
env_file=".env"
subject="/CN=NodeSpark Plugin Signing"
days=3650

command -v openssl >/dev/null || { echo "openssl is not on PATH." >&2; exit 1; }

if [ -e "$key" ] || [ -e "$chain" ]; then
  echo "$key or $chain already exists." >&2
  echo "Signing a release with a new key changes the publisher identity — move the old pair aside" >&2
  echo "deliberately if that is really what you want." >&2
  exit 1
fi

mkdir -p secrets

# 32 random bytes, base64: no interactive prompt, and nothing to remember or mistype.
passphrase="$(openssl rand -base64 32)"

openssl genpkey -aes-256-cbc -algorithm RSA -pkeyopt rsa_keygen_bits:4096 \
  -pass "pass:$passphrase" -out "$key"
openssl req -new -x509 -key "$key" -passin "pass:$passphrase" \
  -days "$days" -subj "$subject" -out "$chain"

chmod 600 "$key" 2>/dev/null || true

[ -f "$env_file" ] || cp .env.example "$env_file"

# Rewrite the passphrase line in place, or append one if the key is not there yet. Written with a
# temporary file in the same directory so a failure cannot leave a half-written .env.
tmp="$(mktemp "${env_file}.XXXXXX")"
if grep -q '^PRIVATE_KEY_PASSWORD=' "$env_file"; then
  # The passphrase is base64, so it can contain '/' and '+': awk, not sed, to avoid escaping rules.
  PASSPHRASE="$passphrase" awk '
    /^PRIVATE_KEY_PASSWORD=/ { print "PRIVATE_KEY_PASSWORD=" ENVIRON["PASSPHRASE"]; next }
    { print }
  ' "$env_file" > "$tmp"
else
  cat "$env_file" > "$tmp"
  echo "PRIVATE_KEY_PASSWORD=$passphrase" >> "$tmp"
fi
mv "$tmp" "$env_file"

echo "Wrote $key and $chain, and the passphrase into $env_file (both gitignored)."
openssl x509 -in "$chain" -noout -subject -enddate
echo
echo "Back secrets/ up outside this repository, then check the release wiring:"
echo "  ./gradlew releaseCheck"
