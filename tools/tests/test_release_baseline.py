"""Regression tests for the release-documentation baseline guard."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_release_baseline import inspect  # noqa: E402


class ReleaseBaselineTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.put("app/build.gradle.kts", '''
        versionName = "0.2.28"
        versionCode = 30
        ''')
        self.put("docs/releases/v0.2.28.md", '''
## 新增
无
## 修复
修正文案
## 验证
测试通过
## 已知限制
设备待验
''')
        self.put("AGENTS.md", "- 当前开发版本：以 app/build.gradle.kts 为准；正式状态见 GitHub Releases\n")
        self.put("README.md", "- 版本：以 app/build.gradle.kts 为准；正式状态见 GitHub Releases\n")

    def put(self, path: str, content: str):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def test_dynamic_status_fields_pass(self):
        self.assertEqual([], inspect(self.root))

    def test_outdated_current_versions_fail(self):
        self.put("AGENTS.md", "- 当前开发版本：**0.2.17**（versionCode 19；正式状态见 Releases）\n")
        self.put("README.md", "- 版本：\`0.2.17\`（versionCode 19；发布状态以 Releases 为准）\n")
        failures = inspect(self.root)
        self.assertTrue(any("AGENTS.md: hardcoded" in error for error in failures))
        self.assertTrue(any("README.md: hardcoded" in error for error in failures))

    def test_missing_release_notes_fail(self):
        (self.root / "docs/releases/v0.2.28.md").unlink()
        self.assertTrue(any("Missing release notes" in error for error in inspect(self.root)))

    def test_missing_heading_fails(self):
        self.put("docs/releases/v0.2.28.md", "## 新增\n无\n## 修复\n无\n## 验证\n测试通过\n")
        self.assertTrue(any("已知限制" in error for error in inspect(self.root)))

    def test_invalid_version_code_fails(self):
        self.put("app/build.gradle.kts", 'versionName = "0.2.28"\nversionCode = 0\n')
        self.assertTrue(any("invalid versionName/versionCode" in error for error in inspect(self.root)))


if __name__ == "__main__":
    unittest.main()
