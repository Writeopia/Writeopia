#!/usr/bin/env python3
"""Reads the xcresult bundles given as arguments and writes, to the log and to the GitHub job
summary, how many tests passed and failed in each and which tests failed, with their messages.
Needs the xcresulttool of Xcode 16 or later."""

import json
import os
import subprocess
import sys


def read_summary(path):
    """The test summary of the bundle at path as a dict, or an error message."""
    completed = subprocess.run(
        ["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", path, "--compact"],
        capture_output=True,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        return None, completed.stderr.strip() or f"xcresulttool exited with {completed.returncode}"
    try:
        return json.loads(completed.stdout), None
    except json.JSONDecodeError as error:
        return None, f"unreadable summary: {error}"


def read_build_results(path):
    """The build results of the bundle at path (a build that failed before the tests), or None."""
    completed = subprocess.run(
        ["xcrun", "xcresulttool", "get", "build-results", "--path", path, "--compact"],
        capture_output=True,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        return None
    try:
        return json.loads(completed.stdout)
    except json.JSONDecodeError:
        return None


def markdown_cell(text):
    return text.replace("|", "\\|").replace("\r", "").replace("\n", "<br>")


def report(path):
    name = os.path.basename(path).removesuffix(".xcresult")
    data, error = read_summary(path)
    if data is None:
        build = read_build_results(path)
        if build is None:
            return [f"## {name}", "", f"Could not read the result bundle: {error}", ""]
        lines = [f"## ❌ {name}: build {build.get('status', 'failed')}", ""]
        for issue in build.get("errors", []):
            where = issue.get("sourceURL", "")
            lines.append(f"- **{issue.get('targetName', '')}**: {markdown_cell(issue.get('message', ''))} {where}".rstrip())
        return lines + [""]

    result = data.get("result", "unknown")
    mark = "✅" if result == "Passed" else "❌"
    lines = [
        f"## {mark} {name}: {result}",
        "",
        f"{data.get('passedTests', 0)} passed, {data.get('failedTests', 0)} failed, "
        f"{data.get('skippedTests', 0)} skipped, of {data.get('totalTestCount', 0)} tests",
        "",
    ]

    failures = data.get("testFailures", [])
    if failures:
        lines += ["| Test | Failure |", "| --- | --- |"]
        for failure in failures:
            test = f"{failure.get('targetName', '')} / {failure.get('testName', '')}"
            lines.append(f"| {markdown_cell(test)} | {markdown_cell(failure.get('failureText', ''))} |")
        lines.append("")

    return lines


def main(paths):
    if not paths:
        print("No result bundles to report.")
        return

    text = "\n".join(line for path in paths for line in report(path))
    print(text)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write(text + "\n")


if __name__ == "__main__":
    main(sys.argv[1:])
