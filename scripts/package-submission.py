#!/usr/bin/env python3
"""Package the reviewed source and latest debug APK; no local settings or caches."""
from pathlib import Path
from datetime import datetime
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parent.parent
apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
if not apk.is_file():
    raise SystemExit('Build :app:assembleDebug before packaging.')
inputs = list((root / 'app/src').rglob('*')) + [root / 'app/build.gradle', root / 'build.gradle']
if any(p.is_file() and p.stat().st_mtime > apk.stat().st_mtime for p in inputs):
    raise SystemExit('Source is newer than APK. Rebuild before packaging.')

stamp = datetime.now().strftime('%Y%m%d-%H%M%S')
out = root / 'releases' / ('submission-' + stamp)
out.mkdir(parents=True, exist_ok=False)
archive = out / 'teach-again-source.zip'
top = ['README.md', '.gitignore', 'build.gradle', 'settings.gradle', 'gradle.properties',
       'gradlew', 'gradlew.bat', 'app/build.gradle']
files = [root / name for name in top]
for name in ['app/src', 'gradle/wrapper', '.kiro/steering', '.kiro/specs', '.kiro/hooks', 'scripts', 'docs']:
    files.extend(p for p in (root / name).rglob('*') if p.is_file() and not p.is_symlink())
files = sorted(set(files))
for p in files:
    if not p.is_file():
        raise SystemExit('Required source missing: ' + str(p.relative_to(root)))
    if p.name == '.DS_Store' or '__pycache__' in p.parts:
        continue
    if p.suffix in {'.keystore', '.jks'} or p.name in {'local.properties', '.env'}:
        raise SystemExit('Refusing local configuration in source package.')
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in files:
        if p.name != '.DS_Store' and '__pycache__' not in p.parts:
            z.write(p, 'teach-again/' + str(p.relative_to(root)))
    if z.testzip() is not None:
        raise SystemExit('Archive verification failed.')
shutil.copy2(apk, out / 'teach-again-demo.apk')
shutil.copy2(root / 'README.md', out / 'README.md')
shutil.copy2(root / 'docs/DEMO.md', out / 'DEMO.md')
lines = []
for p in sorted(out.iterdir()):
    lines.append(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name)
(out / 'SHA256SUMS.txt').write_text('\n'.join(lines) + '\n')
print('Submission files:', out)
print('Source files:', len(files))
print('APK bytes:', apk.stat().st_size)
