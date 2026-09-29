#!/usr/bin/env bash
# 第 09 保：GitHub Projects 作战盘字段（幂等补齐 + 导出快照）
#
# 复用既有的项目 #1「降 SpringAI 阿里 十八掌 · 进度」（PVT_kwHOE6THGM4Bk5wB），**不新建作战盘**。
# 理由：新建会造出第二张排期真源，与「一处真源」冲突；而且既有脚本
# scripts/sync-github-project.py 已经把 18 掌的章节/分支/tag/PR 对应关系同步进去了。
#
# 字段策略（命名保持**英文**，与既有字段一致，避免同义字段重复）：
#   已存在且沿用：Status（状态）/ Priority（优先级）/ Start date / Target date
#   本次新增：Chapter（章节，含「治理」）/ Kind（类型：章节/文档/实验/治理）
#
# 幂等：先 field-list 查重，存在则跳过，不存在才建。
#
# 用法：
#   bash scripts/gh-09-project-fields.sh            # 补齐字段 + 导出快照
#   DRY_RUN=1 bash scripts/gh-09-project-fields.sh   # 只打印将要创建的字段

set -uo pipefail

OWNER="${OWNER:-lifuchun522}"
PROJECT_NUMBER="${PROJECT_NUMBER:-1}"
PROJECT_ID="${PROJECT_ID:-PVT_kwHOE6THGM4Bk5wB}"
OUT_DIR="${OUT_DIR:-docs/github-ops}"
SNAPSHOT="${OUT_DIR}/project-fields.json"
DRY_RUN="${DRY_RUN:-0}"

# --single-select-options 是 **逗号分隔的字符串**，不是 JSON 数组。
# 实测：传 `'["第1章",...]'` 会报
#   invalid argument ... parse error on line 1, column 2: bare " in non-quoted-field
# 这个坑值得记下来——gh 的该参数文档只写 `strings`，而其他 `--xxx` 多数接受 JSON。
CH_OPTIONS="第1章,第2章,第3章,第4章,第5章,第6章,第7章,第8章,第9章,第10章,第11章,第12章,第13章,第14章,第15章,第16章,第17章,第18章,治理"
KIND_OPTIONS="章节,文档,实验,治理"

echo "[gh09] 项目：${OWNER} #${PROJECT_NUMBER} (${PROJECT_ID})"
echo "[gh09] 现有字段："
gh project field-list "$PROJECT_NUMBER" --owner "$OWNER" --format json \
  --jq '.fields[] | "  - \(.name)\t\(.type)"'

ensure_field() {
  local name="$1" options="$2"
  if gh project field-list "$PROJECT_NUMBER" --owner "$OWNER" --format json \
      --jq '.fields[].name' | grep -Fxq "$name"; then
    echo "[gh09] 已存在，跳过：${name}"
    return 0
  fi
  if [ "$DRY_RUN" = "1" ]; then
    echo "[gh09] DRY_RUN：将创建单选项字段 ${name} = ${options}"
    return 0
  fi
  echo "[gh09] 创建单选项字段：${name}"
  local attempt=1
  while [ "$attempt" -le 3 ]; do
    if gh project field-create "$PROJECT_NUMBER" --owner "$OWNER" \
        --name "$name" --data-type SINGLE_SELECT --single-select-options "$options" >/dev/null; then
      return 0
    fi
    echo "[gh09] 第 ${attempt} 次失败，重试……" >&2
    attempt=$((attempt + 1))
    [ "$attempt" -le 3 ] && sleep $((attempt * 2))
  done
  echo "[gh09] 失败：字段 ${name} 创建失败（重试 3 次）" >&2
  return 1
}

ensure_field "Chapter" "$CH_OPTIONS"
ensure_field "Kind" "$KIND_OPTIONS"

if [ "$DRY_RUN" = "1" ]; then
  exit 0
fi

# ------------------------------------------------------------------ 导出快照
mkdir -p "$OUT_DIR"
# 用 GraphQL 导出（与教程口径一致），带 options，便于审计字段取值集合。
gh api graphql -f query='
query($login: String!, $number: Int!) {
  user(login: $login) {
    projectV2(number: $number) {
      id
      title
      fields(first: 50) {
        nodes {
          __typename
          ... on ProjectV2FieldCommon { id name dataType }
          ... on ProjectV2SingleSelectField { options { id name } }
        }
      }
    }
  }
}' -F login="$OWNER" -F number="$PROJECT_NUMBER" --jq '.' >"$SNAPSHOT"
echo "[gh09] 字段快照 -> ${SNAPSHOT}（$(wc -c <"$SNAPSHOT") 字节）"

# ------------------------------------------------------------------ 读回断言
python3 - "$SNAPSHOT" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as fh:
    data = json.load(fh)
proj = data["data"]["user"]["projectV2"]
fields = {f["name"]: f for f in proj["fields"]["nodes"] if f.get("name")}
print("[gh09] 项目：%s" % proj["title"])
required = ["Status", "Priority", "Start date", "Target date", "Chapter", "Kind"]
missing = [n for n in required if n not in fields]
for name in required:
    f = fields.get(name)
    opts = [o["name"] for o in (f or {}).get("options", [])]
    print("  - %-12s %s %s" % (name, "OK " if f else "MISS", ("选项：%d 个" % len(opts)) if opts else ""))
if missing:
    print("[gh09] 失败：缺字段 %s" % ", ".join(missing), file=sys.stderr)
    sys.exit(1)
ch = [o["name"] for o in fields["Chapter"].get("options", [])]
if "治理" not in ch:
    print("[gh09] 失败：Chapter 字段缺「治理」选项", file=sys.stderr)
    sys.exit(1)
print("[gh09] 通过：6 个要件字段齐备，Chapter 含「治理」选项（共 %d 个）" % len(ch))
PY
