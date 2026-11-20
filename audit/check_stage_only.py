"""Static host/build checks; these do not certify live Core behavior."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
build=(root/'app/build.gradle.kts').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
source='\n'.join(p.read_text() for p in (root/'app/src/main/java').rglob('*.kt'))
assert 'android.permission.INTERNET' in manifest
assert 'usesCleartextTraffic="false"' in manifest
assert '.orElse("stage")' in build
assert 'namyranEnvironment' in build
assert 'ir.namyranservice.customer.stage' in build
assert 'BACKEND_ENABLED", "true"' in build
# The support action hands this fixed URL to Android ACTION_VIEW.
# Core API requests still use the Stage host exclusively.
source_hosts = source.replace('android.net.Uri.parse("https://wa.me/989375846363")', '')
for host in re.findall(r'https?://([^/\s"\\]+)',build+source_hosts):
    assert host in {'$targetHost', 'stage.namyranservice.ir'}, f'Unexpected runtime URL: {host}'
assert 'instanceFollowRedirects = false' in source
assert 'AndroidKeyStore' in source
assert 'WebView' not in source
assert not re.search(r'TrustAll|ALLOW_ALL_HOSTNAME_VERIFIER|setHostnameVerifier',source)
auth=(root/'app/src/main/java/ir/namyranservice/customer/AuthScreens.kt').read_text()
assert not any(marker in auth for marker in ('demoChallenge', 'onDemo', '09022896363', '"1111"')), 'Synthetic login bypass must not ship'
assert 'vm.verifyOtp(code)' in auth
print('PASS: Stage identity, HTTPS only, redirect rejection, native UI and encrypted session storage')

assert not re.search(r'clone[0-9]*[.]namyranservice[.]ir|HostAccess|DirectoryPrivacy', build+source, re.I), 'Clone-only connection code must not ship'
assert 'NativeRules.allowedBase(BuildConfig.API_BASE_URL, BuildConfig.ENVIRONMENT)' in source
assert 'parsed.host==BuildConfig.API_HOST' in source
assert 'if (productionTarget) "ir.namyranservice.customer" else "ir.namyranservice.customer.stage"' in build
print('PASS: explicit environment selection, separate app identities, no clone access or host override')
