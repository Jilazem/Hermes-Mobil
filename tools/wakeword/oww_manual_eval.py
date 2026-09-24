import numpy as np, sherpa_onnx
from oww_manual import OWW
ESPEAK='vits-piper-en_US-lessac-medium/espeak-ng-data'
def mk(model_dir, onnx):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=sherpa_onnx.OfflineTtsVitsModelConfig(
        model=f'{model_dir}/{onnx}', tokens=f'{model_dir}/tokens.txt', data_dir=ESPEAK, noise_scale=0.8, noise_scale_w=0.9), num_threads=2)))
T={'en':mk('vits-piper-en_US-lessac-medium','en_US-lessac-medium.onnx'),'tr':mk('tr','tr_TR-fettah-medium.onnx')}
def to16k(x, sr):
    n=int(len(x)*16000/sr); return np.interp(np.linspace(0,len(x)-1,n), np.arange(len(x)), x).astype(np.float32)
def gen(lang,text,speed):
    a=T[lang].generate(text,sid=0,speed=speed); return to16k(np.array(a.samples,dtype=np.float32), a.sample_rate)
o=OWW()
def run(x, th=None):
    o.reset(); pad=np.zeros(16000,dtype=np.float32)
    pcm=(np.clip(np.concatenate([pad,x,pad,pad]),-1,1)*32767).astype(np.int16)
    best=0; hits=0; cool=0
    for i in range(0,len(pcm)-1280,1280):
        s=o.step(pcm[i:i+1280]); best=max(best,s)
        if th is not None:
            if cool>0: cool-=1
            elif s>=th: hits+=1; cool=25
    return best, hits
np.random.seed(0)
pos=[('en','Hey Jarvis.'),('en','Hey Jarvis, turn on the light.'),('tr','Hey Carvis.'),('tr','Hey Jarvis.'),('tr','Hey Carvis, saat kaç?'),('tr','Hey Carvis, müziği aç.')]
neg_tr="Merhaba, bugün hava çok güzel. Yarın sabah toplantımız var ve herkesin gelmesi gerekiyor. Her mesaj önemlidir, lütfen hepsini oku. Arkadaşım Harun dün akşam bize geldi, birlikte yemek yedik. Sarı arabayı garaja park ettim. Ervin ile Mert markete gittiler. Kargo yarın gelecekmiş, adres doğru mu kontrol et. Hermione kitabını bitirdim, çok beğendim. Çarşıda yeni bir kafe açılmış. Hey, bak şuraya, çarşıya gidelim mi? Hey kardeşim, nasılsın? Hayvanlar parka gitti. Harbiye'de buluşalım."
neg_en="Hello there, what is the weather like today? Harvest season is coming soon. Harvey went to the market with his friends. The service was great and everyone was happy. Travis and Jarrod played football in the park. Hey, Jarrett, are you coming? Jar of honey is on the table. Hey Travis, hey Harvey, hey Marvin."
res={}
for l,t in pos:
    res[(l,t)]=[run(gen(l,t,sp))[0] for sp in (0.9,1.0,1.15) for _ in range(3)]
for k,v in res.items(): print(k, ' '.join(f'{s:.2f}' for s in v))
negs=[gen('tr',neg_tr,s) for s in (0.95,1.05,1.15)]+[gen('en',neg_en,s) for s in (1.0,1.1)]
secs=sum(len(x)/16000 for x in negs)
for th in (0.5,0.4,0.3):
    rec=sum(sum(1 for s in v if s>=th) for v in res.values()); tot=sum(len(v) for v in res.values())
    fa=sum(run(x,th)[1] for x in negs); mx=max(run(x)[0] for x in negs)
    print(f'th={th}: recall {rec}/{tot}  false alarms {fa} in {secs:.0f}s (max neg score {mx:.2f})')
def run2(x, th, need):
    o.reset(); pad=np.zeros(16000,dtype=np.float32)
    pcm=(np.clip(np.concatenate([pad,x,pad,pad]),-1,1)*32767).astype(np.int16)
    hits=0; streak=0; cool=0
    for i in range(0,len(pcm)-1280,1280):
        s=o.step(pcm[i:i+1280])
        if cool>0: cool-=1; streak=0; continue
        streak = streak+1 if s>=th else 0
        if streak>=need: hits+=1; cool=25; streak=0
    return hits
print('--- art arda cerceve kurali')
posx={k:[gen(l,t,sp) for sp in (0.9,1.0,1.15) for _ in range(3)] for (l,t) in pos for k in [(l,t)]}
for th,need in [(0.5,1),(0.5,2),(0.4,2),(0.35,2),(0.3,3)]:
    rec=sum(sum(1 for x in v if run2(x,th,need)>0) for v in posx.values()); tot=sum(len(v) for v in posx.values())
    fa=sum(run2(x,th,need) for x in negs)
    print(f'th={th} ardışık={need}: recall {rec}/{tot}  false alarms {fa} in {secs:.0f}s')
