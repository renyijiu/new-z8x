import errno
import importlib.util
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

TOOLS = Path(__file__).resolve().parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


ports = module('check_ports')
share = module('samba_share')
verify = module('verify')


class Helpers(unittest.TestCase):
    def test_refused_is_distinguished_from_unknown(self):
        for error, expected in [(ConnectionRefusedError(errno.ECONNREFUSED, 'refused'), 'REFUSED'),
                                (TimeoutError('timeout'), 'UNKNOWN'),
                                (OSError(errno.EHOSTUNREACH, 'unreachable'), 'UNKNOWN')]:
            with patch.object(ports.socket, 'create_connection', side_effect=error):
                self.assertEqual(ports.check('192.168.1.2', 22)[0], expected)

    def test_foreign_container_is_not_claimed(self):
        responses = [types.SimpleNamespace(stdout='1'), types.SimpleNamespace(stdout='container-id'),
                     types.SimpleNamespace(stdout=json.dumps([{'Config': {'Labels': {share.LABEL: 'someone-else'}}}]))]
        with patch.object(share, 'run', side_effect=responses):
            with self.assertRaises(RuntimeError):
                share.inspect()

    def test_stop_preserves_password_on_unknown_ownership(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'samba-password'
            path.write_text('test-fixture-only')
            with patch.object(share, 'LOCAL', Path(d)), patch.object(share, 'inspect', side_effect=RuntimeError('foreign')):
                with self.assertRaises(RuntimeError): share.stop()
            self.assertTrue(path.exists())

    def test_expired_share_cleanup(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'samba-password'
            path.write_text('test-fixture-only')
            with patch.object(share, 'LOCAL', Path(d)), patch.object(share, 'inspect', return_value=None), patch.object(share, 'run') as run:
                share.stop()
                run.assert_not_called()
            self.assertFalse(path.exists())

    def test_release_integrity(self):
        self.assertEqual(verify.main(), 0)

    def test_corrupted_release_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root/'packages').mkdir()
            (root/'verification').mkdir()
            (root/'packages/test.apk').write_bytes(b'changed')
            (root/'packages/SHA256SUMS').write_text('0'*64 + '  test.apk\n')
            (root/'verification/release.json').write_text(json.dumps({'packages':[{'file':'test.apk','sha256':'0'*64,'bytes':7,'version':'test'}]}))
            with patch.object(verify, 'ROOT', root):
                self.assertEqual(verify.main(), 1)


if __name__ == '__main__':
    unittest.main()
