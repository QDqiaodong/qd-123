# 厂区安防布线配件存放分区归类系统

## 项目简介

工厂安防辅材档案与库房分区归类系统，支持配件建档、自定义分区标签、配件分区调整和按标签筛选。

## 技术栈

- 前端：Vue 3、Vite 5、Element Plus、Axios
- 后端：Spring Boot 3、JDK 17、MyBatis-Plus、Maven
- 数据：MySQL 8、Redis 7
- 部署：Docker Compose、Nginx

## 端口说明

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3123 或 http://127.0.0.1:3123 |
| 后端 API | http://127.0.0.1:8123/api |
| MySQL | 127.0.0.1:3423 |
| Redis | 127.0.0.1:6423 |

端口来自根目录 `.env`，Docker 端口只绑定 `127.0.0.1`。

## 启动方式

```bash
cd qd-123
docker compose up -d --build
```

本地拆分验证：

```bash
cd backend
mvn compile -q

cd ../frontend
npm ci
npm run build
```

## Docker 构建说明

后端、前端、MySQL、Redis 由 Docker Compose 统一编排。首次或依赖变化后执行：

```bash
docker compose up -d --build
docker compose ps
```

## 常见问题

- Docker 拉取镜像慢：调整 `.env` 的 `DOCKER_REGISTRY` 或 Docker daemon 镜像加速。
- 数据库初始化异常：确认 `mysql/init` 脚本存在且数据卷状态符合预期。
- 页面接口失败：build 链路通过后，继续检查后端 API、代理和容器健康状态。
