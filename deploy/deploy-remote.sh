#!/usr/bin/env bash
# 在 CI runner 上执行：把 .env 同步到部署服务器，然后拉取镜像并滚动更新，最后做健康检查。
#
# 为什么 .env 用 scp 而不是 ssh 命令行传参：命令行参数会出现在服务器的进程列表里，
# 密码/密钥等于贴在墙上。runner 是一次性的，落一份临时文件再传是更安全的做法。
#
# 需要的环境变量（全部由 release.yml 从 Secrets / Variables 注入）：
#   DEPLOY_HOST DEPLOY_USER DEPLOY_PORT DEPLOY_DIR
#   VERSION DOCKER_REGISTRY DOCKER_NAMESPACE DOCKER_USERNAME DOCKER_PASSWORD
#   APP_DIGITAL_HUMAN_IMAGE APP_MCP_IMAGE APP_PORT MCP_PORT
#   APP_DEEPSEEK_API_KEY APP_DB_URL APP_DB_USER APP_DB_PASSWORD
#   APP_MCP_DB_URL APP_MCP_DB_USER APP_MCP_DB_PASSWORD
set -euo pipefail

: "${DEPLOY_HOST:?缺少 DEPLOY_HOST}"
: "${DEPLOY_USER:?缺少 DEPLOY_USER}"
: "${DEPLOY_DIR:?缺少 DEPLOY_DIR}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"

SSH=(ssh -i ~/.ssh/id_deploy -p "${DEPLOY_PORT}" -o StrictHostKeyChecking=accept-new "${DEPLOY_USER}@${DEPLOY_HOST}")
SCP=(scp -i ~/.ssh/id_deploy -P "${DEPLOY_PORT}" -o StrictHostKeyChecking=accept-new)

ENV_FILE="$(mktemp)"
trap 'rm -f "${ENV_FILE}"' EXIT

cat > "${ENV_FILE}" <<EOF
# 由 CI 生成（$(date -u +%Y-%m-%dT%H:%M:%SZ)），版本 ${VERSION}
VERSION=${VERSION}
DOCKER_REGISTRY=${DOCKER_REGISTRY}
DOCKER_NAMESPACE=${DOCKER_NAMESPACE}
APP_DIGITAL_HUMAN_IMAGE=${APP_DIGITAL_HUMAN_IMAGE}
APP_MCP_IMAGE=${APP_MCP_IMAGE}
APP_PORT=${APP_PORT:-8080}
MCP_PORT=${MCP_PORT:-8081}
APP_DEEPSEEK_API_KEY=${APP_DEEPSEEK_API_KEY}
APP_DB_URL=${APP_DB_URL}
APP_DB_USER=${APP_DB_USER}
APP_DB_PASSWORD=${APP_DB_PASSWORD}
APP_MCP_DB_URL=${APP_MCP_DB_URL}
APP_MCP_DB_USER=${APP_MCP_DB_USER}
APP_MCP_DB_PASSWORD=${APP_MCP_DB_PASSWORD}
EOF

echo "→ 同步 .env 到 ${DEPLOY_HOST}:${DEPLOY_DIR}"
"${SCP[@]}" "${ENV_FILE}" "${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_DIR}/.env"

echo "→ 登录镜像仓库并滚动更新"
"${SSH[@]}" bash -s <<EOF
set -euo pipefail
cd "${DEPLOY_DIR}"
echo "${DOCKER_PASSWORD}" | docker login "${DOCKER_REGISTRY}" -u "${DOCKER_USERNAME}" --password-stdin
docker compose pull
docker compose up -d --remove-orphans
docker image prune -f --filter "until=168h" >/dev/null 2>&1 || true
EOF

echo "→ 健康检查"
for i in $(seq 1 30); do
  if "${SSH[@]}" "curl -fsS --max-time 5 http://127.0.0.1:${APP_PORT:-8080}/actuator/health" >/dev/null 2>&1; then
    echo "✓ 应用健康检查通过（第 ${i} 次尝试）"
    "${SSH[@]}" "cd '${DEPLOY_DIR}' && docker compose ps"
    exit 0
  fi
  sleep 5
done

echo "✗ 健康检查超时，输出最近日志" >&2
"${SSH[@]}" "cd '${DEPLOY_DIR}' && docker compose logs --tail=80" >&2 || true
exit 1
