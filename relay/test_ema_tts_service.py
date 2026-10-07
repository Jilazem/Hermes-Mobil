import http.client
import io
import json
import struct
import threading
import time
import unittest
import uuid
import wave

from ema_tts_service import EmaRuntime, EmaServer, RequestError, validate


class Chunk:
    """A deterministic fake matching the numpy conversion interface."""
    def clip(self, *_): return self
    def __mul__(self, _): return self
    def astype(self, _): return self
    def tobytes(self): return struct.pack('<hhhh', 0, 1000, -1000, 0)


class Model:
    def stream(self, text, **_):
        if text == 'fail': raise RuntimeError('private details must not escape')
        for _ in range(3): yield Chunk()


class ServiceTest(unittest.TestCase):
    def setUp(self):
        self.loads = 0
        def factory():
            self.loads += 1
            return Model()
        self.runtime = EmaRuntime(factory)
        self.server = EmaServer(('127.0.0.1', 0), 'test-secret', self.runtime)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, method, path, payload=None, token='test-secret'):
        conn = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=3)
        body = json.dumps(payload) if payload is not None else None
        conn.request(method, path, body, {'X-Hermes-Session-Token': token})
        response = conn.getresponse()
        result = response.status, dict(response.getheaders()), response.read()
        conn.close()
        return result

    def test_auth_and_health_do_not_load_model(self):
        self.assertEqual(403, self.request('GET', '/health', token='bad')[0])
        status, _, body = self.request('GET', '/health')
        self.assertEqual(200, status)
        self.assertFalse(json.loads(body)['loaded'])
        self.assertEqual(0, self.loads)

    def test_wav_and_pcm_are_same_audio_and_model_reused(self):
        status, _, body = self.request('POST', '/speak', {'text': 'merhaba', 'sample_rate': 16000})
        self.assertEqual(200, status)
        with wave.open(io.BytesIO(body)) as wav:
            self.assertEqual((1, 2, 16000, 12), (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getnframes()))
            pcm = wav.readframes(12)
        status, headers, body = self.request('POST', '/stream', {'text': 'merhaba', 'sample_rate': 16000})
        self.assertEqual(200, status)
        self.assertEqual('chunked', headers['Transfer-Encoding'])
        self.assertEqual('pcm_s16le', headers['X-Audio-Format'])
        self.assertEqual(pcm, body)
        self.assertEqual(1, self.loads)

    def test_invalid_input_and_cancel_payload(self):
        for payload in [[], {}, {'text': 'a', 'sample_rate': True}, {'text': 'a', 'speed': 0}, {'text': 'a', 'request_id': 5}]:
            self.assertEqual(400, self.request('POST', '/speak', payload)[0])
        for payload in [[], {'request_id': 5}, {'request_id': None}]:
            self.assertEqual(400, self.request('POST', '/cancel', payload)[0])
        self.assertEqual({'cancelled': False}, json.loads(self.request('POST', '/cancel', {'request_id': str(uuid.uuid4())})[2]))

    def test_inference_error_is_a_failure_without_private_details(self):
        status, _, body = self.request('POST', '/stream', {'text': 'fail'})
        self.assertEqual(503, status)
        self.assertNotIn(b'private', body)
        self.assertEqual(200, self.request('POST', '/speak', {'text': 'next'})[0])

    def test_cancel_keeps_capacity_until_inflight_inference_returns(self):
        entered, unblock = [], threading.Event()
        class Slow:
            def stream(self, *_, **__):
                entered.append(1)
                unblock.wait(3)
                yield Chunk()
        runtime = EmaRuntime(Slow)
        requests = []
        try:
            for i in range(4):
                req = runtime.start(validate({'text': 'slow', 'request_id': str(uuid.uuid4())}))
                requests.append(req)
            deadline = time.monotonic() + 1
            while len(entered) != 4 and time.monotonic() < deadline: time.sleep(.01)
            self.assertEqual(4, len(entered))
            for req in requests:
                runtime.cancel(req.id)
                runtime.finish(req)
            with self.assertRaises(RequestError): runtime.start(validate({'text': 'overflow'}))
        finally:
            unblock.set()
            for req in requests: self.assertTrue(req.producer_done.wait(2))
        deadline = time.monotonic() + 1
        while runtime.active and time.monotonic() < deadline: time.sleep(.01)
        self.assertFalse(runtime.active)


if __name__ == '__main__': unittest.main()
