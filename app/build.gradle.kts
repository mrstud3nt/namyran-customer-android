from pathlib import Path
root=Path(__file__).resolve().parents[1]
w=(root/'.github/workflows/customer-stage-debug.yml').read_text()
b=(root/'app/build.gradle.kts').read_text()
g=(root/'build.gradle.kts').read_text()
assert 'workflow_dispatch:' in w
assert "gradle-version: '9.6.0'" in w
assert "sdkmanager 'platforms;android-36'" in w
assert 'id("com.android.application") version "9.4.0"' in g
assert 'id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"' in g
assert 'compileSdk = 36' in b
assert 'applicationId = "ir.namyranservice.customer.stage"' in b
assert 'BACKEND_ENABLED", "false"' in b
assert 'https://namyranservice.ir/' not in b
print('PASS: manual Stage-only workflow, compatible toolchain pinning, backend disabled')
