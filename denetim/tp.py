# Denetim sayim betigi: JUnit XML'lerinden test toplamini cikarir.
import glob, xml.etree.ElementTree as ET
t=f=s=e=0
n=0
for p in glob.glob('/Users/gokhanuzman/hermes-workspace/wt-android-uzman/app/build/test-results/testDebugUnitTest/*.xml'):
    n+=1
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
print('xml_dosya',n,'tests',t,'failures',f,'errors',e,'skipped',s)
