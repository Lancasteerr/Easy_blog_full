"""验证漏洞告警与执行失败的边界，不依赖真实 registry 或漏洞数据库。"""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class SecurityReportTests(unittest.TestCase):
    def run_report(self, kind, report, status=0, source="test-source"):
        """通过真实命令行入口验证退出码、告警和运行摘要。"""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_path = root / "report.json"
            report_path.write_text(report if isinstance(report, str) else json.dumps(report), encoding="utf-8")
            (root / "source.txt").write_text(source, encoding="utf-8")
            (root / "status.txt").write_text(str(status), encoding="utf-8")
            summary = root / "summary.md"
            command = [sys.executable, str(Path(__file__).with_name("summarize-security.py")),
                       kind, str(report_path), str(root / "source.txt")]
            if kind == "npm":
                command += ["--status-file", str(root / "status.txt")]
            result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                                    env={**os.environ, "GITHUB_STEP_SUMMARY": str(summary),
                                         "PYTHONIOENCODING": "utf-8"}, check=False)
            return result, summary.read_text(encoding="utf-8") if summary.exists() else ""

    @staticmethod
    def npm_report(severity=None):
        counts = {level: 0 for level in ("info", "low", "moderate", "high", "critical", "total")}
        vulnerabilities = {}
        if severity:
            counts[severity] = counts["total"] = 1
            vulnerabilities["example-package"] = {"severity": severity}
        return {"auditReportVersion": 2, "vulnerabilities": vulnerabilities,
                "metadata": {"vulnerabilities": counts}}

    @staticmethod
    def trivy_report(vulnerabilities=None):
        result = {"Target": "usr/bin/pebble"}
        if vulnerabilities is not None:
            result["Vulnerabilities"] = vulnerabilities
        return {"SchemaVersion": 2, "ArtifactName": "example@sha256:1234", "Results": [result]}

    def test_npm_clean_report_succeeds_without_warning(self):
        result, summary = self.run_report("npm", self.npm_report())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("::warning::", result.stdout)
        self.assertIn("HIGH：0；CRITICAL：0", summary)

    def test_npm_all_severities_are_non_blocking(self):
        for severity in ("info", "low", "moderate", "high", "critical"):
            with self.subTest(severity=severity):
                result, summary = self.run_report("npm", self.npm_report(severity), status=1)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("::warning::", result.stdout)
                self.assertIn("漏洞总数：1", summary)

    def test_npm_registry_error_is_not_a_vulnerability(self):
        report = {**self.npm_report(), "error": {"code": "E503", "summary": "registry unavailable"}}
        result, summary = self.run_report("npm", report, status=1)
        self.assertEqual(result.returncode, 1)
        self.assertNotIn("::warning::", result.stdout)
        self.assertEqual(summary, "")

    def test_npm_exit_code_must_match_report(self):
        for report, status in ((self.npm_report(), 1), (self.npm_report("high"), 0),
                               (self.npm_report("critical"), 2)):
            with self.subTest(status=status, report=report):
                result, summary = self.run_report("npm", report, status=status)
                self.assertEqual(result.returncode, 1)
                self.assertEqual(summary, "")

    def test_npm_rejects_missing_invalid_or_inconsistent_counts(self):
        invalid_reports = [{}, {"auditReportVersion": 2}]
        for value in (None, -1, True, "1"):
            report = self.npm_report()
            report["metadata"]["vulnerabilities"]["high"] = value
            invalid_reports.append(report)
        report = self.npm_report("high")
        report["vulnerabilities"] = {}
        invalid_reports.append(report)
        report = self.npm_report("high")
        report["vulnerabilities"]["example-package"]["severity"] = "low"
        invalid_reports.append(report)
        report = self.npm_report("high")
        report["vulnerabilities"]["example-package"] = {}
        invalid_reports.append(report)
        for report in invalid_reports:
            with self.subTest(report=report):
                result, summary = self.run_report("npm", report, status=1)
                self.assertEqual(result.returncode, 1)
                self.assertEqual(summary, "")

    def test_trivy_clean_target_without_vulnerability_field_is_valid(self):
        result, summary = self.run_report("trivy", self.trivy_report())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("::warning::", result.stdout)
        self.assertIn("漏洞总数：0", summary)

    def test_trivy_preserves_all_severities_and_reports_high_critical(self):
        vulnerabilities = [{"VulnerabilityID": f"CVE-EXAMPLE-{index}", "Severity": severity}
                           for index, severity in enumerate(("UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL"))]
        result, summary = self.run_report("trivy", self.trivy_report(vulnerabilities),
                                          source="example:prod\nexample@sha256:1234")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("::warning::", result.stdout)
        self.assertIn("漏洞总数：5；HIGH：1；CRITICAL：1", summary)
        self.assertIn("example@sha256:1234", summary)

    def test_trivy_rejects_database_errors_and_malformed_results(self):
        invalid_reports = [{"error": "database download failed"}, {},
                           self.trivy_report({"unexpected": "object"}),
                           self.trivy_report([{"VulnerabilityID": "CVE-EXAMPLE", "Severity": "INVALID"}])]
        for report in invalid_reports:
            with self.subTest(report=report):
                result, summary = self.run_report("trivy", report)
                self.assertEqual(result.returncode, 1)
                self.assertNotIn("::warning::", result.stdout)
                self.assertEqual(summary, "")

    def test_broken_json_and_missing_source_fail(self):
        for kind in ("npm", "trivy"):
            with self.subTest(kind=kind):
                result, summary = self.run_report(kind, "{broken")
                self.assertEqual(result.returncode, 1)
                self.assertEqual(summary, "")
        result, summary = self.run_report("npm", self.npm_report(), source="")
        self.assertEqual(result.returncode, 1)
        self.assertEqual(summary, "")


if __name__ == "__main__":
    unittest.main()
