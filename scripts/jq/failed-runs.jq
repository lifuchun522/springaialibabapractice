# 第 12 保：统计「最近 100 次 Actions 运行中的失败数」（排除被取消的运行）
#
# 排除 cancelled 的理由：并发取消是正常工作流行为（第 08 保给 ci.yml 配了 cancel-in-progress），
# 把它算成失败会让「失败数」这个指标长期虚高，复盘时就会误判 CI 健康度。
#
# 用法：gh api "repos/{owner}/{repo}/actions/runs?per_page=100" --jq "$(cat scripts/jq/failed-runs.jq)"
[.workflow_runs[] | select(.conclusion == "failure")] | length
