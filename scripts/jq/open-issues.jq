# 第 12 保：统计「开放 Issue 数」
#
# 为什么单独放一个文件（而不是写成 gh api 的内联 --jq 参数）：
# 实测本机在 PowerShell / 跨函数传参时，参数里的双引号会被剥掉，
# `select(.state=="open")` 会变成 `select(.state==open)`，jq 报 `function not defined: open/0`。
# 把表达式落成文件（gh api --jq "$(cat file)" 或 jq -f）可以完全绕开这一层。
#
# 用法：gh api "repos/{owner}/{repo}/issues?state=all&per_page=100" --jq "$(cat scripts/jq/open-issues.jq)"
map(select(.state == "open")) | length
