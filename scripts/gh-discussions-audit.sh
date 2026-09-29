#!/usr/bin/env bash
# 第 04 保：Discussions 只读巡检
#
# 本脚本**只读**：不含任何 mutation，不创建/不标记/不关闭/不锁定讨论。
# 写操作（标记答案、Issue 转 Discussion、锁定）全部走 Web 人工——
# gh 的 discussion 子命令在多个版本上仍不稳定，把写操作脚本化会把不稳定性带进治理链路。
#
# 输出：
#   1) 分类概览（名称 + 是否可标记答案）
#   2) 所有「可标记答案」分类下、仍未标记答案的开放讨论：分类 / 编号 / 标题 / 评论数 / URL
# 用途：主持人每个工作日跑一次，用来核「48 小时内首次响应」这条承诺。
#
# 用法：bash scripts/gh-discussions-audit.sh
#
# 实现说明：GitHub GraphQL 的 DiscussionCategory 上**没有** discussions 字段
# （早期资料里常见 `discussionCategories { discussions {...} }` 的写法，实测报
#  `Field 'discussions' doesn't exist on type 'DiscussionCategory'`）。
# 正确做法是走顶层 `repository.discussions`，用 categoryId 过滤、再按分类归组。

set -euo pipefail

OWNER="${OWNER:-lifuchun522}"
REPO_NAME="${REPO_NAME:-springaialibabapractice}"

# 前置：确认 Discussions 已开启。否则「没有未回答问题」会被误读成「社区很健康」。
enabled="$(gh api graphql -f query='
query($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) { hasDiscussionsEnabled }
}' -f owner="$OWNER" -f name="$REPO_NAME" --jq '.data.repository.hasDiscussionsEnabled')"

if [ "$enabled" != "true" ]; then
  echo "[gh04] 失败：$OWNER/$REPO_NAME 的 Discussions 未开启，巡检无意义。" >&2
  echo "        开启路径：Settings → General → Features → Discussions。" >&2
  exit 1
fi

echo "[gh04] Discussions 已开启。分类概览："
gh api graphql -f query='
query($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) {
    discussionCategories(first: 20) { nodes { id name isAnswerable } }
  }
}' -f owner="$OWNER" -f name="$REPO_NAME" --jq '
.data.repository.discussionCategories.nodes[]
| "  - \(.name)\t可标记答案=\(.isAnswerable)\t\(.id)"'

echo "[gh04] 未标记答案的开放问答讨论（分类 / 编号 / 标题 / 评论数 / URL）："
gh api graphql -f query='
query($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) {
    discussions(first: 50, states: [OPEN], orderBy: {field: UPDATED_AT, direction: DESC}) {
      nodes {
        number title url isAnswered updatedAt
        comments { totalCount }
        category { name isAnswerable }
      }
    }
  }
}' -f owner="$OWNER" -f name="$REPO_NAME" --jq '
.data.repository.discussions.nodes[]
| select(.category.isAnswerable)
| select(.isAnswered == false)
| "[\(.category.name)] #\(.number) \(.title)\n    评论 \(.comments.totalCount) 条，最后更新 \(.updatedAt)\n    \(.url)"'

echo "[gh04] 巡检结束。上面为空 = 可标记答案的分类下没有未回答的开放讨论。"
