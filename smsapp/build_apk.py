#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
سكربت البناء السريع - تطبيق الرسائل (محطة كهرباء الشوافي)
الاستخدام:
    python build_apk.py           # رفع التعديلات + بناء + تنزيل APK
    python build_apk.py -skip     # بناء فقط بدون رفع (إعادة تشغيل آخر بناء)
    python build_apk.py -monitor  # مراقبة آخر تشغيل فقط
    python build_apk.py -download # تنزيل أحدث APK من آخر بناء ناجح فقط

ملاحظة: ملفات المشروع تُرفع إلى مجلد smsapp/ داخل نفس المستودع
        (حتى لا تتعارض مع ملفات تطبيق التسديد في الجذر).
شروط الاستخدام:
    - ملف التوكن: ضعه في ملف  gh_token.txt  بجانب السكربت (مستثنى من git)
    - أو ضع التوكن في متغير البيئة  GH_TOKEN
"""
import base64, io, json, os, re, sys, time, urllib.request, urllib.error, zipfile

OWNER = '774204710773014363mmmm-spec'
REPO = 'tasdeed-app'
BASE = 'https://api.github.com/repos/%s/%s' % (OWNER, REPO)
ROOT = os.path.dirname(os.path.abspath(__file__))
APK_OUT = os.path.join(ROOT, 'رسائل_نقي.apk')
WORKFLOW_PATH = '.github/workflows/build-sms.yml'
ARTIFACT_NAME = 'sms-app-debug'
SMS_PREFIX = 'smsapp/'

def get_token():
    t = os.environ.get('GH_TOKEN')
    if t: return t
    f = os.path.join(ROOT, 'gh_token.txt')
    if os.path.exists(f):
        t = io.open(f, encoding='utf-8').read().strip()
        if t: return t
    print('❌ لا يوجد توكن!')
    print('   إما: ضعه في ملف gh_token.txt بجانب السكربت')
    print('   أو:  اضبط متغير البيئة GH_TOKEN')
    sys.exit(1)

TOKEN = get_token()

def api(method, url, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header('Authorization', 'Bearer ' + TOKEN)
    req.add_header('User-Agent', 'opencode')
    req.add_header('Accept', 'application/vnd.github+json')
    if data:
        req.add_header('Content-Type', 'application/json')
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                body = r.read().decode()
                return json.loads(body) if body else {}
        except urllib.error.HTTPError as e:
            return {'__error__': (e.code, e.read().decode()[:300])}
        except Exception as e:
            print('  ⏳ محاولة %d فشلت (%s)...' % (attempt + 1, str(e)[:80]))
            time.sleep(5)
    return {'__error__': ('timeout', 'فشل الاتصال بعد 4 محاولات')}

def local_files():
    """كل الملفات المحلية التي يجب رفعها (بدون استثناءات git)."""
    out = []
    skip_dirs = {'.gradle', 'build', '.idea', '.git', '__pycache__', 'app/build'}
    skip_ext = {'.pyc', '.iml'}
    for r_, ds, fs in os.walk(ROOT):
        ds[:] = [d for d in ds if d not in skip_dirs]
        for f in fs:
            full = os.path.join(r_, f)
            rel = os.path.relpath(full, ROOT).replace(os.sep, '/')
            if f.endswith(tuple(skip_ext)): continue
            if rel == 'gh_token.txt': continue
            if rel == 'رسائل_نقي.apk': continue
            out.append((rel, full))
    return out

def read_gitignore():
    out = []
    p = os.path.join(ROOT, '.gitignore')
    if os.path.exists(p):
        for line in io.open(p, encoding='utf-8'):
            line = line.strip()
            if line and not line.startswith('#'):
                out.append(line)
    return out

def is_ignored(rel, rules):
    for rule in rules:
        if rule.endswith('/'):
            if rel.startswith(rule) or rel.startswith(rule[:-1] + '/'):
                return True
        elif '*' in rule:
            import fnmatch
            if fnmatch.fnmatch(rel, rule): return True
        else:
            if rel == rule or rel.startswith(rule + '/'):
                return True
    return False

def collect_uploads():
    """الملفات + مسار المستودع النهائي (smsapp/ للمشروع، الجذر لملف العمل)."""
    rules = read_gitignore()
    files = []
    for rel, full in local_files():
        if is_ignored(rel, rules):
            print('  (مستثنى)', rel)
            continue
        if rel.startswith('.github/'):
            repo_path = rel
        else:
            repo_path = SMS_PREFIX + rel
        files.append((repo_path, rel, full))
    return files

def get_remote_shas():
    tree = api('GET', BASE + '/git/trees/main?recursive=1')
    if '__error__' in tree:
        print('⚠️ تعذر قراءة شجرة الملفات:', tree['__error__'])
        return {}
    return {e['path']: e['sha'] for e in tree['tree'] if e['type'] == 'blob'}

def main():
    skip_upload = '-skip' in sys.argv
    monitor_only = '-monitor' in sys.argv
    download_only = '-download' in sys.argv

    if not skip_upload and not monitor_only and not download_only:
        # باد الزيادة على versionCode (مشروع التطبيق داخل smsapp/)
        gradle_file = os.path.join(ROOT, 'app', 'build.gradle.kts')
        g = io.open(gradle_file, encoding='utf-8').read()
        m = re.search(r'versionCode\s*=\s*(\d+)', g)
        if m:
            new_vc = int(m.group(1)) + 1
            g = g.replace('versionCode = %d' % int(m.group(1)), 'versionCode = %d' % new_vc)
            io.open(gradle_file, 'w', encoding='utf-8').write(g)
            print('🔢 versionCode →', new_vc)

        remote = get_remote_shas()
        uploads = collect_uploads()
        changed = []
        new_files = []
        for repo_path, rel, full in uploads:
            if repo_path not in remote:
                new_files.append((repo_path, rel, full))
            else:
                blob = api('GET', BASE + '/git/blobs/' + remote[repo_path])
                if '__error__' in blob:
                    print('⚠️', repo_path, blob['__error__']); continue
                import base64 as b64
                remote_bytes = b64.b64decode(blob['content'])
                local_bytes = io.open(full, 'rb').read()
                if remote_bytes != local_bytes:
                    changed.append((repo_path, rel, full))
        print('🔄 تغييرات:', len(changed), '| ملفات جديدة:', len(new_files))
        for repo_path, _, _ in changed + new_files:
            print('   •', repo_path)
        if changed or new_files:
            for repo_path, _, full in changed + new_files:
                raw = io.open(full, 'rb').read()
                r = api('PUT', BASE + '/contents/' + repo_path,
                        {'message': 'update smsapp ' + repo_path,
                         'content': base64.b64encode(raw).decode(),
                         'sha': remote.get(repo_path)})
                if '__error__' in r:
                    print('❌ فشل رفع', repo_path, r['__error__']); sys.exit(1)
            print('✅ تم رفع', len(changed) + len(new_files), 'ملف')
        else:
            print('✅ لا توجد تغييرات - الكل محدث')

    # 2) تشغيل البناء
    if not monitor_only and not download_only:
        r = api('POST', BASE + '/actions/workflows/build-sms.yml/dispatches', {'ref': 'main'})
        if '__error__' in r:
            print('❌ فشل تشغيل البناء:', r['__error__']); sys.exit(1)
        print('🚀 بدأ البناء على GitHub Actions...')

    # 3) مراقبة
    run_id = None
    if not download_only:
        for i in range(80):
            runs = api('GET', BASE + '/actions/runs')
            if '__error__' in runs: time.sleep(10); continue
            runs = [r for r in (runs.get('workflow_runs') or []) if r.get('path') == WORKFLOW_PATH]
            if not runs: time.sleep(10); continue
            r = runs[0]
            if run_id is None:
                run_id = r['id']
            if r['id'] == run_id:
                st = r['status']; concl = r['conclusion']
                print('  [%02d] %s %s' % (i, st, concl or ''))
                if st == 'completed':
                    if concl != 'success':
                        print('❌ البناء فشل! راجع GitHub Actions')
                        sys.exit(1)
                    break
            time.sleep(20)
        else:
            print('⏰ انتهى وقت الانتظار'); sys.exit(1)

    # 4) تنزيل الـ APK
    if download_only:
        run_id = None
        for r in api('GET', BASE + '/actions/runs').get('workflow_runs') or []:
            if r.get('path') != WORKFLOW_PATH: continue
            if r.get('status') == 'completed' and r.get('conclusion') == 'success':
                run_id = r['id']
                break
        if not run_id:
            print('❌ لا يوجد تشغيل ناجح سابق'); sys.exit(1)
        print('📦 استخدام آخر بناء ناجح: #%d' % run_id)
    arts = api('GET', BASE + '/actions/runs/%d/artifacts' % run_id)
    if '__error__' in arts or not arts.get('artifacts'):
        print('❌ لا يوجد Artifact'); sys.exit(1)
    art = None
    for a in arts['artifacts']:
        if a.get('name') == ARTIFACT_NAME:
            art = a
            break
    if art is None:
        art = arts['artifacts'][0]
    dl = art['archive_download_url']

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *a, **k): return None
    op = urllib.request.build_opener(NoRedirect)
    req = urllib.request.Request(dl, headers={'Authorization': 'Bearer ' + TOKEN, 'User-Agent': 'opencode'})
    try:
        resp = op.open(req, timeout=30)
        loc = resp.headers.get('Location')
    except urllib.error.HTTPError as e:
        loc = e.headers.get('Location')
    if not loc:
        print('❌ تعذر الحصول على رابط التنزيل'); sys.exit(1)
    req2 = urllib.request.Request(loc, headers={'User-Agent': 'opencode'})
    data = urllib.request.urlopen(req2, timeout=600).read()

    tmpzip = os.path.join(ROOT, '_apk_tmp.zip')
    io.open(tmpzip, 'wb').write(data)
    z = zipfile.ZipFile(tmpzip)
    names = z.namelist()
    apk_entry = names[0]
    apk_data = z.read(apk_entry)
    z.close()
    io.open(APK_OUT, 'wb').write(apk_data)
    os.remove(tmpzip)
    print('✅ تم البناء والتنزيل:')
    print('   📱', APK_OUT)
    print('   💾 الحجم:', len(apk_data), 'بايت')

if __name__ == '__main__':
    main()