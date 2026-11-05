"""Read-only mobile inventory and immutable EMA assets. No config secrets leave here."""
import hashlib
import json
import re
import urllib.request
import urllib.parse

EMA_FILES = {
    'config.json': (584, 'e8d20ee8458fc4c583af7ced3e52e151c2efd8ddac92443fa39ab73b346fb512'),
    'text.onnx': (4808338, 'a5418a36318ae00048f2506c5c0e17a70da3834b59a35f3f94955f8eac2765a2'),
    'sound.onnx': (18134687, '553fb64ecd2e2178b88fff9de0b80f949601a09f132bea1528bcd0c56010502c'),
    'decoder.onnx': (12040974, '89c6b502181b39aa69f208bfe5715e6f8de2790ba69de7a57c63cbfb2be61fdf'),
}

def configured_models(config, discovered=None):
    def valid(value):
        return isinstance(value, str) and bool(re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_./:@+\-]{0,199}', value))
    providers = []
    for slug, provider in config.get('providers', {}).items():
        if not valid(slug) or not isinstance(provider, dict) or provider.get('enabled') is False:
            continue
        models = provider.get('models', {})
        names = list(models) if isinstance(models, (dict,list)) else []
        names.append(provider.get('default_model'))
        if discovered is not None and slug in discovered:
            names = discovered[slug]
        names = list(dict.fromkeys(value for value in names if valid(value)))
        if names: providers.append(dict(slug=slug, name=slug, models=names))
    return dict(providers=providers, notice='Kayıtlı modeller gösteriliyor. Canlı katalog, Dashboard güncellemesi nedeniyle şu anda kullanılamıyor.')

def read_configured_models(url, token):
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs): return None
    request = urllib.request.Request(url, headers={'X-Hermes-Session-Token':token})
    with urllib.request.build_opener(NoRedirect).open(request, timeout=15) as response:
        raw = response.read(2_097_153)
    if len(raw)>2_097_152: raise ValueError('Inventory too large')
    config=json.loads(raw)
    discovered={}
    # Only local model endpoints may be discovered here. Never send a Dashboard
    # token to a provider, follow redirects, or probe arbitrary external URLs.
    for slug, provider in config.get('providers',{}).items():
        if not isinstance(provider,dict) or provider.get('enabled') is False:continue
        base=provider.get('base_url')
        if not isinstance(base,str):continue
        parts=urllib.parse.urlsplit(base)
        if parts.hostname not in ('127.0.0.1','localhost','::1') or parts.scheme not in ('http','https') or parts.username or parts.password or parts.query or parts.fragment:continue
        headers={}
        key=provider.get('api_key')
        if isinstance(key,str) and key:headers['Authorization']='Bearer '+key
        try:
            request=urllib.request.Request(base.rstrip('/')+'/models',headers=headers)
            with urllib.request.build_opener(NoRedirect).open(request,timeout=3) as response:catalog_raw=response.read(1_048_577)
            if len(catalog_raw)>1_048_576:continue
            rows=json.loads(catalog_raw).get('data')
            if isinstance(rows,list):discovered[slug]=[row.get('id') for row in rows[:1000] if isinstance(row,dict)]
        except Exception:pass
    return configured_models(config,discovered)

def serve_ema(handler, directory, name):
    if name not in EMA_FILES or directory is None:
        handler._error(404, 'model_not_found', 'Model dosyası bulunamadı.'); return
    target = directory/name
    size, digest = EMA_FILES[name]
    try:
        with target.open('rb') as stream:
            if target.stat().st_size != size or hashlib.file_digest(stream, 'sha256').hexdigest() != digest:
                handler._error(503, 'model_integrity', 'Model doğrulaması başarısız.'); return
            start, end = 0, size-1
            value=handler.headers.get('Range')
            if value:
                match=re.fullmatch(r'bytes=(\d+)-(\d*)', value)
                if not match:
                    handler.send_response(416); handler.send_header('Content-Range',f'bytes */{size}'); handler.send_header('Content-Length','0'); handler.end_headers(); return
                start=int(match[1]); end=int(match[2]) if match[2] else size-1
                if start>=size or start>end or end>=size:
                    handler.send_response(416); handler.send_header('Content-Range',f'bytes */{size}'); handler.send_header('Content-Length','0'); handler.end_headers(); return
            handler.send_response(206 if value else 200)
            handler.send_header('Content-Type','application/octet-stream')
            handler.send_header('Content-Length',str(end-start+1))
            handler.send_header('Accept-Ranges','bytes')
            handler.send_header('X-Content-Type-Options','nosniff')
            if value: handler.send_header('Content-Range',f'bytes {start}-{end}/{size}')
            handler.end_headers(); stream.seek(start); remaining=end-start+1
            while remaining:
                block=stream.read(min(131072,remaining))
                if not block:break
                handler.wfile.write(block);remaining-=len(block)
    except FileNotFoundError:handler._error(404,'model_not_found','Model dosyası bulunamadı.')

