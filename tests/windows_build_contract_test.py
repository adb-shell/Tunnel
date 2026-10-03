"""Isolated server-side contract tests: no Cargo, Flutter, downloads or driver install."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import struct
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


def synthetic_pe(path, machine=0x8664, dll=False):
    image = bytearray(88)
    image[:2] = b'MZ'
    struct.pack_into('<I', image, 60, 64)
    image[64:68] = b'PE\0\0'
    struct.pack_into('<H', image, 68, machine)
    struct.pack_into('<H', image, 86, 0x2002 if dll else 0x0002)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(image)


def synthetic_amyuni(folder):
    folder.mkdir(parents=True, exist_ok=True)
    (folder / 'usbmmIdd.inf').write_text('[Version]\nCatalogFile=usbmmidd.cat\n')
    (folder / 'usbmmidd.cat').write_bytes(b'synthetic-catalog')
    (folder / 'License.txt').write_text('synthetic license')
    synthetic_pe(folder / 'x64/usbmmIdd.dll', dll=True)
    synthetic_pe(folder / 'Win32/usbmmIdd.dll', machine=0x014c, dll=True)
    synthetic_pe(folder / 'deviceinstaller64.exe')
    synthetic_pe(folder / 'deviceinstaller.exe', machine=0x014c)
    (folder / 'usbmmidd.bat').write_text('synthetic legacy installer')


class WindowsBuildContracts(unittest.TestCase):
    def test_amyuni_umdf_package_requires_no_vendor_sys_and_keeps_x64_layout(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); source = root / 'source'; stage = root / 'stage'
            synthetic_amyuni(source)
            self.assertEqual(list(source.rglob('*.sys')), [])
            assets.require_amyuni_x64(source)
            assets.copy_tree(source, stage, exclude_32bit=True)
            assets.require_amyuni_x64(stage)
            self.assertTrue((stage / 'x64/usbmmIdd.dll').is_file())
            self.assertTrue((stage / 'License.txt').is_file())
            self.assertFalse((stage / 'Win32').exists())
            self.assertFalse((stage / 'deviceinstaller.exe').exists())
            self.assertFalse((stage / 'usbmmidd.bat').exists())

    def test_amyuni_rejects_missing_empty_or_wrong_architecture_payload(self):
        required = ('usbmmIdd.inf', 'usbmmidd.cat', 'x64/usbmmIdd.dll',
                    'deviceinstaller64.exe', 'License.txt')
        for name in required:
            for empty in (False, True):
                with self.subTest(name=name, empty=empty), tempfile.TemporaryDirectory() as folder:
                    source = Path(folder); synthetic_amyuni(source)
                    if empty:
                        (source / name).write_bytes(b'')
                    else:
                        (source / name).unlink()
                    with self.assertRaises(ValueError):
                        assets.require_amyuni_x64(source)
        for name, dll in (('x64/usbmmIdd.dll', True), ('deviceinstaller64.exe', False)):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                source = Path(folder); synthetic_amyuni(source)
                synthetic_pe(source / name, machine=0x014c, dll=dll)
                with self.assertRaisesRegex(ValueError, 'Windows x64'):
                    assets.require_amyuni_x64(source)

    def test_new_digest_pin_reuses_only_matching_legacy_cache_offline(self):
        for matching in (True, False):
            with self.subTest(matching=matching), tempfile.TemporaryDirectory() as folder:
                cache = Path(folder); old = cache / '42-driver.zip'
                with zipfile.ZipFile(old, 'w') as archive:
                    archive.writestr('driver.inf', 'synthetic')
                digest = assets.sha256(old) if matching else '0' * 64
                spec = {'name': 'driver.zip', 'asset_id': 42, 'sha256': digest,
                        'size': old.stat().st_size, 'url': 'https://invalid.example/driver.zip'}
                if matching:
                    acquired, receipt = assets.acquired(spec, cache, True)
                    self.assertEqual(acquired.name, digest + '-driver.zip')
                    self.assertEqual(receipt['sha256'], digest)
                    self.assertTrue(old.is_file())
                else:
                    with self.assertRaisesRegex(ValueError, 'Offline cache is missing'):
                        assets.acquired(spec, cache, True)
                    self.assertFalse((cache / (digest + '-driver.zip')).exists())

    def test_portable_rejects_wrong_architecture_and_dll_artifacts(self):
        with tempfile.TemporaryDirectory() as folder:
            artifact = Path(folder) / 'synthetic.exe'
            for machine, flags, accepted in ((0x8664, 0x0002, True),
                                             (0xaa64, 0x0002, False),
                                             (0x014c, 0x0002, False),
                                             (0x8664, 0x2002, False)):
                image = bytearray(88)
                image[:2] = b'MZ'; struct.pack_into('<I', image, 60, 64)
                image[64:68] = b'PE\0\0'
                struct.pack_into('<H', image, 68, machine)
                struct.pack_into('<H', image, 86, flags)
                artifact.write_bytes(image)
                if accepted:
                    portable.require_windows_x64_executable(artifact)
                else:
                    with self.assertRaises(RuntimeError):
                        portable.require_windows_x64_executable(artifact)

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
