import json
import unittest
from unittest.mock import Mock

from coolapk_bridge import GitHub, event_feedback, issue_body, issue_title, marker, normalize


BASE = {"external_id": "reply:201:303", "source_url": "https://www.coolapk.com/feed/201?replyid=303",
        "text": "导出图片顺序不对\n希望修复", "author": "test", "version": "0.2.8"}


class IntakeTests(unittest.TestCase):
    def test_normalized_and_drops_query(self):
        item = normalize(BASE)
        self.assertEqual(item["source_url"], "https://www.coolapk.com/feed/201")
        self.assertIn("导出图片", issue_title(item))
        self.assertIn("> 希望修复", issue_body(item))

    def test_rejects_untrusted_domains_and_paths(self):
        for bad in ["https://www.coolapk.com.evil.com/feed/201", "http://www.coolapk.com/feed/201",
                    "https://www.coolapk.com/@evil/feed/201", "https://www.coolapk.com:8443/feed/201"]:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                normalize({**BASE, "source_url": bad})

    def test_rejects_invalid_payload(self):
        for payload in [{**BASE, "external_id": "a b"}, {**BASE, "text": ""},
                        {**BASE, "text": "x" * 12001}, {**BASE, "author": ["fake"]}]:
            with self.subTest(payload=str(payload)[:100]), self.assertRaises(ValueError):
                normalize(payload)

    def test_same_id_has_stable_marker_and_is_not_raw(self):
        self.assertEqual(marker(normalize(BASE)), marker(normalize({**BASE, "text": "edit"})))
        self.assertNotIn("reply:201:303", marker(normalize(BASE)))

    def test_dispatch_event(self):
        self.assertEqual(event_feedback("repository_dispatch", {"action": "coolapk-feedback", "client_payload": BASE}), BASE)
        self.assertEqual(event_feedback("workflow_dispatch", {"inputs": {"feedback_json": json.dumps(BASE)}}), BASE)
        with self.assertRaises(ValueError):
            event_feedback("push", {})

    def test_create_issue_and_deduplicate(self):
        item = normalize(BASE)
        client = GitHub("ldohgfsdu/changjietu", "test-token")
        client.request = Mock(side_effect=[[], {"number": 5, "html_url": "https://github.com/issue/5"}])
        self.assertTrue(client.create_issue(item)["created"])
        self.assertEqual(client.request.call_args.args[0], "POST")
        second = GitHub("ldohgfsdu/changjietu", "test-token")
        second.request = Mock(return_value=[{"number": 5, "html_url": "https://github.com/issue/5", "body": issue_body(item)}])
        self.assertFalse(second.create_issue(item)["created"])
        second.request.assert_called_once()

    def test_release_must_be_published(self):
        client = GitHub("ldohgfsdu/changjietu", "test-token")
        for bad in [
            {"tag_name": "v0.2.8", "draft": True, "html_url": "url"},
            {"tag_name": "v0.2.8", "prerelease": True, "html_url": "url"},
            {"tag_name": "other", "html_url": "url"},
        ]:
            client.request = Mock(return_value=bad)
            with self.assertRaises(ValueError):
                client.release("v0.2.8")


if __name__ == "__main__":
    unittest.main()
