#!/usr/bin/env bash
# 轻量校验：只编译教程相关的纯 Java 类并跑规则/导入检查，
# 不触发完整 Android 构建（秒级完成），适合保存时快速反馈。
set -euo pipefail

project_root="$(cd "$(dirname "$0")/.." && pwd)"
java_home="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"

if [ -z "${java_home:-}" ] || [ ! -x "$java_home/bin/javac" ]; then
  echo "跳过教程校验：未找到 JDK 17" >&2
  exit 0
fi

out="$(mktemp -d "${TMPDIR:-/tmp}/teach-validate.XXXXXX")"
trap 'rm -rf "$out"' EXIT

# 编译纯逻辑类（无 Android 依赖）+ 测试
"$java_home/bin/javac" -encoding UTF-8 -d "$out" \
  "$project_root/app/src/main/java/com/teachagain/app/StepMatchRules.java" \
  "$project_root/scripts/tests/ScreenMatchChecks.java" 2>&1

"$java_home/bin/java" -cp "$out" ScreenMatchChecks

# 导入/导出往返检查（若脚本存在）
if [ -f "$project_root/scripts/check-tutorial-import.sh" ]; then
  bash "$project_root/scripts/check-tutorial-import.sh"
fi

echo "教程校验通过 ✅"
