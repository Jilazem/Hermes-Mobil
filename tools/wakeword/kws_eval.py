import sherpa_onnx, numpy as np, sys, itertools, random
M='sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01'
ESPEAK='vits-piper-en_US-lessac-medium/espeak-ng-data'
def mk(model_dir, onnx):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=sherpa_onnx.OfflineTtsVitsModelConfig(
        model=f'{model_dir}/{onnx}', tokens=f'{model_dir}/tokens.txt', data_dir=ESPEAK, noise_scale=0.8, noise_scale_w=0.9), num_threads=2)))
T={'en':mk('vits-piper-en_US-lessac-medium','en_US-lessac-medium.onnx'),'tr':mk('tr','tr_TR-fettah-medium.onnx')}
def gen(lang,text,speed):
    a=T[lang].generate(text,sid=0,speed=speed); return np.array(a.samples,dtype=np.float32), a.sample_rate
def spotter(kwfile, th, score):
    return sherpa_onnx.KeywordSpotter(tokens=f'{M}/tokens.txt',
        encoder=f'{M}/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx',
        decoder=f'{M}/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx',
        joiner=f'{M}/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx',
        keywords_file=kwfile, num_threads=1, keywords_score=score, keywords_threshold=th, max_active_paths=4)
def detect(kws, samples, sr, noise=0.0):
    s=kws.create_stream(); pad=np.zeros(int(sr*0.8),dtype=np.float32)
    x=np.concatenate([pad,samples,pad,pad])
    if noise>0: x=x+np.random.RandomState(1).normal(0,noise,len(x)).astype(np.float32)
    s.accept_waveform(sr, x); s.input_finished(); hits=[]
    while kws.is_ready(s):
        kws.decode_stream(s); r=kws.get_result(s)
        if r: hits.append(r); kws.reset_stream(s)
    return hits
pos=[('en','Hey Jarvis.'),('en','Jarvis, turn on the light.'),('tr','Hey Carvis.'),('tr','Hey Jarvis.'),('tr','Carvis, saat kaç?'),('tr','Hey Hermes.'),('tr','Hermes, yarın hava nasıl?')]
neg_text_tr="Merhaba, bugün hava çok güzel. Yarın sabah toplantımız var ve herkesin gelmesi gerekiyor. Her mesaj önemlidir, lütfen hepsini oku. Arkadaşım Harun dün akşam bize geldi, birlikte yemek yedik. Sarı arabayı garaja park ettim. Ervin ile Mert markete gittiler. Kargo yarın gelecekmiş, adres doğru mu kontrol et. Hermione kitabını bitirdim, çok beğendim. Çarşıda yeni bir kafe açılmış."
neg_text_en="Hello there, what is the weather like today? Harvest season is coming soon. Harvey went to the market with his friends. The service was great and everyone was happy. Travis and Jarrod played football in the park. Her message was clear. Jar of honey is on the table."
samples=[]
for lang,t in pos:
    for sp in (0.9,1.0,1.15):
        for rep in range(2): samples.append((True,lang,t,sp,gen(lang,t,sp)))
negs=[gen('tr',neg_text_tr,1.0),gen('tr',neg_text_tr,1.1),gen('en',neg_text_en,1.0)]
neg_seconds=sum(len(a)/sr for a,sr in negs)
kwsets={
 'basic':"▁HE Y ▁JA R VI S @JARVIS\n▁JA R VI S @JARVIS\n▁HE Y ▁HER ME S @HERMES\n▁HER ME S @HERMES\n",
 'variants':"▁HE Y ▁JA R VI S @JARVIS\n▁JA R VI S @JARVIS\n▁HE Y ▁C AR VI S @JARVIS\n▁C AR VI S @JARVIS\n▁CHA R VI S @JARVIS\n▁HE Y ▁HER ME S @HERMES\n▁HER ME S @HERMES\n▁HER M IS @HERMES\n",
}
for name,content in kwsets.items():
    open(f'kw_{name}.txt','w').write(content)
    for th,score in [(0.25,1.0),(0.18,1.5),(0.12,2.0),(0.08,2.5)]:
        kws=spotter(f'kw_{name}.txt',th,score)
        tp=sum(1 for w,l,t,sp,(a,sr) in samples if detect(kws,a,sr))
        tpn=sum(1 for w,l,t,sp,(a,sr) in samples if detect(kws,a,sr,noise=0.01))
        fa=sum(len(detect(kws,a,sr)) for a,sr in negs)
        by={}
        for w,l,t,sp,(a,sr) in samples:
            by.setdefault((l,t),[0,0]); by[(l,t)][1]+=1; by[(l,t)][0]+= bool(detect(kws,a,sr))
        print(f'{name:9s} th={th:.2f} sc={score:.1f}  recall {tp}/{len(samples)}  +noise {tpn}/{len(samples)}  false-alarms {fa} in {neg_seconds:.0f}s  ' + ' '.join(f'{t[:10]}:{v[0]}/{v[1]}' for (l,t),v in by.items()))
