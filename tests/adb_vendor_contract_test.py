"""Read-only provenance and isolated staging checks. Never executes adb or a product build."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('prepare_adb', ROOT / 'android-adb/prepare_adb.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
LOCK = json.loads(prepare.LOCK_PATH.read_text(encoding='utf-8'))


class AdbVendorContracts(unittest.TestCase):
    def test_complete_upstream_snapshot_matches_recorded_hashes(self):
        provenance = json.loads((prepare.VENDORED / 'TUNNEL_SOURCE.json').read_text())
        self.assertEqual(provenance['commit'], LOCK['commit'])
        self.assertTrue(provenance['files'])
        for entry in provenance['files']:
            with self.subTest(path=entry['path']):
                source = prepare.VENDORED / entry['path']
                source.resolve().relative_to(prepare.VENDORED.resolve())
                self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), entry['sha256'])

    def test_all_abis_and_licenses_stage_offline_without_network(self):
        entries = dict(LOCK['artifacts'], **LOCK['notices'])
        with tempfile.TemporaryDirectory() as folder, mock.patch.object(prepare.urllib.request, 'urlopen') as network:
            for name, entry in entries.items():
                with self.subTest(asset=name):
                    destination = Path(folder) / name
                    content = prepare.acquire(destination, entry, LOCK['commit'], offline=True)
                    self.assertEqual(prepare.blob_sha1(content), entry['gitBlobSha1'])
                    self.assertEqual(destination.read_bytes(), content)
            network.assert_not_called()

    def test_corrupt_vendor_is_rejected_without_network_or_overwriting_destination(self):
        entry = LOCK['artifacts']['arm64-v8a']
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            vendor = root / 'vendor'
            source = vendor / entry['path']
            source.parent.mkdir(parents=True)
            source.write_bytes(b'not the pinned binary')
            destination = root / 'staged.so'
            with mock.patch.object(prepare, 'VENDORED', vendor), \
                    mock.patch.object(prepare.urllib.request, 'urlopen') as network:
                with self.assertRaisesRegex(ValueError, 'blob mismatch'):
                    prepare.acquire(destination, entry, LOCK['commit'], offline=False)
                network.assert_not_called()
            self.assertFalse(destination.exists())


if __name__ == '__main__':
    unittest.main()
