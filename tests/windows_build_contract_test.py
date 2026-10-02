"""Isolated server-side contract tests: no Cargo, Flutter, downloads or driver install."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


assets = load('windows_assets', 'scripts/windows_assets.py')
portable = load('tunnel_portable', 'libs/portable/generate.py')


class WindowsBuildContracts(unittest.TestCase):
    def test_payload_must_match_embedded_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            stage = root / 'stage'; stage.mkdir()
            (stage / 'tunnel.exe').write_bytes(b'MZ-synthetic-runner')
            (stage / 'drivers').mkdir()
            (stage / 'drivers/device.inf').write_text('driver\n', encoding='utf-8')
            payload = portable.write_payload(stage, root, stage / 'tunnel.exe', 1, ['drivers'])
            image = root / 'synthetic.exe'
            image.write_bytes(b'MZ-prefix' + payload.read_bytes() + b'suffix')
            self.assertEqual(portable.verify_embedded_payload(image, payload),
                             hashlib.sha256(payload.read_bytes()).hexdigest())
            image.write_bytes(b'MZ-prefix' + payload.read_bytes()[:-1] + b'Xsuffix')
            with self.assertRaises(RuntimeError):
                portable.verify_embedded_payload(image, payload)

    def test_required_driver_cannot_be_an_empty_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); stage = root / 'stage'; stage.mkdir()
            (stage / 'tunnel.exe').write_bytes(b'MZ')
            (stage / 'drivers').mkdir(); (stage / 'drivers/empty-subdir').mkdir()
            with self.assertRaises(ValueError):
                portable.write_payload(stage, root, stage / 'tunnel.exe', 1, ['drivers'])

    def test_zip_rejects_windows_escape_and_name_collisions(self):
        for names in (['../escape'], ['C:/escape'], ['drivers/CON.inf'],
                      ['driver/file', 'DRIVER/FILE'], ['driver/file.']):
            with self.subTest(names=names), tempfile.TemporaryDirectory() as folder:
                cache = Path(folder) / 'cache'; cache.mkdir(); archive = cache / 'bad.zip'
                with zipfile.ZipFile(archive, 'w') as stream:
                    for name in names:
                        stream.writestr(name, 'x')
                with self.assertRaises(ValueError):
                    assets.extract(archive, cache)
                self.assertFalse((cache.parent / 'escape').exists())

    def test_x64_driver_selection_ignores_win32_duplicate_inf(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); (root / 'Win32').mkdir()
            expected = root / 'usbmmIdd.inf'; expected.write_text('x64')
            (root / 'Win32/usbmmIdd.inf').write_text('x86')
            self.assertEqual(assets.unique_file(root, 'usbmmIdd.inf', exclude_32bit=True), expected)

    def test_offline_missing_asset_never_downloads(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, 'Offline cache is missing'):
                assets.acquired({'name': 'asset.zip', 'sha256': '0' * 64,
                                 'url': 'https://invalid.example/asset.zip'}, Path(folder), True)


if __name__ == '__main__':
    unittest.main()
