"""Check that first-time server setup cannot replace production credentials."""
import base64
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("prepare_environment", ROOT / "deploy/digitalocean/prepare-env.py")
PREPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREPARE)


class EnvironmentTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name)

    def configure(self, origin):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            PREPARE.prepare(self.base, "production", origin)
        return output.getvalue()

    def test_random_credentials_are_private_and_not_printed(self):
        output = self.configure("http://164.92.163.71")
        path = self.base / "production/backend/.env"
        entries = dict(line.split("=", 1) for line in path.read_text().splitlines())
        self.assertEqual(len(base64.b64decode(entries["JWT_SECRET"])), 32)
        self.assertNotEqual(entries["MYSQL_PASSWORD"], entries["MYSQL_ROOT_PASSWORD"])
        self.assertNotIn(entries["MYSQL_PASSWORD"], output)
        self.assertNotIn(entries["JWT_SECRET"], output)
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual((self.base / "production/frontend/.env").stat().st_mode & 0o777, 0o600)

    def test_existing_environment_is_never_rotated(self):
        self.configure("https://quiz.example.com")
        path = self.base / "production/backend/.env"
        previous = path.read_bytes()
        with self.assertRaises(FileExistsError):
            self.configure("https://quiz.example.com")
        self.assertEqual(path.read_bytes(), previous)

    def test_https_configures_the_domain_and_http_preserves_custom_ports(self):
        self.configure("http://164.92.163.71:8080")
        self.assertIn("PUBLIC_ORIGIN=http://164.92.163.71:8080", (self.base / "production/backend/.env").read_text())
        self.assertIn("HTTP_PORT=8080", (self.base / "production/frontend/.env").read_text())
        # A different environment gets its own credentials and TLS configuration.
        PREPARE.prepare(self.base, "staging", "https://quiz.example.com")
        self.assertIn("SITE_ADDRESS=quiz.example.com", (self.base / "staging/frontend/.env").read_text())

    def test_credentials_paths_queries_and_invalid_origins_are_rejected(self):
        for origin in ("https://u:p@quiz.example.com", "https://quiz.example.com/api", "https://quiz.example.com?x=y", "https://quiz.example.com#fragment", "file:///tmp/a", "https://quiz.example.com:8443"):
            with self.subTest(origin=origin), self.assertRaises(ValueError):
                self.configure(origin)
        self.assertFalse((self.base / "production").exists())


if __name__ == "__main__":
    unittest.main()
