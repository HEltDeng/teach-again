#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
task_java_home="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
task_classes="$(mktemp -d "${TMPDIR:-/tmp}/teach-screen-checks.XXXXXX")"
trap 'rm -rf "$task_classes"' EXIT
"$task_java_home/bin/javac" -encoding UTF-8 -d "$task_classes" \
  "$project_root/app/src/main/java/com/teachagain/app/StepMatchRules.java" \
  "$project_root/scripts/tests/ScreenMatchChecks.java"
"$task_java_home/bin/java" -cp "$task_classes" ScreenMatchChecks
