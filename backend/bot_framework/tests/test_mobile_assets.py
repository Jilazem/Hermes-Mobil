import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from bot_framework.mobile_assets import configured_models, serve_ema

class AssetsTest(unittest.TestCase):
    def test_live_inventory_removes_unadvertised_alias_without_config_write(self):
        config={'providers':{'local':{'models':['real-model','yerel'],'default_model':'yerel'}}}
        result=configured_models(config,{'local':['real-model']})
        self.assertEqual(['real-model'],result['providers'][0]['models'])
        self.assertEqual('yerel',config['providers']['local']['default_model'])
    def test_empty_live_provider_does_not_invent_a_default_model(self):
        self.assertEqual([],configured_models({'providers':{'local':{'default_model':'yerel'}}},{'local':[]})['providers'])
    def test_config_secrets_and_urls_are_not_serialized(self):
        config={'providers':{'local':{'models':{'model-one':{'api_key':'SECRET-IN-METADATA'}},'api_key':'SECRET','base_url':'https://private'},'disabled':{'enabled':False,'models':['hidden']}}}
        result=configured_models(config)
        self.assertEqual(['model-one'],result['providers'][0]['models'])
        self.assertEqual(1,len(result['providers']))
        for secret in ('SECRET','api_key','https://private','base_url'):self.assertNotIn(secret,json.dumps(result))
    def test_no_models_are_invented_for_empty_provider(self):
        self.assertEqual([],configured_models({'providers':{'empty':{'api_key':'SECRET'}}})['providers'])
    def test_invalid_names_are_not_model_ids(self):
        result=configured_models({'providers':{'one':{'models':['safe-model',{'token':'SECRET'},'x\nheader']}}})
        self.assertEqual(['safe-model'],result['providers'][0]['models'])
    def test_range_and_path_guards(self):
        class Handler:
            headers={'Range':'bytes=2-4'}
            wfile=io.BytesIO()
            def _error(self,code,*_):self.code=code
            def send_response(self,code):self.code=code
            def send_header(self,*_):pass
            def end_headers(self):pass
        import hashlib
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'test.bin').write_bytes(b'012345')
            pins={'test.bin':(6,hashlib.sha256(b'012345').hexdigest())}
            with patch('bot_framework.mobile_assets.EMA_FILES',pins):
                h=Handler();serve_ema(h,root,'test.bin');self.assertEqual(206,h.code);self.assertEqual(b'234',h.wfile.getvalue())
                h=Handler();h.headers={'Range':'bytes=6-'};serve_ema(h,root,'test.bin');self.assertEqual(416,h.code)
                h=Handler();serve_ema(h,root,'../test.bin');self.assertEqual(404,h.code)
                (root/'test.bin').write_bytes(b'CORRUP');h=Handler();serve_ema(h,root,'test.bin');self.assertEqual(503,h.code)
