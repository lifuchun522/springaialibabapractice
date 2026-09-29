#!/usr/bin/env bash
# 第 13 保：运营指标按日归档（四类）
#
# 与第 12 保的分工：
#   第 12 保（gh-insights-*）—— 按月、九端点、面向「月报复盘」
#   第 13 保（本脚本）     —— 按日、四类、面向「每周节奏 + 任务池健康度」
#   两者共用 docs/github-ops/metrics/ 目录，文件名前缀区分（YYYY-MM vs YYYY-MM-DD）。
#
# 只读：只做 GET 与本地写文件，不改任何 GitHub 状态。
#
# 用法：bash scripts/gh-ops-metrics.sh

set -uo pipefail

OWNER="${OWNER:-lifuchun522}"
REPO_NAME="${REPO_NAME:-springaialibabapractice}"
STAMP="${STAMP:-$(date -u +%Y-%m-%d)}"
DIR="${DIR:-docs/github-ops/metrics}"
mkdir -p "$DIR"

fetch() {
  local name="$1" cmd="$2"
  local target="$DIR/$STAMP-$name"
  local attempt=1
  while [ "$attempt" -le 3 ]; do
    # 直接重定向到文件；不用「捕获 stdout 再重试」的封装（会让重定向拿到空文件）
    if eval "$cmd" >"$target" 2>/tmp/gh13.err; then
      if [ -s "$target" ]; then
        echo "[gh13]   ✓ $name ($(wc -c <"$target") 字节)"
        return 0
      fi
    fi
    echo "[gh13]   ! $name 第 ${attempt} 次失败：$(tail -1 /tmp/gh13.err 2>/dev/null)" >&2
    attempt=$((attempt + 1))
    [ "$attempt" -le 3 ] && sleep $((attempt * 2))
  done
  printf '{"_note":"采集失败（重试 3 次），数据缺失","_target":"%s","_date":"%s"}\n' \
    "$name" "$STAMP" >"$target"
  echo "[gh13]   ✗ $name 采集失败，已写占位（缺，不是零）" >&2
  return 1
}

echo "[gh13] 归档日期（UTC）：$STAMP"
echo "[gh13] 目录：$DIR"

echo "[gh13] 1/4 访问量与克隆量（Traffic，14 天窗口）"
fetch "views.json"    "gh api \"repos/$OWNER/$REPO_NAME/traffic/views\" --jq '.'"
fetch "clones.json"   "gh api \"repos/$OWNER/$REPO_NAME/traffic/clones\" --jq '.'"

echo "[gh13] 2/4 任务池：开放 good first issue"
fetch "gfi.json" "gh issue list -R $OWNER/$REPO_NAME --label 'good first issue' --state open --json number,title,createdAt,url"

echo "[gh13] 3/4 版本：最近 10 个 Release"
fetch "releases.json" "gh release list -R $OWNER/$REPO_NAME --limit 10 --json tagName,publishedAt,isLatest"

echo "[gh13] 4/4 社区：讨论数与未答问答数"
fetch "discussions.json" "gh api graphql -f query='query(\$o:String!,\$n:String!){repository(owner:\$o,name:\$n){hasDiscussionsEnabled discussions(first:100){totalCount nodes{number isAnswered category{name isAnswerable}}}}}' -f o=$OWNER -f n=$REPO_NAME --jq '.'"

echo "[gh13] 写入完成："
ls -1 "$DIR" | grep "^$STAMP" | sed "s|^|[gh13]   $DIR/|"
