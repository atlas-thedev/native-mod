#!/usr/bin/env python3
"""Writes the manifest.json the Noctra launcher reads to find the mod jar.

usage: make-manifest.py <jar> <version> <repo> <out>
"""
import hashlib
import json
import sys

jar, version, repo, out = sys.argv[1:5]
data = open(jar, "rb").read()
name = jar.replace("\\", "/").split("/")[-1]
manifest = {
    "schema": 1,
    "version": version,
    "file": name,
    "url": f"https://github.com/{repo}/releases/download/v{version}/{name}",
    "sha256": hashlib.sha256(data).hexdigest(),
    "size": len(data),
    # Fabric / Quilt only. The launcher falls back to CustomSkinLoader below `minecraft.min`.
    "loaders": ["fabric", "quilt"],
    "minecraft": {"min": "1.16", "tested": "26.3"},
}
with open(out, "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, indent=2)
    handle.write("\n")
print(json.dumps(manifest, indent=2))
