#!/usr/bin/env python3
"""Integrate the exact owner-approved GPX launcher icon from an authenticated local brand checkout.

Run on the Android feature branch:
  python3 tools/integrate-approved-app-icon.py /path/to/trailone-brand-context
No artwork is generated or modified. Requires the SHA-256-pinned approved master PNG.
"""
import hashlib
import json
import shutil
import sys
from pathlib import Path

BRAND_REL = Path("brand/visual-identity/iconography/applications/trailone-gpx/TRAILONE_GPX_APP_ICON_APPROVED_v1.0.0.png")
APP_REL = Path("app/src/main/res/mipmap-nodpi/ic_launcher.png")
MANIFEST_REL = Path("app/src/main/AndroidManifest.xml")
LOCK_REL = Path("governance/SOURCE_LOCK.json")
STATE_REL = Path("APP_STATE.json")
APPROVED_SHA256 = "be19ff29cc63cf4e3615fb9613668334f086cb9a2d879a4bc004eaeefad7be38"
APPROVED_SIZE = 1314647
ROOT = Path(__file__).resolve().parents[1]

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def main():
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 tools/integrate-approved-app-icon.py PATH_TO_BRAND_CHECKOUT")
    source = Path(sys.argv[1]).expanduser().resolve() / BRAND_REL
    if not source.is_file():
        sys.exit(f"Missing approved source: {source}")
    payload = source.read_bytes()
    if len(payload) != APPROVED_SIZE or sha256(payload) != APPROVED_SHA256:
        sys.exit("Approved PNG integrity check FAILED; nothing changed")
    manifest_path = ROOT / MANIFEST_REL
    before = manifest_path.read_text(encoding="utf-8")
    attr = 'android:icon="@mipmap/ic_launcher"'
    if attr in before:
        after = before
    else:
        needle = 'android:label="TrailOne GPX"'
        if before.count(needle) != 1:
            sys.exit("Manifest structure differs from approved baseline; nothing changed")
        after = before.replace(needle, needle + "\n        " + attr, 1)
    lock_path = ROOT / LOCK_REL
    state_path = ROOT / STATE_REL
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    state = json.loads(state_path.read_text(encoding="utf-8"))
    if "source_tree_sha256" not in lock or "approved_baselines" not in state:
        sys.exit("Required governance objects unavailable; nothing changed")
    destination = ROOT / APP_REL
    if destination.exists() and destination.read_bytes() != payload:
        sys.exit("Existing launcher image differs; nothing changed")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    manifest_path.write_text(after, encoding="utf-8")
    lock["source_tree_sha256"][str(APP_REL)] = sha256(payload)
    lock["source_tree_sha256"][str(MANIFEST_REL)] = sha256(after.encode("utf-8"))
    lock["approved_brand_app_icon"] = {
        "identifier": "TRAILONE-GPX-APP-ICON-001",
        "version": "1.0.0",
        "source_repository": "nestrada88/trailone-brand-context",
        "source_path": str(BRAND_REL),
        "asset_sha256": APPROVED_SHA256,
        "android_resource_path": str(APP_REL)
    }
    state["approved_baselines"]["gpx_application_icon"] = {
        "identifier": "TRAILONE-GPX-APP-ICON-001",
        "version": "1.0.0",
        "status": "APPROVED_ARTWORK_IMPLEMENTATION_UNVERIFIED",
        "source_repository": "nestrada88/trailone-brand-context",
        "source_path": str(BRAND_REL),
        "sha256": APPROVED_SHA256,
        "android_resource_path": str(APP_REL)
    }
    lock_path.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    state_path.write_text(json.dumps(state, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Added exact icon: {APP_REL} (SHA-256 {APPROVED_SHA256})")
    print("Updated AndroidManifest.xml, SOURCE_LOCK.json and APP_STATE.json")
    print("NEXT: python3 tools/verify-app-context.py")
    print("NEXT: bash tools/test-core.sh && bash tools/test-guided.sh")
    print("NEXT: bash docker/build.sh; then device visual/signing validation")
    print("Do not merge or claim Android validation before these gates pass.")

if __name__ == "__main__":
    main()
