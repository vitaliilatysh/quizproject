"""Keep the production policy wired before any Droplet mutation."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ProductionWorkflowTest(unittest.TestCase):
    def test_production_gate_checks_signed_image_and_manifests_before_apply(self):
        workflow = (ROOT / ".github/workflows/deploy-digitalocean.yml").read_text(encoding="utf-8")
        steps = workflow.split("      - name: ")
        gate = next(step for step in steps if step.startswith("Require successful checks before production\n"))
        self.assertIn("if: env.DEPLOY_ENV == 'production'", gate)
        self.assertIn("GH_TOKEN: ${{ github.token }}", gate)
        self.assertIn("org.opencontainers.image.revision", gate)
        self.assertIn('"${DIGEST_REF}"', gate)
        self.assertIn("--repo \"${GITHUB_REPOSITORY}\" --branch master", gate)
        self.assertIn('--sha "${IMAGE_SHA}" --sha "${DEPLOY_SHA}"', gate)
        self.assertEqual(["ci.yml","sonarqube.yml","container.yml"], re.findall(r"--workflow (\S+)", gate))
        self.assertIn("  actions: read\n", workflow)
        self.assertLess(workflow.index("Verify the image signature"), workflow.index(gate))
        self.assertLess(workflow.index(gate), workflow.index("Deploy the verified image over SSH"))

    def test_staging_trigger_keeps_its_existing_trust_boundary(self):
        workflow = (ROOT / ".github/workflows/deploy-digitalocean.yml").read_text(encoding="utf-8")
        self.assertIn("github.event.workflow_run.event == 'push'", workflow)
        self.assertIn("github.event.workflow_run.head_repository.full_name == github.repository", workflow)
        self.assertIn("branches: [master]", workflow)

    def test_delivery_trigger_matches_the_publisher(self):
        delivery = (ROOT / ".github/workflows/container.yml").read_text(encoding="utf-8")
        name = re.search(r"^name: (.+)$", delivery, re.MULTILINE).group(1)
        workflow = (ROOT / ".github/workflows/deploy-digitalocean.yml").read_text(encoding="utf-8")
        self.assertIn('workflows: ["' + name + '"]', workflow)

    def test_ci_runs_the_policy_tests(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("python3 -B -m unittest discover -s .github/scripts -v", workflow)


    def test_digitalocean_uses_verified_ssh_and_opt_in_staging(self):
        workflow = (ROOT / ".github/workflows/deploy-digitalocean.yml").read_text(encoding="utf-8")
        self.assertNotIn("OCI_CLI_", workflow)
        self.assertNotIn("OKE_CLUSTER_OCID", workflow)
        self.assertNotIn("configure-kubectl-oke", workflow)
        self.assertIn("vars.DO_STAGING_ENABLED == 'true'", workflow)
        self.assertIn("secrets.DO_SSH_KEY", workflow)
        self.assertIn("secrets.DO_KNOWN_HOSTS", workflow)
        self.assertIn("cancel-in-progress: false", workflow)
        ship = (ROOT / "deploy/digitalocean/ship.sh").read_text(encoding="utf-8")
        self.assertIn("StrictHostKeyChecking=yes", ship)
        self.assertNotIn("ssh-keyscan", ship)


if __name__ == "__main__":
    unittest.main()
