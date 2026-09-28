# 第 14 掌溯源证据

`docs/ch14-验收记录.md` 引用的原始产物。事实源：Spring AI Alibaba **1.1.2.2** / Spring AI **1.1.2**。

| 文件 | 对应标准 | 说明 |
|------|----------|------|
| `01-locate-interceptToolCall.txt` | #1 | 定位脚本输出：谁实现了 `interceptToolCall`（模块@版本 + 文件:行号） |
| `02-locate-chainToolInterceptors.txt` | #1 | 调用者：`InterceptorChain#chainToolInterceptors` 的位置 |
| `03-locate-retry-loop.txt` | #1 | 重试循环 `while (attempt <= maxRetries)` 的位置 |
| `06-source-excerpts.txt` | #1 | 三段源码摘录（重试循环 / 错误拦截器 / 链组装顺序），带真实行号 |
| `04-git-history-ToolRetryInterceptor.json` | #2 | 该文件在 `v1.1.2.2` 之前的提交历史（GitHub API 原始响应） |
| `05-git-history-ToolErrorInterceptor.json` | #2 | 错误拦截器的提交历史（晚 4 天引入） |
| `07-tag-vs-main-diff.txt` | #2 | tag 版本与 main 的逐行差异：main 已把「非成功响应」纳入重试 |
| `08-minimal-reproduction.txt` | #3 | 最小复现的真实测试输出（3 个断言，3.4 秒） |

## 复算方式

```powershell
# 1) 拉源码（脚本会自己从 Maven Central 下 *-sources.jar 并缓存到 .source-cache/，该目录不入库）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\locate-source.ps1 -Symbol interceptToolCall

# 2) 跑最小复现
./mvnw -B -ntp -pl digital-human test -Dtest=ToolRetrySemanticsTest
```

> 版本号在 `scripts/locate-source.ps1` 里是**显式常量**：升级依赖时它会跟着改，
> 否则脚本给出的行号就不是你跑的那份字节码——这正是本掌要防的「结论随分支漂移」。
