import io
import json
import os
import unittest
from unittest import mock
from urllib.parse import parse_qs, urlsplit

import verify_release
from verify_release import fetch_runs, verdict, verify

SHA = "a" * 40

def run(**changes):
    return dict({"id": 1, "head_sha": SHA, "head_branch": "main", "event": "push",
                 "status": "completed", "conclusion": "success"}, **changes)

class ReleaseGateTest(unittest.TestCase):
    def test_only_exact_commit_default_branch_push_counts(self):
        for changes in ({"head_sha": "b" * 40}, {"head_branch": "other"}, {"event": "pull_request"}):
            self.assertEqual("pending", verdict([run(**changes)], SHA, "main"))

    def test_latest_failure_overrides_old_success(self):
        self.assertEqual("failure", verdict([run(), run(id=2, conclusion="failure")], SHA, "main"))

    def test_latest_pending_overrides_old_success(self):
        self.assertEqual("pending", verdict([run(), run(id=2, status="in_progress")], SHA, "main"))

    def test_only_success_is_allowed(self):
        self.assertEqual("success", verdict([run()], SHA, "main"))
        for result in ("failure", "cancelled", "skipped", "neutral", "timed_out"):
            self.assertEqual("failure", verdict([run(conclusion=result)], SHA, "main"))

    def test_all_workflows_for_image_and_manifest_are_required(self):
        calls = []
        def fetch(repo, workflow, sha, branch):
            calls.append((workflow, sha))
            return [run(head_sha=sha)]
        verify("owner/repo", [SHA, "b" * 40], "main", ["ci.yml", "sonarqube.yml"], 0, fetch=fetch)
        self.assertEqual(4, len(calls))

    def test_missing_or_failed_run_blocks(self):
        for rows in ([], [run(conclusion="failure")]):
            with self.assertRaises(RuntimeError):
                verify("owner/repo", [SHA], "main", ["ci.yml"], 0, fetch=lambda *args, rows=rows: rows)

    def test_invalid_metadata_is_rejected_before_requests(self):
        for repo, sha in (("owner/repo", "main"), ("bad", SHA)):
            with self.assertRaises(ValueError):
                verify(repo, [sha], "main", ["ci.yml"], 0)

    def test_waits_for_running_check(self):
        rows = iter([[], [run()]])
        sleeps = []
        verify("owner/repo", [SHA], "main", ["ci.yml"], 20,
               fetch=lambda *args: next(rows), clock=lambda: 0, sleep=sleeps.append)
        self.assertEqual([10], sleeps)

    def test_api_error_is_not_treated_as_success(self):
        def unavailable(*args):
            raise OSError("GitHub unavailable")
        with self.assertRaises(OSError):
            verify("owner/repo", [SHA], "main", ["ci.yml"], 0, fetch=unavailable)

    def test_fetch_asks_github_for_push_runs_of_exactly_that_commit(self):
        seen = {}
        def urlopen(request, timeout):
            seen.update(url=request.full_url, auth=request.get_header("Authorization"), timeout=timeout)
            return io.BytesIO(json.dumps({"workflow_runs": [run()]}).encode())
        with mock.patch.dict(os.environ, {"GH_TOKEN": "test-token"}), mock.patch("urllib.request.urlopen", urlopen):
            runs = fetch_runs("owner/repo", "ci.yml", SHA, "main")
        url = urlsplit(seen["url"])
        self.assertEqual("/repos/owner/repo/actions/workflows/ci.yml/runs", url.path)
        query = parse_qs(url.query)
        self.assertEqual(([SHA], ["main"], ["push"]), (query["head_sha"], query["branch"], query["event"]))
        self.assertEqual("Bearer test-token", seen["auth"])
        self.assertEqual(30, seen["timeout"])
        self.assertEqual([run()], runs)

    def test_every_sha_and_workflow_on_the_command_line_is_checked(self):
        argv = ["verify_release.py", "--repo", "owner/repo", "--sha", SHA, "--sha", "b" * 40,
                "--branch", "main", "--workflow", "ci.yml", "--workflow", "container.yml"]
        with mock.patch("sys.argv", argv), mock.patch.object(verify_release, "verify") as checked:
            verify_release.main()
        checked.assert_called_once_with("owner/repo", [SHA, "b" * 40], "main", ["ci.yml", "container.yml"], 300)

if __name__ == "__main__":
    unittest.main()
