import sys,zipfile,hashlib,pathlib
p=pathlib.Path(sys.argv[1])
if not p.is_file(): raise SystemExit(f'APK missing: {p}')
with zipfile.ZipFile(p) as z:
    names=z.namelist()
    if not any(n.startswith('classes') and n.endswith('.dex') for n in names): raise SystemExit('No DEX in APK')
    if 'AndroidManifest.xml' not in names: raise SystemExit('Missing binary manifest')
    if z.testzip() is not None: raise SystemExit('Corrupt APK ZIP')
if p.stat().st_size < 100_000: raise SystemExit('APK suspiciously small')
print('PASS: real APK ZIP + DEX + Manifest')
print('SHA-256:',hashlib.sha256(p.read_bytes()).hexdigest())
