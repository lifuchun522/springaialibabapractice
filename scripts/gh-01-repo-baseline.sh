#!/usr/bin/env bash
# 第 01 保：仓库建制（About 三件套 + Topics + Features 开关 + 基线快照）
#
# 用途：把仓库门面与基础制度一次立起来，并留下可 diff、可复现的 JSON 快照。
# 幂等：gh repo edit 与 topics PUT 均为整体写入语义，重复执行结果一致。
# 失败：缺 admin 权限时以非零退出，并提示改用 Web 手册路径（不静默失败）。
#
# 用法：
#   bash scripts/gh-01-repo-baseline.sh                # 写入 + 采快照
#   DRY_RUN=1 bash scripts/gh-01-repo-baseline.sh      # 只校验不写入
#   TOPICS_OVERRIDE='中文标签' bash scripts/gh-01-repo-baseline.sh   # 负向自测：应被拦下

set -euo pipefail

OWNER_REPO="${OWNER_REPO:-lifuchun522/springaialibabapractice}"
HOMEPAGE="${HOMEPAGE:-https://lifuchun522.github.io/springaialibabapractice/}"
DESCRIPTION="${DESCRIPTION:-Spring AI Alibaba 中文实战与数字人示例：18 掌实战代码、验收证据与运维治理}"
# 六个英文关键词，覆盖搜索意图；任何中文或截断项都会被下面的红线校验拦下。
TOPICS_DEFAULT="spring-ai-alibaba,java,agent,rag,mcp,digital-human"
TOPICS="${TOPICS_OVERRIDE:-$TOPICS_DEFAULT}"
OUT_DIR="${OUT_DIR:-docs/github-ops}"
SNAPSHOT="${OUT_DIR}/01-repo-baseline.json"
DRY_RUN="${DRY_RUN:-0}"

# ---------------------------------------------------------------- 红线校验
# 规则：Topics 必须是小写英文连字符，不允许中文、不允许下划线/空格/大写。
# 2026-09 实测本仓库曾存在截断项 spring-ai-ali，就是漏了这道校验的产物。
validate_topics() {
  local bad=0 name
  IFS=',' read -ra arr <<<"$TOPICS"
  if [ "${#arr[@]}" -lt 6 ]; then
    echo "红线：Topics 少于 6 个（当前 ${#arr[@]} 个）" >&2
    bad=1
  fi
  for name in "${arr[@]}"; do
    if ! printf '%s' "$name" | grep -Eq '^[a-z0-9]+(-[a-z0-9]+)*$'; then
      echo "红线：非法 Topics 项 '${name}'（只允许小写英文与连字符，禁止中文/大写/下划线/空格）" >&2
      bad=1
    fi
  done
  # 已知历史截断项，显式点名，避免它又被写回来。
  for name in "${arr[@]}"; do
    case "$name" in
      spring-ai-ali)
        echo "红线：命中历史截断项 'spring-ai-ali'，应为 'spring-ai-alibaba'" >&2
        bad=1
        ;;
    esac
  done
  [ "$bad" -eq 0 ] || return 1
}

echo "[gh01] 校验 Topics（${TOPICS}）"
validate_topics

if [ "$DRY_RUN" = "1" ]; then
  echo "[gh01] DRY_RUN=1，校验通过，未写入远端。"
  exit 0
fi

mkdir -p "$OUT_DIR"

# ------------------------------------------------------- About 三件套 + Features
# Features 取舍依据：会不会运营。不运营的能力一律关闭，把承诺变少变真。
#   Issues      开（有模板 + 首次响应承诺）
#   Discussions 开（第 04 保有五类分流与 48 小时承诺）
#   Wiki        关（无运营动作）
#   Projects    关（项目级只用第 09 保的作战盘；仓库级页签不运营）
#
# 注意：本机 gh 2.63 的 gh repo edit 只有 --enable-* 系列，没有 --disable-* 开关；
# 关闭要用 `--enable-xxx=false` 的布尔写法（gh 官方文档示例即此形式）。
if ! gh repo edit "$OWNER_REPO" \
  --description "$DESCRIPTION" \
  --homepage "$HOMEPAGE" \
  --enable-issues=true \
  --enable-discussions=true \
  --enable-wiki=false \
  --enable-projects=false; then
  echo "[gh01] 失败：写入仓库元数据需要 admin 权限。请改用 Web 路径（Settings → General / About 齿轮），" >&2
  echo "        或执行 gh auth refresh -h github.com -s repo，然后重跑本脚本。" >&2
  exit 1
fi

# ------------------------------------------------------------------- Topics
# PUT /topics 是整体替换语义，天然幂等；重复执行不会追加重复项。
# 用 Python 组装 JSON 再经 --input - 传入，避免 shell 数组参数在 Windows/macOS 上的转义差异。
python3 - "$TOPICS" <<'PY' | gh api --method PUT "repos/${OWNER_REPO}/topics" \
  -H "Accept: application/vnd.github+json" --input - >/dev/null
import json, sys
names = [t for t in sys.argv[1].split(",") if t]
print(json.dumps({"names": names}))
PY

# ------------------------------------------------------------------- 快照
gh repo view "$OWNER_REPO" \
  --json name,description,homepageUrl,repositoryTopics,visibility,defaultBranchRef,hasIssuesEnabled,hasDiscussionsEnabled,hasWikiEnabled,hasProjectsEnabled \
  >"$SNAPSHOT"

echo "[gh01] 基线快照已写入 $SNAPSHOT"

# --------------------------------------------------- 快照字段非空率自检（100% 才算过）
python3 - "$SNAPSHOT" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as fh:
    data = json.load(fh)
required = ["name", "description", "homepageUrl", "repositoryTopics", "visibility", "defaultBranchRef"]
missing = [k for k in required if not data.get(k)]
topics = [t["name"] for t in data.get("repositoryTopics") or []]
print("[gh01] 快照字段非空率：%d/%d" % (len(required) - len(missing), len(required)))
print("[gh01] Topics：%s" % ", ".join(topics))
if missing:
    print("[gh01] 失败：以下字段为空 -> %s" % ", ".join(missing), file=sys.stderr)
    sys.exit(1)
if not data.get("hasIssuesEnabled"):
    print("[gh01] 失败：Issues 关闭了，与第 01 保取舍不符", file=sys.stderr)
    sys.exit(1)
if data.get("hasWikiEnabled") or data.get("hasProjectsEnabled"):
    print("[gh01] 失败：Wiki/Projects 仍为开启，与「不运营的能力一律关闭」不符", file=sys.stderr)
    sys.exit(1)
print("[gh01] 通过：字段非空率 100%%，Issues 开 / Wiki 关 / Projects 关")
PY
