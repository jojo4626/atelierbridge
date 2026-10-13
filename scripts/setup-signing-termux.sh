#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
for cmd in gh keytool openssl base64 sha256sum; do
  command -v "$cmd" >/dev/null || { echo "Commande absente : $cmd"; exit 1; }
done
[[ -f app/build.gradle && -d .git ]] || { echo 'Exécute ce script depuis le dépôt AtelierBridge'; exit 1; }
gh auth status >/dev/null || { echo 'Connecte ton compte avec gh auth login'; exit 1; }
repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
[[ "$repo" == 'jojo4626/atelierbridge' ]] || { echo "Dépôt inattendu : $repo"; exit 1; }
key_dir="$HOME/.artcraftmobile-signing"
key_file="$key_dir/artcraft-release.p12"
pass_file="$key_dir/password"
mkdir -p "$key_dir"
chmod 700 "$key_dir"
if [[ -f "$key_file" && ! -f "$pass_file" || ! -f "$key_file" && -f "$pass_file" ]]; then
  echo 'Clé ou mot de passe manquant : signature incomplète' >&2
  exit 1
fi
if [[ ! -f "$key_file" ]]; then
  umask 077
  openssl rand -hex 24 > "$pass_file"
  keytool -genkeypair -noprompt -keystore "$key_file" -storetype PKCS12 \
    -keyalg RSA -keysize 3072 -validity 10000 -alias 'artcraft-mobile' \
    -storepass "$(cat "$pass_file")" -keypass "$(cat "$pass_file")" \
    -dname 'CN=AtelierBridge, O=Independent developer'
fi
chmod 600 "$key_file" "$pass_file"
password="$(cat "$pass_file")"
alias='artcraft-mobile'
cert="$(keytool -exportcert -keystore "$key_file" -alias "$alias" -storepass "$password" 2>/dev/null | sha256sum | awk '{print toupper($1)}')"
[[ ${#cert} -eq 64 ]] || { echo 'Certificat illisible' >&2; exit 1; }
existing="$(gh variable get ATELIERBRIDGE_CERT_SHA256 2>/dev/null || true)"
if [[ -n "$existing" && "$existing" != "$cert" ]]; then
  echo 'Certificat différent de celui déjà publié : arrêt pour protéger les mises à jour.' >&2
  exit 1
fi
base64 < "$key_file" | tr -d "\n" | gh secret set ATELIERBRIDGE_KEYSTORE_B64
printf '%s' "$password" | gh secret set ATELIERBRIDGE_STORE_PASSWORD
printf '%s' "$password" | gh secret set ATELIERBRIDGE_KEY_PASSWORD
printf '%s' "$alias" | gh secret set ATELIERBRIDGE_KEY_ALIAS
gh variable set ATELIERBRIDGE_CERT_SHA256 --body "$cert"
unset password
printf '\nSignature configurée et certificat verrouillé pour %s.\n' "$repo"
printf 'Sauvegarde ces deux fichiers hors de la tablette :\n%s\n%s\n' "$key_file" "$pass_file"
