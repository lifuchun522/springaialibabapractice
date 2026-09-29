#!/usr/bin/env bash
# 第 12 保：GitHub Traffic 与协作信号原始快照
#
# 为什么「第一次采集就必须落盘」：GitHub Traffic（views / clones / referrers / paths）
# **只保留最近 14 天，且按 UTC 天聚合**。Insights 是看板不是账本——
# 缺了落盘层，三个月后回看只剩一句「好像涨过一批人」。
#
# 只读：本脚本只做 GET 与本地写文件，不改任何 GitHub 状态。
# 缺失处理：某个端点取不到（例如仓库刚公开、或权限不足）时，写一个带 note 的占位 JSON，
#          而不是留空文件——空文件会被下游误读成「这个月流量为零」。
#
# 用法：
#   bash scripts/gh-insights-snapshot.sh              # 落到当月前缀
#   MONTH=2026-09 bash scripts/gh-insights-snapshot.sh

set -uo pipefail

REPO="${REPO:-lifuchun522/springaialibabapractice}"
MONTH="${MONTH:-$(date -u +%Y-%m)}"
OUT_DIR="${OUT_DIR:-docs/github-ops/metrics}"
RAW_DIR="${OUT_DIR}/raw"

mkdir -p "$RAW_DIR"
echo "[gh12] 采集窗口口径：Traffic 仅保留最近 14 天，按 UTC 天聚合（窗口由 GitHub 决定，不可调）"

snapshot() {
  local name="$1" endpoint="$2"
  local target="${RAW_DIR}/${MONTH}-${name}.json"
  local attempt=1
  while [ "$attempt" -le 3 ]; do
    # 注意两点：
    # 1) 必须直接重定向到文件 —— 不能用「捕获 stdout 再重试」的封装，否则重定向拿到 0 字节文件（第 05 保已踩过）；
    # 2) endpoint 里已经带上查询串（如 ?per_page=100）。不要把查询串拆成第二个参数传：
    #    未加引号的 `$extra` 即使为空也会产生一个空参数，gh 会报 `accepts 1 arg(s), received 2`。
    if gh api "${endpoint}" --jq '.' >"$target" 2>/tmp/gh12.err; then
      if [ -s "$target" ]; then
        echo "[gh12]   ✓ ${name} -> ${target} ($(wc -c <"$target") 字节)"
        return 0
      fi
    fi
    echo "[gh12]   ! ${name} 第 ${attempt} 次失败：$(tail -1 /tmp/gh12.err 2>/dev/null)" >&2
    attempt=$((attempt + 1))
    [ "$attempt" -le 3 ] && sleep $((attempt * 2))
  done
  # 不静默失败：写占位并注明原因，让月报能把「缺」和「零」区分开
  printf '{"_note":"本端点采集失败（重试 3 次），数据缺失","_endpoint":"%s","_month":"%s"}\n' \
    "$endpoint" "$MONTH" >"$target"
  echo "[gh12]   ✗ ${name} 采集失败，已写占位（缺，不是零）" >&2
  return 1
}

echo "[gh12] Traffic 四端点（14 天窗口）："
snapshot views     "repos/${REPO}/traffic/views"
snapshot clones    "repos/${REPO}/traffic/clones"
snapshot referrers "repos/${REPO}/traffic/popular/referrers"
snapshot paths     "repos/${REPO}/traffic/popular/paths"

echo "[gh12] 协作信号五端点（用于对照流量是否转成了协作）："
snapshot releases     "repos/${REPO}/releases?per_page=100"
snapshot issues       "repos/${REPO}/issues?state=all&per_page=100"
snapshot pulls        "repos/${REPO}/pulls?state=all&per_page=100&sort=updated"
snapshot contributors "repos/${REPO}/contributors?per_page=100"
snapshot runs         "repos/${REPO}/actions/runs?per_page=100"

echo "[gh12] 原始快照 -> ${RAW_DIR}"
ls -1 "$RAW_DIR" | sed 's/^/[gh12]   /'
