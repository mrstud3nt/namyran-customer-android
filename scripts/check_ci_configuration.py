from pathlib import Path
root=Path(__file__).resolve().parents[1]
w=(root/'.github/workflows/android-stage.yml').read_text()
b=(root/'app/build.gradle.kts').read_text()
g=(root/'build.gradle.kts').read_text()
assert 'workflow_dispatch:' in w
assert 'pull_request:' in w
assert ':app:testDebugUnitTest :app:lintDebug' in w
assert 'if: always()' in w
assert 'testImplementation("junit:junit:4.13.2")' in b
assert "gradle-version: '9.6.0'" in w
assert "sdkmanager 'platforms;android-36'" in w
assert 'id("com.android.application") version "9.4.0"' in g
assert 'id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"' in g
assert 'compileSdk = 36' in b
assert 'applicationId = "ir.namyranservice.customer.stage"' in b
assert 'BACKEND_ENABLED", "false"' in b
assert 'https://namyranservice.ir/' not in b
print('PASS: Stage workflow includes unit tests, lint and reports; backend remains disabled')
