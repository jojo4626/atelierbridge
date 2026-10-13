#!/usr/bin/env python3
"""Fetch pinned upstream WebAssembly releases for the Google Play bundle."""
import hashlib
import json
import os
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/play"
MAX_BYTES = 160 * 1024 * 1024
MODULES = {"photocraft": "v0.8.2", "vectorcraft": "v0.10.0"}


def request(url):
    req = urllib.request.Request(url, headers={"User-Agent": "AtelierBridge-Play-Build/1.0", "Accept": "application/vnd.github+json"})
    return urllib.request.urlopen(req, timeout=45)


def fetch(slug, tag):
    with request(f"https://api.github.com/repos/storytold/{slug}/releases/tags/{tag}") as res:
        release = json.load(res)
    options = [a for a in release.get("assets", []) if a.get("name", "").lower().startswith(f"{slug}-web-") and a.get("name", "").lower().endswith(".zip")]
    if len(options) != 1:
        raise RuntimeError(f"{slug}: expected one matching web ZIP for {tag}; found {len(options)}")
    asset = options[0]
    src = asset.get("browser_download_url", "")
    allowed = f"https://github.com/storytold/{slug}/releases/download/{tag}/"
    if not src.startswith(allowed) or asset.get("size", 0) <= 0 or asset["size"] > MAX_BYTES:
        raise RuntimeError(f"{slug}: invalid origin or size")
    declared = asset.get("digest", "")
    if not declared.startswith("sha256:") or len(declared) != 71:
        raise RuntimeError(f"{slug}: GitHub API does not provide verified sha256 digest; pin manually before Play build")
    OUT.mkdir(parents=True, exist_ok=True)
    dest = OUT / (slug + ".zip")
    temp = dest.with_suffix(".part")
    h = hashlib.sha256()
    count = 0
    try:
        with request(src) as res, open(temp, "wb") as w:
            while True:
                chunk = res.read(1024 * 1024)
                if not chunk: break
                count += len(chunk)
                if count > MAX_BYTES: raise RuntimeError("archive too large")
                h.update(chunk)
                w.write(chunk)
        if count != asset["size"] or h.hexdigest().lower() != declared[7:].lower():
            raise RuntimeError(f"{slug}: checksum or size mismatch")
        with zipfile.ZipFile(temp) as z:
            names = z.namelist()
            if not any(x.endswith("index.html") for x in names): raise RuntimeError("No index.html")
            if len(names) > 6000: raise RuntimeError("Too many files")
        temp.replace(dest)
        print(f"Verified {slug} {tag} ({count} bytes), sha256 {h.hexdigest()}")
    finally:
        if temp.exists(): temp.unlink()


def fetch_notices():
    chunks = ["AtelierBridge Play — attributions et licences des logiciels tiers\n",
              "Application indépendante. Le code source et les éléments de marque restent la propriété de leurs auteurs.\n"]
    for slug, tag in MODULES.items():
        chunks.append("\n" + "=" * 60 + f"\n{slug} ({tag})\n" + "=" * 60 + "\n")
        found = 0
        for name in ("NOTICE", "ATTRIBUTION.md", "ASSETS.md", "LICENSE-MIT", "LICENSE-APACHE", "LICENSE", "LICENSE.md"):
            try:
                with request(f"https://raw.githubusercontent.com/storytold/{slug}/{tag}/{name}") as src:
                    raw = src.read(400_000)
            except Exception:
                continue
            text = raw.decode("utf-8", errors="replace")
            if text.strip():
                chunks.append(f"\n--- {name} ---\n{text}\n")
                found += 1
        if found < 2:
            raise RuntimeError(f"{slug}: insufficient upstream notices for public redistribution")
    (OUT / "THIRD_PARTY_NOTICES.txt").write_text("".join(chunks), encoding="utf-8")
    print("Embedded upstream license and attribution documents")

if __name__ == "__main__":
    for k, v in MODULES.items(): fetch(k, v)
    fetch_notices()
