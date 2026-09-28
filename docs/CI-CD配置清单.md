# 流水线配置清单（Key 与配置位置）

本文回答两个问题：**每个配置放在哪**、**每个 key 叫什么、给谁用**。
配完之后打 tag 即可跑完「构建 → 发布 jar → 推镜像 → 部署 → 钉钉通知」全链路。

## 零、已经确认的取值（你提供）

| 项 | 取值 |
|---|---|
| 镜像仓库 | `registry.us-west-1.aliyuncs.com` |
| 命名空间 | `platweb` |
| 数字人应用镜像 | `registry.us-west-1.aliyuncs.com/platweb/public` |
| MCP Server 镜像 | `registry.us-west-1.aliyuncs.com/platweb/public-mcp` |
| 仓库登录账号 | `505847426@qq.com` |
| 镜像 tag | `saa-YYYYMMDD`（当天构建的可读版本）+ `saa-YYYYMMDD-<sha7>`（唯一，部署用这个） |

> `saa-YYYYMMDD` 是**会移动的标签**：同一天再发一次就会被覆盖。
> 所以部署与回滚一律使用 `saa-YYYYMMDD-<sha7>`，可读标签只用来「一眼看出是哪天发过」。

## 一、四类配置分别在什么地方

| 配置类别 | 放在哪 | 谁能看到 | 说明 |
|---|---|---|---|
| 凭据（口令、私钥、webhook、模型 key） | GitHub → **Settings → Secrets and variables → Actions → Secrets** | 只有工作流运行时 | 写入后不可回读，只能覆盖 |
| 非敏感参数（地址、命名空间、端口、目录） | 同页面 → **Variables** | 所有人可读 | 便于在 PR 里讨论 |
| 运行期凭据（应用连库、模型 key） | 服务器 `$DEPLOY_DIR/.env`（**由 CI 生成**） | 服务器管理员 | 仓库与镜像里都不出现 |
| 消费方（谁读这些 key） | `.github/workflows/*.yml`、`deploy/*` | 所有人 | 每个 key 在文件里都有注释指向本文 |

## 二、Secrets 清单（12 个）

| Secret 名称 | 用途 | 谁提供 | 被哪里消费 |
|---|---|---|---|
| `MAVEN_REPO_USERNAME` | 发布 jar 到 Maven 仓库的账号 | **待你提供** | `release.yml` → `deploy/settings.xml` |
| `MAVEN_REPO_PASSWORD` | 同上，口令或访问令牌 | **待你提供** | 同上 |
| `DOCKER_PASSWORD` | 镜像仓库口令（阿里云容器镜像服务的固定密码） | **待你提供** | `release.yml` 登录、`deploy-remote.sh` 服务器登录 |
| `DEPLOY_HOST` | 部署服务器地址 | **待你提供** | `release.yml` deploy |
| `DEPLOY_USER` | 部署账号（建议专用账号，不用 root） | **待你提供** | 同上 |
| `DEPLOY_SSH_KEY` | 部署私钥全文（含 `-----BEGIN/END` 两行） | **待你提供** | 同上 |
| `DINGTALK_WEBHOOK` | 钉钉机器人 webhook | **待你提供** | `ci.yml` / `release.yml` → `deploy/notify-dingtalk.sh` |
| `DINGTALK_SECRET` | 钉钉加签密钥（安全设置选「加签」时必填；选「IP 白名单」可不填） | **待你提供** | 同上 |
| `APP_DEEPSEEK_API_KEY` | 应用运行时用的模型密钥 | **待你提供** | `deploy-remote.sh` 写入服务器 `.env` |
| `APP_DB_PASSWORD` | 数字人应用库口令 | **待你提供** | 同上 |
| `APP_MCP_DB_PASSWORD` | MCP Server 库口令 | **待你提供** | 同上 |
| `DOCKER_USERNAME` | 镜像仓库账号 | ✅ 已确认：`505847426@qq.com` | 见下方 Variables（账号不算敏感，放 Variables 更好排查） |

生成部署私钥：

```bash
ssh-keygen -t ed25519 -C "gh-actions-deploy" -f deploy_key
# deploy_key.pub 追加到服务器部署账号的 ~/.ssh/authorized_keys
gh secret set DEPLOY_SSH_KEY --repo lifuchun522/springaialibabapractice < deploy_key
```

## 三、Variables 清单

| Variable 名称 | 取值 | 用途 |
|---|---|---|
| `DOCKER_REGISTRY` | ✅ `registry.us-west-1.aliyuncs.com` | 镜像仓库域名 |
| `DOCKER_NAMESPACE` | ✅ `platweb` | 命名空间 |
| `DOCKER_REPO` | ✅ `public` | 数字人应用镜像仓库名 |
| `DOCKER_REPO_MCP` | ✅ `public-mcp` | MCP Server 镜像仓库名 |
| `DOCKER_USERNAME` | ✅ `505847426@qq.com` | 登录账号（口令在 Secrets） |
| `DOCKER_BASE_IMAGE` | **待你确认 tag**：`eclipse-temurin:21-jre-alpine`（默认） | 运行镜像基础镜像；**必须 JDK 21**（应用要求 21，17 的 JRE 跑不起来）。若你们的公共库有 21 的 JRE alpine 镜像，填它的完整地址 |
| `MAVEN_REPO_ID` | **待你提供**（如 `codeup`） | settings.xml 的 server id，必须与发布命令一致 |
| `MAVEN_REPO_URL` | **待你提供** | `mvn deploy` 目标地址 |
| `DEPLOY_DIR` | **待你提供**：如 `/opt/springaialibabapractice` | 服务器部署目录 |
| `DEPLOY_PORT` | 默认 `22` | SSH 端口 |
| `APP_PORT` | 默认 `8080` | 数字人应用对外端口 |
| `MCP_PORT` | 默认 `8081` | MCP Server 对外端口 |
| `APP_DB_URL` | **待你提供** | 数字人库 JDBC 地址（容器内连宿主机数据库写 `host.docker.internal`） |
| `APP_DB_USER` | **待你提供** | 数字人库账号 |
| `APP_MCP_DB_URL` | **待你提供** | MCP 库 JDBC 地址（默认库名 `digital_human_ext`） |
| `APP_MCP_DB_USER` | **待你提供** | MCP 库账号 |

## 四、服务器侧一次性准备

```bash
# 1) 部署账号与目录
useradd -m -s /bin/bash deploy || true
mkdir -p /opt/springaialibabapractice && chown deploy:deploy /opt/springaialibabapractice
# 2) 把 deploy_key.pub 写进部署账号的 authorized_keys
# 3) 装 docker 与 compose 插件（能用 docker compose 命令即可）
# 4) 建两个库（表结构不用手工建，应用启动时由 Flyway 迁移）
CREATE DATABASE digital_human     CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE digital_human_ext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

服务器目录（CI 同步前两个文件、生成第三个）：

```text
/opt/springaialibabapractice/
├── docker-compose.yml   ← CI 每次覆盖
├── .env.example         ← CI 每次覆盖（模板，供人工核对）
└── .env                 ← CI 生成，含运行期凭据（建议 chmod 600）
```

## 五、把配置写进 GitHub（可复制）

```bash
REPO=lifuchun522/springaialibabapractice

# --- Variables：已确认的 ---
gh variable set DOCKER_REGISTRY   --repo $REPO --body "registry.us-west-1.aliyuncs.com"
gh variable set DOCKER_NAMESPACE  --repo $REPO --body "platweb"
gh variable set DOCKER_REPO       --repo $REPO --body "public"
gh variable set DOCKER_REPO_MCP   --repo $REPO --body "public-mcp"
gh variable set DOCKER_USERNAME   --repo $REPO --body "505847426@qq.com"
gh variable set DOCKER_BASE_IMAGE --repo $REPO --body "eclipse-temurin:21-jre-alpine"

# --- Variables：待你填 ---
gh variable set MAVEN_REPO_ID     --repo $REPO --body "<你的 server id>"
gh variable set MAVEN_REPO_URL    --repo $REPO --body "<你的 Maven 仓库地址>"
gh variable set DEPLOY_DIR        --repo $REPO --body "/opt/springaialibabapractice"
gh variable set APP_DB_URL        --repo $REPO --body "<数字人库 JDBC>"
gh variable set APP_DB_USER       --repo $REPO --body "<数字人库账号>"
gh variable set APP_MCP_DB_URL    --repo $REPO --body "<MCP 库 JDBC>"
gh variable set APP_MCP_DB_USER   --repo $REPO --body "<MCP 库账号>"

# --- Secrets：值从 stdin 读，不进 shell 历史 ---
gh secret set MAVEN_REPO_USERNAME --repo $REPO
gh secret set MAVEN_REPO_PASSWORD --repo $REPO
gh secret set DOCKER_PASSWORD     --repo $REPO
gh secret set DEPLOY_HOST         --repo $REPO
gh secret set DEPLOY_USER         --repo $REPO
gh secret set DEPLOY_SSH_KEY      --repo $REPO < deploy_key
gh secret set DINGTALK_WEBHOOK    --repo $REPO
gh secret set DINGTALK_SECRET     --repo $REPO
gh secret set APP_DEEPSEEK_API_KEY --repo $REPO
gh secret set APP_DB_PASSWORD     --repo $REPO
gh secret set APP_MCP_DB_PASSWORD --repo $REPO
```

## 六、触发方式与回滚

| 动作 | 命令 / 操作 | 跑哪些阶段 |
|---|---|---|
| 日常提交 | push / PR | 只跑 `CI`（编译 + 单测 + 门禁，**不需要任何密钥**） |
| 正式发布 | `git tag v1.0.0 && git push origin v1.0.0` | 全链路：构建 → Maven → 镜像 → 部署 → 通知 |
| 只发镜像不部署 | Actions → Release → Run workflow → 勾 `skip_deploy` | 构建 → Maven → 镜像 → 通知 |
| 只部署不发 Maven | 同上 → 勾 `skip_maven` | 构建 → 镜像 → 部署 → 通知 |
| 回滚 | 服务器 `.env` 里 `VERSION=saa-<日期>-<旧 sha7>` 后 `docker compose up -d` | 只切镜像标签，不重建 |

## 七、本地已经验证过的部分（不依赖你的凭据）

流水线里最容易被写错的两段——**镜像构建**与**部署编排**——已经在本机用真实 Docker 跑通：

```text
docker build digital-human        → 574MB，成功
docker build digital-human-mcp    → 538MB，成功
docker compose up -d              → digital-human-mcp healthy，digital-human Up
curl :8080/actuator/health        → {"status":"UP"}
启动日志                          → MCP 远程工具已发现 1 个：showroom_query_availability
真实对话（容器内跑）               → HTTP 200「我是深圳展厅的数字人讲解员…」
```

过程中修掉一个真实问题：**容器默认解析不到 `host.docker.internal`**（Linux/多数 Docker 需要显式声明），
现在 compose 里两个服务都加了 `extra_hosts: ["host.docker.internal:host-gateway"]`，
这样数据库跑在宿主机时容器才连得上。

尚未验证（需要你的凭据或服务器）：Maven 发布、镜像推送到你们的仓库、SSH 部署、钉钉通知。
建议第一步先在 Actions 里跑一次 `skip_deploy=true` 的 Release，确认前两段；再加部署。

## 八、还需要你提供

| 项 | 需要的内容 |
|---|---|
| Maven 仓库 | 地址 URL、server id、账号、口令/令牌 |
| Docker 仓库 | 口令（固定密码）；以及**一个带 JDK 21 的基础镜像 tag**（若不用 Docker Hub 默认的 `eclipse-temurin:21-jre-alpine`） |
| 部署服务器 | 地址、SSH 端口、部署账号、公钥是否已就位；是否已有 MySQL 与两个库 |
| 钉钉机器人 | webhook；安全设置选「加签」（要 secret）还是「IP 白名单」（不用） |
| 运行期 | `DEEPSEEK_API_KEY`；两个库的地址与账号口令 |
