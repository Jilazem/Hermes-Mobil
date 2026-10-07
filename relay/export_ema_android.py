"""Export EMA 1.0.1 to portable ONNX stages; never touches deployed weights.
Run with EMA's Python environment plus onnx/onnxruntime.
"""
import argparse, hashlib, json, os, pathlib, time
import numpy as np
import torch
import onnx
import onnxruntime as ort
from ema_lightning.model import load_acoustic
from ema_lightning.decoder import load_decoder
from ema_lightning.engine import Engine
from ema_lightning.frontend import Frontend

class Text(torch.nn.Module):
    def __init__(self, model): super().__init__(); self.model=model
    def forward(self, ids, mask): return self.model.text_stage(ids, mask)
class Sound(torch.nn.Module):
    def __init__(self, model): super().__init__(); self.model=model
    def forward(self,h,dur,mask,cw,wstart,fw,fp,fmask,noise):
        return self.model.sound_stage(h,dur,mask,cw,wstart,fw,fp,fmask,noise)
class Decode(torch.nn.Module):
    def __init__(self, decoder): super().__init__(); self.decoder=decoder
    def forward(self,z): return self.decoder(z.transpose(1,2))

def main():
    p=argparse.ArgumentParser(); p.add_argument('--weights',required=True); p.add_argument('--output',required=True); a=p.parse_args()
    torch.set_num_threads(2)
    torch.onnx.register_custom_op_symbolic("aten::expm1", lambda g,x: g.op("Sub",g.op("Exp",x),g.op("Constant",value_t=torch.tensor(1.0))),18)
    w=pathlib.Path(a.weights); out=pathlib.Path(a.output); out.mkdir(parents=True,exist_ok=True)
    model=load_acoustic(w/'ema.pt','cpu'); decoder=load_decoder(w/'decoder.pt','cpu'); engine=Engine(model,decoder,'cpu'); frontend=Frontend(model.vocab)
    piece=engine.piece(frontend('Merhaba Gökhan, bugün hava çok güzel.'),0,42); engine.plan([piece],1); engine.think([piece])
    L,T=piece.letters,piece.frames
    text_args=(piece.ids[None],torch.ones(1,L,dtype=torch.bool))
    sound_args=(piece.h[None],piece.dur[None],text_args[1],piece.cw[None],piece.wstart[None],piece.fw[None],piece.fp[None],torch.ones(1,T,dtype=torch.bool),engine.noise(piece)[None])
    exports=[('text',Text(model),text_args,['ids','mask'],['h','dur'],{'ids':{1:'letters'},'mask':{1:'letters'},'h':{1:'letters'},'dur':{1:'letters'}}),
      ('sound',Sound(model),sound_args,['h','dur','mask','cw','wstart','fw','fp','fmask','noise'],['latents'],{**{k:{1:'letters'} for k in ['h','dur','mask','cw','wstart']},**{k:{1:'frames'} for k in ['fw','fp','fmask','latents']},'noise':{2:'frames'}}),
      ('decoder',Decode(decoder),(piece.latents[:33][None],),['z'],['audio'],{'z':{1:'frames'},'audio':{1:'samples'}})]
    with torch.no_grad():
        for name, module, args, inputs,outputs,axes in exports:
            print('Exporting',name,flush=True)
            torch.onnx.export(module,args,str(out/(name+'.onnx')),input_names=inputs,output_names=outputs,dynamic_axes=axes,opset_version=18,dynamo=False,external_data=False)
            onnx.checker.check_model(str(out/(name+'.onnx')))
    sessions={n:ort.InferenceSession(str(out/(n+'.onnx')),providers=['CPUExecutionProvider']) for n in ['text','sound','decoder']}
    proof=[]
    for sentence in ['Merhaba Gökhan, bugün hava çok güzel.','İnternet olmadan da konuşabiliyorum.']:
        start=time.monotonic(); piece=engine.piece(frontend(sentence),0,42); engine.plan([piece],1); engine.think([piece]); L,T=piece.letters,piece.frames
        h,dur=sessions['text'].run(None,{'ids':piece.ids[None].numpy(),'mask':np.ones((1,L),bool)})
        counts=np.clip(np.rint(np.bincount(piece.cw.numpy(),weights=dur[0])),1,250).astype(np.int64)
        fw=np.repeat(np.arange(len(counts)),counts)[:3000]; fp=((np.arange(len(fw))-(np.cumsum(counts)-counts)[fw])/counts[fw]).astype(np.float32)
        noise=engine.noise(piece)[None].numpy()
        assert len(fw)==T,(len(fw),T)
        z=sessions['sound'].run(None,{'h':h,'dur':dur,'mask':np.ones((1,L),bool),'cw':piece.cw[None].numpy(),'wstart':piece.wstart[None].numpy(),'fw':fw[None],'fp':fp[None],'fmask':np.ones((1,T),bool),'noise':noise})[0]
        errors={'text':float(np.max(np.abs(h-piece.h[None].numpy()))),'latent':float(np.max(np.abs(z-piece.latents[None].numpy())))}
        audio=sessions['decoder'].run(None,{'z':z[:,:33]})[0]
        ref=decoder(torch.from_numpy(z[:,:33]).transpose(1,2)).detach().numpy()
        errors['audio']=float(np.max(np.abs(audio-ref))); assert errors['latent']<1e-3 and errors['audio']<1e-3,errors
        proof.append({'text':sentence,'letters':L,'frames':T,'errors':errors,'seconds':time.monotonic()-start}); print(proof[-1],flush=True)
    meta={'format':1,'engine':'ema-lightning','ema_version':'1.0.1','sample_rate':48000,'hop':decoder.hop,'vocab':model.vocab,'times':model.times,'latent_dim':model.latent_dim,'hidden_dim':model.d,'source':'https://huggingface.co/canberkkkkkk/ema-lightning','revision':w.name,'license':'Apache-2.0'}
    (out/'config.json').write_text(json.dumps(meta,ensure_ascii=False))
    files=[{'name':f.name,'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sorted(out.iterdir()) if f.suffix in ['.onnx','.json']]
    (out/'manifest.json').write_text(json.dumps({'version':1,'files':files},indent=2))
    (out/'export-proof.json').write_text(json.dumps(proof,indent=2,ensure_ascii=False))
    print('Verified',sum(f['bytes'] for f in files),'bytes',flush=True)
if __name__=='__main__': main()
