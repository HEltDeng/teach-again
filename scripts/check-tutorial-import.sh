#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
task_java_home="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
task_android_home="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
task_android_jar="$task_android_home/platforms/android-36.1/android.jar"
task_classes="$(mktemp -d "${TMPDIR:-/tmp}/teach-again-checks.XXXXXX")"
trap 'rm -rf "$task_classes"' EXIT
"$task_java_home/bin/javac" -encoding UTF-8 -cp "$task_android_jar" -d "$task_classes" \
  "$project_root/app/src/main/java/com/teachagain/app/Tutorial.java" \
  "$project_root/app/src/main/java/com/teachagain/app/TutorialImporter.java" \
  "$project_root/app/src/main/java/com/teachagain/app/StepTemplates.java" \
  "$project_root/app/src/main/java/com/teachagain/app/ai/ChatClient.java" \
  "$project_root/app/src/main/java/com/teachagain/app/ai/StepGenerator.java" \
  "$project_root/app/src/main/java/com/teachagain/app/TutorialRepository.java" \
  "$project_root/app/src/main/java/com/teachagain/app/TutorialRuleResolver.java" \
  "$project_root/scripts/tests/TutorialImportChecks.java"
"$task_java_home/bin/java" -cp "$task_classes:$task_android_jar" TutorialImportChecks
