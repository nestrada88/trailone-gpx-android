#!/usr/bin/env python3
"""Verify exact prior Android source and the development context lock; no third-party dependencies."""
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'governance/SOURCE_LOCK.json').read_text(encoding='utf-8'))
STATE = json.loads((ROOT / 'APP_STATE.json').read_text(encoding='utf-8'))
errors = []

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

if STATE.get('context_id') != 'TRAILONE-GPX-ANDROID-DEVCTX':
    errors.append('Wrong context ID')
if STATE.get('context_status') != 'WORKING_CONTEXT_NOT_RELEASE':
    errors.append('Unexpected status; requires explicit review')
if STATE.get('source_baselines', {}).get('android_local_source', {}).get('sha256') != LOCK['source_archive']['sha256']:
    errors.append('Source archive metadata disagreement')
if STATE.get('approved_baselines', {}).get('brand_palette', {}).get('sha256') != LOCK['approved_brand_palette']['sha256']:
    errors.append('Brand palette metadata disagreement')
for name, expected in sorted(LOCK['source_tree_sha256'].items()):
    path = ROOT / name
    if not path.is_file():
        errors.append('MISSING: ' + name)
    elif sha(path) != expected:
        errors.append('MODIFIED: ' + name)
for msg in errors:
    print('FAIL:', msg)
if errors:
    print('Context mismatch or changed code. Update lock only after change control and tests.')
    sys.exit(1)
print('PASS: prior Android source integrity and development context metadata')
print('NOTE: no Android APK compile, runtime, signature, brand-server freshness or parity verification performed')
