"""GET-only live contract probe. Never sends OTP, auth tokens, or request POSTs."""
import datetime
import json
import pathlib
import re
import urllib.error
import urllib.request

BASE = 'https://stage.namyranservice.ir'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None

opener = urllib.request.build_opener(NoRedirect())

def get(path):
    try:
        with opener.open(BASE + path, timeout=30) as r:
            return r.status, r.headers, r.read(2_000_001)
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read(2_000_001)

status, headers, raw = get('/wp-json/namyran-customer/v1/config')
assert status == 200, f'config HTTP {status}'
assert len(raw) <= 2_000_000
config = json.loads(raw)
assert config['customerMobile']['schema'] == 1
assert config['services'] and config['categories']
assert 'no-store' in headers.get('Cache-Control', '')
status, _, raw = get('/service-request/')
assert status == 200, f'Core form HTTP {status}'
match = re.search(r'var NSC_DATA = (.*?);\s*\n', raw.decode())
assert match, 'Core form localization missing'
form = json.loads(match.group(1))
keys = ['services', 'categories', 'brands', 'installBrands', 'tvInstall', 'visitSlots', 'dispatchLeadHours']
parity = {key: form[key] == config[key] for key in keys}
assert all(parity.values()), parity
today = datetime.datetime.fromisoformat(config['serverNow']).date()
schedule = []
for label, date, expected in [('tomorrow', str(today + datetime.timedelta(days=1)), 200), ('past', str(today - datetime.timedelta(days=1)), 400), ('malformed', 'not-a-date', 400)]:
    status, _, raw = get('/wp-json/namyran-customer/v1/schedule?date=' + date)
    body = json.loads(raw)
    assert status == expected, (label, status, body.get('code'))
    if status == 200:
        assert body['kind'] == 'customer_preference'
        assert [r['value'] for r in body['slots']] == [r['value'] for r in config['visitSlots']]
    schedule.append({'case': label, 'status': status, 'code': body.get('code'), 'kind': body.get('kind')})
evidence = {'verified_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'site': BASE, 'core_version': config['customerMobile']['coreVersion'], 'revision': config['revision'], 'services': len(config['services']), 'groups': len(config['categories']), 'devices': sum(len(v) for v in config['categories'].values()), 'cities': len(config['customerMobile']['cities']), 'form_parity': parity, 'schedule_checks': schedule, 'writes_performed': False, 'otp_or_submission_tested': False}
path = pathlib.Path('docs/evidence/stage-readonly.json')
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(evidence, ensure_ascii=False, indent=2))
