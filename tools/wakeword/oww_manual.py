# Kotlin'e birebir taşınacak akış: TFLite yorumlayıcılarıyla elle openWakeWord.
import os, numpy as np, openwakeword
from ai_edge_litert.interpreter import Interpreter
D=os.path.join(os.path.dirname(openwakeword.__file__),'resources','models')
class OWW:
    def __init__(self):
        self.mel=Interpreter(model_path=f'{D}/melspectrogram.tflite', experimental_delegates=[], num_threads=1)
        self.mel.resize_tensor_input(0,[1,1760],strict=True); self.mel.allocate_tensors()
        self.emb=Interpreter(model_path=f'{D}/embedding_model.tflite', num_threads=1)
        self.emb.resize_tensor_input(0,[1,76,32,1],strict=True); self.emb.allocate_tensors()
        self.ww=Interpreter(model_path=f'{D}/hey_jarvis_v0.1.tflite', num_threads=1); self.ww.allocate_tensors()
        self.reset()
    def reset(self):
        self.raw=np.zeros(0,dtype=np.float32)          # son 1760 örnek
        self.mels=np.ones((76,32),dtype=np.float32)     # mel tamponu
        self.feats=np.zeros((16,96),dtype=np.float32)   # gömme tamponu
        self.n=0
    def step(self, chunk_int16):  # 1280 örnek
        self.raw=np.concatenate([self.raw, chunk_int16.astype(np.float32)])[-1760:]
        if len(self.raw)<1760: return 0.0
        i=self.mel.get_input_details()[0]['index']; self.mel.set_tensor(i, self.raw[None,:]); self.mel.invoke()
        spec=np.squeeze(self.mel.get_tensor(self.mel.get_output_details()[0]['index']))/10+2   # (8,32)
        self.mels=np.vstack([self.mels,spec])[-76:]
        i=self.emb.get_input_details()[0]['index']; self.emb.set_tensor(i, self.mels[None,:,:,None].astype(np.float32)); self.emb.invoke()
        e=np.squeeze(self.emb.get_tensor(self.emb.get_output_details()[0]['index']))   # (96,)
        self.feats=np.vstack([self.feats,e])[-16:]
        self.n+=1
        i=self.ww.get_input_details()[0]['index']; self.ww.set_tensor(i, self.feats[None,:,:].astype(np.float32)); self.ww.invoke()
        s=float(np.squeeze(self.ww.get_tensor(self.ww.get_output_details()[0]['index'])))
        return s if self.n>=5 else 0.0   # ilk çerçeveler ısınma
if __name__=='__main__':
    o=OWW()
    for x in [o.mel,o.emb,o.ww]:
        print([ (d['shape'].tolist(), d['dtype'].__name__) for d in x.get_input_details()], '->', [ (d['shape'].tolist()) for d in x.get_output_details()])
