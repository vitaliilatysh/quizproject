import unittest
from verify_release import verdict, verify

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
                verify("owner/repo", [SHA], "main", ["ci.yml"], 0, fetch=lambda *args: rows)

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

if __name__ == "__main__":
    unittest.main()
