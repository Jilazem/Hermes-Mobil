# Tur-9 denetim probe: JUnit XML sayimi + APK/asset dogrulamasi
import glob, zipfile
import xml.etree.ElementTree as ET

BASE = '/Users/gokhanuzman/hermes-workspace/wt-android-uzman'
tot = f = e = s = suites = 0
for p in glob.glob(BASE + '/app/build/test-results/testDebugUnitTest/*.xml'):
    r = ET.parse(p).getroot()
    suites += 1
    tot += int(r.get('tests')); f += int(r.get('failures'))
    e += int(r.get('errors')); s += int(r.get('skipped'))
print('SUITES=%d TESTS=%d FAIL=%d ERR=%d SKIP=%d' % (suites, tot, f, e, s))

for p in glob.glob(BASE + '/app/build/test-results/testDebugUnitTest/*ArenaSceneTest*'):
    r = ET.parse(p).getroot()
    print('ArenaSceneTest tests=%s fail=%s err=%s skip=%s' % (
        r.get('tests'), r.get('failures'), r.get('errors'), r.get('skipped')))

z = zipfile.ZipFile(BASE + '/app/build/outputs/apk/debug/app-debug.apk')
for n in z.namelist():
    if 'assets/arena' in n:
        print(n, z.getinfo(n).file_size)
