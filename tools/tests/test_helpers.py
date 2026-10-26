import errno
import importlib.util
import json
import os
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
receiver = module('adb_receiver')


class Helpers(unittest.TestCase):
    def test_report_is_private_before_any_data_is_written(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'result.json'
            real_dump = json.dump
            def inspect_write(data, output, **kwargs):
                self.assertEqual(os.fstat(output.fileno()).st_mode & 0o777, 0o600)
                return real_dump(data, output, **kwargs)
            previous_umask = os.umask(0)
            try:
                with patch.object(receiver.json, 'dump', side_effect=inspect_write):
                    receiver.save_report(path, {'run_id': 'test-fixture-only'})
            finally:
                os.umask(previous_umask)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual(json.loads(path.read_text()), {'run_id': 'test-fixture-only'})

    def test_report_does_not_follow_predictable_temporary_symlink(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'result.json'
            victim = Path(d) / 'unrelated.txt'
            victim.write_text('keep')
            path.with_suffix('.tmp').symlink_to(victim)
            receiver.save_report(path, {'run_id': 'test-fixture-only'})
            self.assertEqual(victim.read_text(), 'keep')

    def test_failed_report_preserves_previous_output_and_removes_temporary_file(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'result.json'
            path.write_text('previous')
            with patch.object(receiver.os, 'replace', side_effect=OSError('disk error')):
                with self.assertRaises(OSError):
                    receiver.save_report(path, {'run_id': 'test-fixture-only'})
            self.assertEqual(path.read_text(), 'previous')
            self.assertEqual(list(Path(d).iterdir()), [path])

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
