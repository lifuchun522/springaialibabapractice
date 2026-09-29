#!/usr/bin/env bash
# 第 12 保：月报生成（口径固定，可跨月对照）
#
# 原则：表头与指标名**不随月份变化**。某项缺失时注明「缺（原因）」而不是删掉那一行——
# 删行会让相邻两个月的月报无法对照，复盘就退化成「读一读印象」。
#
# 只读：只做 GET 与本地写文件。
#
# 用法：
#   bash scripts/gh-insights-report.sh
#   MONTH=2026-09 bash scripts/gh-insights-report.sh

set -uo pipefail

REPO="${REPO:-lifuchun522/springaialibabapractice}"
MONTH="${MONTH:-$(date -u +%Y-%m)}"
OUT_DIR="${OUT_DIR:-docs/github-ops/metrics}"
RAW_DIR="${OUT_DIR}/raw"
REPORT="${OUT_DIR}/${MONTH}.md"
JQ_DIR="${JQ_DIR:-scripts/jq}"

mkdir -p "$OUT_DIR"

# jq 表达式尽量内联；含双引号的表达式落成 scripts/jq/*.jq 再读，
# 绕开「PowerShell / 跨函数传参剥掉双引号」这个实测坑（见 scripts/jq/open-issues.jq 注释）。
JQ_OPEN_ISSUES="$(cat "${JQ_DIR}/open-issues.jq")"
JQ_FAILED_RUNS="$(cat "${JQ_DIR}/failed-runs.jq")"

metric() {
  # $1 端点后段   $2 jq 表达式   $3 失败时的说明
  # 这里必须显式判断「输出是不是一个数字」：
  # 用 `[ -n "$out" ]` 会把空数组的 length=0 也当成有效值，但同时把「有效值 0」和
  # 「命令失败」混在一起（两者都表现为空/零），复盘时无法区分。要求命中 ^[0-9]+$ 才能两者分开。
  local out
  if out="$(gh api "repos/${REPO}/$1" --jq "$2" 2>/dev/null)" \
    && printf '%s' "$out" | grep -Eq '^[0-9]+$'; then
    printf '%s' "$out"
  else
    printf '缺（%s）' "$3"
  fi
}

{
  echo "# ${MONTH} Insights 复盘"
  echo
  echo "> 数据窗口：GitHub Traffic **仅保留最近 14 天，按 UTC 天聚合**（GitHub 侧固定，不可调）。"
  echo "> 生成命令：\`bash scripts/gh-insights-report.sh\`；原始快照见 \`metrics/raw/\`。"
  echo
  echo "## 一、核心指标"
  echo
  echo "| 指标 | 值 |"
  echo "| --- | --- |"
  printf '| 14 天访问次数 | %s |\n' "$(metric "traffic/views" '.count' '端点无返回，可能仓库刚公开')"
  printf '| 独立访客 | %s |\n' "$(metric "traffic/views" '.uniques' '端点无返回')"
  printf '| 14 天克隆次数 | %s |\n' "$(metric "traffic/clones" '.count' '端点无返回')"
  printf '| 独立克隆者 | %s |\n' "$(metric "traffic/clones" '.uniques' '端点无返回')"
  echo
  echo "## 二、协作信号"
  echo
  echo "| 指标 | 值 |"
  echo "| --- | --- |"
  printf '| 累计 Release 数 | %s |\n' "$(metric "releases?per_page=100" 'length' '无 Release（尚未发布软件版本）')"
  printf '| Issue 总数（含已关闭） | %s |\n' "$(metric "issues?state=all&per_page=100" 'length' '端点无返回')"
  printf '| 开放 Issue 数 | %s |\n' "$(metric "issues?state=all&per_page=100" "$JQ_OPEN_ISSUES" '端点无返回')"
  printf '| PR 总数（含已合并） | %s |\n' "$(metric "pulls?state=all&per_page=100" 'length' '端点无返回')"
  printf '| Contributors 数 | %s |\n' "$(metric "contributors?per_page=100" 'length' '端点无返回')"
  printf '| 最近 100 次 Actions 运行中失败数（不含 cancelled） | %s |\n' "$(metric "actions/runs?per_page=100" "$JQ_FAILED_RUNS" '端点无返回')"
  echo
  echo "## 三、流量来源（Top 5）"
  echo
  echo "| 来源 | 次数 | 独立访客 |"
  echo "| --- | --- | --- |"
  if ! gh api "repos/${REPO}/traffic/popular/referrers" \
      --jq '.[:5][] | "| \(.referrer) | \(.count) | \(.uniques) |"' 2>/dev/null; then
    echo "| 缺 | 缺（referrers 端点无返回） | 缺 |"
  fi
  echo
  echo "## 四、被访问最多的内容（Top 5）"
  echo
  echo "| 路径 | 访问次数 | 独立访客 |"
  echo "| --- | --- | --- |"
  if ! gh api "repos/${REPO}/traffic/popular/paths" \
      --jq '.[:5][] | "| \(.path) | \(.count) | \(.uniques) |"' 2>/dev/null; then
    echo "| 缺 | 缺（paths 端点无返回） | 缺 |"
  fi
  echo
  echo "## 五、原始快照清单"
  echo
  if [ -d "$RAW_DIR" ]; then
    ls -1 "$RAW_DIR" | sed 's/^/- `/; s/$/`/'
  else
    echo "- 缺（raw 目录不存在，请先跑 \`scripts/gh-insights-snapshot.sh\`）"
  fi
  echo
  echo "## 六、指标 → 判断 → 动作"
  echo
  echo "> 指标只作为判断输入，**不作为考核目标**。本节必须写出一条明确动作，否则这个月的复盘没有产出。"
  echo
  echo "| 观察到的 | 判断 | 下个月的动作 |"
  echo "| --- | --- | --- |"
  echo "| （读上面数据后填写） | | |"
  echo
  echo "### 口径说明（跨月不变）"
  echo
  echo "- Traffic 四项指标固定为：14 天访问次数、独立访客、14 天克隆次数、独立克隆者；"
  echo "- 「访问高但克隆为 0」= README 没能把人送到快速开始；"
  echo "- 「克隆有但 Issue/PR 为 0」= 只被当资料下载，没进社区，属于第 13 保的漏斗问题；"
  echo "- 长期为零的指标：**砍掉对应工作**，而不是继续维护。"
} >"$REPORT"

echo "[gh12] 月报 -> ${REPORT}（$(wc -l <"$REPORT") 行）"
