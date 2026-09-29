#!/usr/bin/env bash
# 第 09 保：把治理条目同步进作战盘（幂等）
#
# 复用既有项目 #1「降 SpringAI 阿里 十八掌 · 进度」。
# 幂等策略：先 item-list 拉出全部条目的 content.number，命中则跳过，不重复添加。
#
# 与既有脚本的分工：
#   scripts/sync-github-project.py  —— 18 掌的章节/分支/tag/PR 对应关系（章节侧的排期真源）
#   scripts/gh-09-project-sync.sh   —— 十三保治理条目（治理侧的排期真源）
#   两者共用同一张盘，靠 Chapter 字段的「治理」选项区分，不各建一张。
#
# 用法：
#   bash scripts/gh-09-project-sync.sh 57 58 61     # 指定 Issue/PR 编号
#   bash scripts/gh-09-project-sync.sh --from-github-ops   # 自动取 docs/github-ops 索引里的 Issue 编号

set -uo pipefail

OWNER="${OWNER:-lifuchun522}"
PROJECT_NUMBER="${PROJECT_NUMBER:-1}"
PROJECT_ID="${PROJECT_ID:-PVT_kwHOE6THGM4Bk5wB}"
REPO="${REPO:-lifuchun522/springaialibabapractice}"
OUT_DIR="${OUT_DIR:-docs/github-ops}"

if [ "$#" -eq 0 ]; then
  echo "用法：bash scripts/gh-09-project-sync.sh <Issue或PR编号> [...]" >&2
  echo "      bash scripts/gh-09-project-sync.sh --from-github-ops" >&2
  exit 2
fi

numbers=()
if [ "$1" = "--from-github-ops" ]; then
  # 从治理文档索引里抓「#数字」形态的引用（本目录的文档会把 Issue 编号写在正文里）
  while read -r n; do numbers+=("$n"); done < <(
    grep -rhoE '#[0-9]{1,4}' docs/github-ops/*.md 2>/dev/null | tr -d '#' | sort -un
  )
  echo "[gh09] 从 docs/github-ops 抓到候选编号：${numbers[*]:-<空>}"
else
  numbers=("$@")
fi

[ "${#numbers[@]}" -gt 0 ] || { echo "[gh09] 没有要同步的编号" >&2; exit 1; }

echo "[gh09] 读取作战盘现有条目……"
existing="$(gh project item-list "$PROJECT_NUMBER" --owner "$OWNER" --limit 200 --format json \
  --jq '.items[] | .content.number // empty' 2>/dev/null | sort -u)"
echo "[gh09] 现有条目编号：$(printf '%s ' $existing)"

added=0
skipped=0
for n in "${numbers[@]}"; do
  if printf '%s\n' "$existing" | grep -Fxq "$n"; then
    echo "[gh09] #${n} 已存在，跳过"
    skipped=$((skipped + 1))
    continue
  fi
  # 先确认这个编号属于本仓库（防止把外部 URL 的编号加进来）
  if ! gh api "repos/${REPO}/issues/${n}" --jq '.number' >/dev/null 2>&1; then
    echo "[gh09] #${n} 不属于 ${REPO}，跳过" >&2
    continue
  fi
  url="https://github.com/${REPO}/issues/${n}"
  if gh project item-add "$PROJECT_NUMBER" --owner "$OWNER" --url "$url" --format json \
      --jq '.id' >/dev/null 2>&1; then
    echo "[gh09] 已加入：${url}"
    added=$((added + 1))
  else
    echo "[gh09] 加入失败：${url}" >&2
  fi
done

echo "[gh09] 本次新增 ${added} 条，跳过 ${skipped} 条"

echo "[gh09] 导出作战盘条目快照……"
mkdir -p "$OUT_DIR"
gh project item-list "$PROJECT_NUMBER" --owner "$OWNER" --limit 200 --format json --jq '.' \
  >"${OUT_DIR}/project-items.json"
echo "[gh09] 条目快照 -> ${OUT_DIR}/project-items.json（$(wc -c <"${OUT_DIR}/project-items.json") 字节）"

python3 - "${OUT_DIR}/project-items.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as fh:
    data = json.load(fh)
items = data.get("items", [])
print("[gh09] 作战盘共 %d 条条目" % len(items))
by_kind = {}
for it in items:
    kind = (it.get("kind") or {}).get("name") or "<未填 Kind>"
    by_kind[kind] = by_kind.get(kind, 0) + 1
for k, v in sorted(by_kind.items()):
    print("        %-10s %d 条" % (k, v))
PY
