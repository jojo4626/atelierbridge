#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

for cmd in gh openssl base64 sha256sum; do
  command -v "$cmd" >/dev/null || { printf 'Commande absente : %s\n' "$cmd" >&2; exit 1; }
done

[[ -f app/build.gradle && -d .git ]] || { echo 'Exécute ce script depuis le dépôt AtelierBridge' >&2; exit 1; }
gh auth status >/dev/null || { echo 'Connecte ton compte avec gh auth login' >&2; exit 1; }

repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
[[ "$repo" == 'jojo4626/atelierbridge' ]] || { printf 'Dépôt inattendu : %s\n' "$repo" >&2; exit 1; }

key_dir="$HOME/.artcraftmobile-signing"
key_file="$key_dir/artcraft-release.p12"
pass_file="$key_dir/password"
alias='artcraft-mobile'

mkdir -p "$key_dir"
chmod 700 "$key_dir"

if [[ -f "$key_file" && ! -f "$pass_file" || ! -f "$key_file" && -f "$pass_file" ]]; then
  echo 'Clé ou mot de passe manquant : signature incomplète' >&2
  exit 1
fi

if [[ ! -f "$key_file" ]]; then
  umask 077
  openssl rand -hex 24 > "$pass_file"
  if command -v keytool >/dev/null 2>&1; then
    keytool -genkeypair -noprompt -keystore "$key_file" -storetype PKCS12 \
      -keyalg RSA -keysize 3072 -validity 10000 -alias "$alias" \
      -storepass "$(cat "$pass_file")" -keypass "$(cat "$pass_file")" \
      -dname 'CN=AtelierBridge, O=Independent developer'
  else
    temp_key="$(mktemp "$key_dir/private.XXXXXXXX")"
    temp_cert="$(mktemp "$key_dir/cert.XXXXXXXX")"
    trap 'rm -f "$temp_key" "$temp_cert"' EXIT
    openssl req -new -x509 -newkey rsa:3072 -sha256 -nodes -days 10000 \
      -subj '/CN=AtelierBridge/O=Independent developer' \
      -keyout "$temp_key" -out "$temp_cert" >/dev/null 2>&1
    openssl pkcs12 -export -inkey "$temp_key" -in "$temp_cert" \
      -name "$alias" -out "$key_file" -passout "file:$pass_file"
    rm -f "$temp_key" "$temp_cert"
    trap - EXIT
  fi
fi

chmod 600 "$key_file" "$pass_file"

cert="$(openssl pkcs12 -in "$key_file" -clcerts -nokeys -passin "file:$pass_file" 2>/dev/null \
  | openssl x509 -outform DER 2>/dev/null \
  | sha256sum | awk '{print toupper($1)}')"
[[ "$cert" =~ ^[A-F0-9]{64}$ ]] || { echo 'Certificat illisible' >&2; exit 1; }

existing="$(gh variable get ATELIERBRIDGE_CERT_SHA256 2>/dev/null || true)"
if [[ -n "$existing" && "$existing" != "$cert" ]]; then
  echo 'Certificat différent de celui déjà enregistré : arrêt pour protéger les mises à jour.' >&2
  exit 1
fi

base64 < "$key_file" | tr -d '\n' | gh secret set ATELIERBRIDGE_KEYSTORE_B64
gh secret set ATELIERBRIDGE_STORE_PASSWORD < "$pass_file"
gh secret set ATELIERBRIDGE_KEY_PASSWORD < "$pass_file"
printf '%s' "$alias" | gh secret set ATELIERBRIDGE_KEY_ALIAS
gh variable set ATELIERBRIDGE_CERT_SHA256 --body "$cert"

printf '\nSignature configurée pour %s.\n' "$repo"
printf 'À sauvegarder de façon privée, en dehors du dépôt :\n%s\n%s\n' "$key_file" "$pass_file"
