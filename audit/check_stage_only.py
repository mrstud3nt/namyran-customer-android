"""Read-only local checks; these are NOT Android compile or endpoint tests."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
build = (root / "app/build.gradle.kts").read_text()
source = "\n".join(f.read_text() for f in (root / "app/src/main/java").rglob("*.kt"))
assert 'android.permission.INTERNET' not in manifest, "INTERNET permission would allow network requests"
assert 'https://stage.namyranservice.ir/wp-json/' in build, "Stage API URL missing"
assert 'BACKEND_ENABLED", "false"' in build, "Backend flag must be false"
assert 'ir.namyranservice.customer.stage' in build, "Wrong application ID"
assert 'https://namyranservice.ir/' not in build + source, "Production URL found in runtime source"
assert 'stage.namyranservice.ir' not in source, "UI should not access backend directly"
assert 'RequestRules.check' in source, "Offline validation missing"
assert 'در انتظار اتصال Stage' in source, "Unverified slot selection should be disabled"
print("PASS: stage-only, no permission to network, offline wizard validation")
