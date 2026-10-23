#!/usr/bin/env python3
"""校验独立安全报告：漏洞只告警，执行错误和不完整报告必须失败。"""

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import sys


def trivy_counts(report):
    """统计扫描结果；拒绝错误对象或缺少扫描结果的文件。"""
    if (not isinstance(report, dict) or report.get("error") is not None
            or report.get("SchemaVersion") != 2
            or not isinstance(report.get("ArtifactName"), str)
            or not isinstance(report.get("Results"), list)):
        raise ValueError("Trivy 报告缺少有效的扫描结果")
    counts = Counter({level: 0 for level in ("UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL")})
    for result in report["Results"]:
        if not isinstance(result, dict):
            raise ValueError("Trivy 扫描目标格式错误")
        vulnerabilities = result.get("Vulnerabilities", [])
        if not isinstance(vulnerabilities, list):
            raise ValueError("Trivy 漏洞列表格式错误")
        for vulnerability in vulnerabilities:
            if (not isinstance(vulnerability, dict)
                    or not isinstance(vulnerability.get("VulnerabilityID"), str)
                    or vulnerability.get("Severity") not in counts):
                raise ValueError("Trivy 漏洞条目缺少有效的编号或严重等级")
            counts[vulnerability["Severity"]] += 1
    return counts


def npm_counts(report, status):
    """退出码 1 本身不能证明发现漏洞，必须同时核对有效的审计结构。"""
    if (not isinstance(report, dict) or report.get("error") is not None
            or report.get("auditReportVersion") != 2
            or not isinstance(report.get("vulnerabilities"), dict)
            or not isinstance(report.get("metadata"), dict)):
        raise ValueError("npm 审计失败或报告结构不完整")
    counts = report["metadata"].get("vulnerabilities")
    levels = ("info", "low", "moderate", "high", "critical")
    if not isinstance(counts, dict) or any(
        type(counts.get(level)) is not int or counts[level] < 0
        for level in (*levels, "total")
    ):
        raise ValueError("npm 审计缺少有效的漏洞计数")
    total = sum(counts[level] for level in levels)
    if counts["total"] != total or total != len(report["vulnerabilities"]):
        raise ValueError("npm 审计漏洞计数与报告内容不一致")
    actual = Counter()
    for vulnerability in report["vulnerabilities"].values():
        if not isinstance(vulnerability, dict) or vulnerability.get("severity") not in levels:
            raise ValueError("npm 审计漏洞条目缺少有效的严重等级")
        actual[vulnerability["severity"]] += 1
    if any(actual[level] != counts[level] for level in levels):
        raise ValueError("npm 审计严重等级统计与漏洞条目不一致")
    # 命令使用 audit-level=info：无漏洞应退出 0，发现任意等级漏洞应退出 1。
    if status != (1 if total else 0):
        raise ValueError("npm 审计退出码与漏洞报告不一致，可能发生执行错误")
    return Counter({level.upper(): counts[level] for level in levels})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("trivy", "npm"))
    parser.add_argument("report", type=Path)
    parser.add_argument("source", type=Path)
    parser.add_argument("--status-file", type=Path)
    args = parser.parse_args()
    try:
        report = json.loads(args.report.read_text(encoding="utf-8"))
        source = args.source.read_text(encoding="utf-8").strip()
        if not source:
            raise ValueError("安全报告缺少镜像摘要或源码提交号")
        if args.kind == "npm":
            if args.status_file is None:
                raise ValueError("npm 审计缺少退出码文件")
            counts = npm_counts(report, int(args.status_file.read_text(encoding="utf-8").strip()))
        else:
            counts = trivy_counts(report)
        total = sum(counts.values())
        # 只输出受控的统计信息；原始漏洞细节完整保存在 artifact 中。
        summary = (f"### {args.kind} 安全检查\n\n检查对象：\n\n```text\n{source}\n```\n\n"
                   f"漏洞总数：{total}；HIGH：{counts['HIGH']}；CRITICAL：{counts['CRITICAL']}。\n\n"
                   "发现项仅供维护评估，不阻断镜像发布；详细信息见原始 JSON 报告。\n")
        print(summary)
        if total:
            print(f"::warning::安全检查发现 {total} 项漏洞，其中 HIGH {counts['HIGH']} 项、"
                  f"CRITICAL {counts['CRITICAL']} 项；请结合实际利用条件评估。")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as output:
                output.write(summary + "\n")
    except (OSError, ValueError, TypeError) as error:
        print(f"安全报告处理失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
