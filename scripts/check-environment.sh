#!/usr/bin/env bash
# Read-only check. Does not print connected device identifiers or account data.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
task_java_home="${JAVA_HOME:-}"
if [[ -z "$task_java_home" && -x /usr/libexec/java_home ]]; then
  task_java_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
task_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Library/Android/sdk}}"

if [[ ! -x "$task_java_home/bin/java" ]]; then
  echo 'FAIL: JDK unavailable. Set JAVA_HOME to a supported JDK.' >&2
  exit 1
fi
"$task_java_home/bin/java" -version 2>&1 | head -n 1
if [[ ! -d "$task_sdk_root/platforms" || ! -x "$task_sdk_root/platform-tools/adb" ]]; then
  echo 'FAIL: Android SDK platform or adb unavailable. Set ANDROID_HOME.' >&2
  exit 1
fi
for task_platform in "$task_sdk_root"/platforms/*; do
  [[ -d "$task_platform" ]] && printf 'SDK platform: %s\n' "${task_platform##*/}"
done
for task_build_tools in "$task_sdk_root"/build-tools/*; do
  [[ -d "$task_build_tools" ]] && printf 'Build tools: %s\n' "${task_build_tools##*/}"
done
if [[ -f "$project_root/gradlew" ]]; then
  echo 'Gradle wrapper: present'
else
  echo 'Gradle wrapper: not present yet'
fi
if task_device_state="$("$task_sdk_root/platform-tools/adb" get-state 2>/dev/null)" && [[ "$task_device_state" == device ]]; then
  echo 'USB device: connected and authorized'
else
  echo 'USB device: unavailable, unauthorized, or more than one device connected'
  [[ "${1:-}" != --require-device ]] || exit 1
fi
echo 'Environment check complete. This does not verify APK compilation.'
