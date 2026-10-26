#!/usr/bin/env python3
"""Verify the two packaged releases against release metadata and SHA256SUMS."""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    report = json.loads((ROOT / 'verification/release.json').read_text())
    expected = {}
    for line in (ROOT / 'packages/SHA256SUMS').read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        expected[name.strip()] = digest
    failures = []
    for item in report['packages']:
        name = item['file']
        file = ROOT / 'packages' / name
        if Path(name).name != name or not file.is_file():
            failures.append(name + ': 文件不存在或名称无效')
            continue
        actual = hashlib.sha256(file.read_bytes()).hexdigest()
        if actual != item['sha256'] or actual != expected.get(name) or file.stat().st_size != item['bytes']:
            failures.append(name + ': 校验不一致')
        else:
            print('OK', name, item['version'], actual)
    if len(expected) != 2 or len(report['packages']) != 2:
        failures.append('最终包数量不应偏离两个')
    for error in failures:
        print(error, file=sys.stderr)
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(main())
