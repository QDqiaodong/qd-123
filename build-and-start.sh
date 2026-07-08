#!/usr/bin/env bash
# 厂区安防布线配件存放分区归类系统 - 构建启动 + 自检脚本
# 功能：构建并启动容器 -> 等待服务就绪 -> 校验 127.0.0.1 与 localhost 一致性 -> 打印前端访问地址

set -e

cd "$(dirname "$0")"

# 读取 .env 中的端口配置（正确 source，逐变量导出）
set -a
. ./.env
set +a

FRONTEND_PORT="${FRONTEND_PORT:-3008}"
BACKEND_PORT="${BACKEND_PORT:-8088}"

echo "============================================================"
echo "  厂区安防布线配件存放分区归类系统 - 开始构建启动"
echo "============================================================"

# 先停止本项目已有容器，释放本项目占用的端口（保留数据卷）
docker compose down >/dev/null 2>&1 || true

# 端口占用预检（仅检查宿主机端口，若被其他进程占用则报错退出，禁止自动换端口）
check_port() {
  local port=$1
  local name=$2
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[错误] 端口 ${port}（${name}）已被其他进程占用："
    lsof -nP -iTCP:"$port" -sTCP:LISTEN
    echo "请释放该端口后重试，本脚本不会自动更换端口。"
    exit 1
  fi
}

echo "[1/4] 端口占用预检..."
check_port "${FRONTEND_PORT}" "前端"
check_port "${BACKEND_PORT}" "后端"
check_port "${MYSQL_PORT:-3309}" "MySQL"
check_port "${REDIS_PORT:-6380}" "Redis"
echo "    -> 端口预检通过"

echo "[2/4] 构建并启动容器（docker compose up --build -d）..."
docker compose up --build -d

echo "[3/4] 等待后端服务就绪..."
MAX_WAIT=120
WAITED=0
until curl -s "http://127.0.0.1:${BACKEND_PORT}/api/zone-tag/list" >/dev/null 2>&1; do
  sleep 2
  WAITED=$((WAITED + 2))
  if [ "$WAITED" -ge "$MAX_WAIT" ]; then
    echo "[错误] 后端服务在 ${MAX_WAIT}s 内未就绪，请检查日志：docker compose logs backend"
    exit 1
  fi
done
echo "    -> 后端服务已就绪（耗时 ${WAITED}s）"

echo "[4/4] 校验 127.0.0.1 与 localhost 访问一致性..."
sleep 2
IP_RESP=$(curl -sS "http://127.0.0.1:${FRONTEND_PORT}" | head -n 5)
LOCAL_RESP=$(curl -sS "http://localhost:${FRONTEND_PORT}" | head -n 5)

if [ "$IP_RESP" != "$LOCAL_RESP" ]; then
  echo "[错误] 127.0.0.1 与 localhost 返回内容不一致，请检查容器端口绑定！"
  exit 1
fi
echo "    -> 一致性校验通过"

echo ""
echo "============================================================"
echo "  ✅ 构建启动成功！"
echo "------------------------------------------------------------"
echo "  前端访问地址：  http://localhost:${FRONTEND_PORT}"
echo "                  http://127.0.0.1:${FRONTEND_PORT}"
echo "  后端接口地址：  http://localhost:${BACKEND_PORT}/api"
echo "  MySQL 端口：    ${MYSQL_PORT:-3309}"
echo "  Redis 端口：    ${REDIS_PORT:-6380}"
echo "============================================================"
