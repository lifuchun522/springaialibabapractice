#!/usr/bin/env bash
# 第 03 保：中文标签幂等同步
#
# 真源：docs/github-ops/labels.txt（格式 名称|颜色|说明）
# 幂等：gh label create --force —— 已存在则更新颜色与说明，不产生重复标签。
# 断言：官方默认标签 good first issue / help wanted 必须仍在，且不得被改名。
# 只读校验：与 .github/ISSUE_TEMPLATE/*.yml 的 labels 引用逐字符比对，防止「表单引用了清单里没有的标签」。
#
# 用法：
#   bash scripts/gh-labels.sh                    # 同步标签 + 自检
#   CHECK_ONLY=1 bash scripts/gh-labels.sh       # 只自检不写入

set -euo pipefail

REPO="${REPO:-lifuchun522/springaialibabapractice}"
MANIFEST="${MANIFEST:-docs/github-ops/labels.txt}"
CHECK_ONLY="${CHECK_ONLY:-0}"

[ -f "$MANIFEST" ] || { echo "[gh03] 失败：找不到清单 $MANIFEST" >&2; exit 1; }

# 重试封装：实测在部分网络环境下 gh 会偶发 `net/http: TLS handshake timeout`，
# 这类抖动不该被当成「配置失败」。重试 3 次，每次间隔递增。
gh_retry() {
  local attempt=1 out
  while [ "$attempt" -le 3 ]; do
    if out="$("$@" 2>&1)"; then
      return 0
    fi
    echo "[gh03] 第 ${attempt} 次执行失败：$*" >&2
    printf '%s\n' "$out" | tail -3 >&2
    attempt=$((attempt + 1))
    [ "$attempt" -le 3 ] && sleep $((attempt * 2))
  done
  echo "[gh03] 失败：重试 3 次后仍未成功：$*" >&2
  return 1
}

created=0
updated=0
while IFS='|' read -r name color desc; do
  # 跳过空行与注释行
  case "${name:-}" in ''|\#*) continue ;; esac
  if ! printf '%s' "$color" | grep -Eq '^[0-9a-fA-F]{6}$'; then
    echo "[gh03] 失败：'$name' 的颜色 '${color}' 不是 6 位十六进制" >&2
    exit 1
  fi
  if [ "$CHECK_ONLY" = "1" ]; then
    continue
  fi
  # 先查再建：用于统计「新建」与「更新」两类，仅作日志用，写入本身是同一条幂等命令。
  if gh label list --repo "$REPO" --limit 200 --json name --jq '.[].name' | grep -Fxq "$name"; then
    updated=$((updated + 1))
  else
    created=$((created + 1))
  fi
  gh_retry gh label create "$name" --repo "$REPO" --color "$color" --description "$desc" --force
done <"$MANIFEST"

if [ "$CHECK_ONLY" != "1" ]; then
  echo "[gh03] 标签同步完成：新建 ${created} 个，更新 ${updated} 个"
fi

# ---------------------------------------------------------------- 官方默认标签断言
echo "[gh03] 检查官方默认标签是否仍在："
if ! gh label list --repo "$REPO" --limit 200 --json name --jq '.[].name' \
  | grep -E '^(good first issue|help wanted)$'; then
  echo "[gh03] 警告：官方默认标签缺失！第 13 保的任务池依赖 good first issue，请勿改名或删除。" >&2
  exit 1
fi

# ------------------------------------------------- 表单标签引用与清单一致性（只读）
echo "[gh03] 检查 Issue 表单引用的标签是否都在清单里："
python3 - "$MANIFEST" <<'PY'
import glob
import os
import re
import sys

manifest = sys.argv[1]
declared = set()
with open(manifest, encoding="utf-8") as fh:
    for line in fh:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) >= 3:
            declared.add(parts[0])

missing = []
checked = 0
for path in sorted(glob.glob(".github/ISSUE_TEMPLATE/*.yml")):
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    for m in re.finditer(r"^labels:\s*\[(.*?)\]\s*$", text, re.M):
        for raw in m.group(1).split(","):
            name = raw.strip().strip("'\"")
            if not name:
                continue
            checked += 1
            if name not in declared:
                missing.append("%s -> %s" % (os.path.basename(path), name))

print("[gh03] 表单标签引用 %d 处" % checked)
if missing:
    print("[gh03] 失败：以下标签被表单引用但不在 labels.txt 中：", file=sys.stderr)
    for item in missing:
        print("        " + item, file=sys.stderr)
    sys.exit(1)
print("[gh03] 通过：表单引用的标签全部存在于清单中")
PY
