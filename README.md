# 厂区安防布线配件存放分区归类系统

## 项目简介

工厂安防辅材档案与库房分区归类系统，支持配件建档、自定义分区标签、配件分区调整和按标签筛选。

布线方案管理支持按关键词、启用状态筛选，并可“导出当前筛选结果”为 CSV（UTF-8 BOM，Excel 可直接打开）。导出列为方案名称、适用场景、启用状态、配件名称、所属分区、需求数量、规格单位；同一方案的配件按库房分区排序号排列，方案信息逐行重复以保留方案边界；空结果、超长名称、中文文件名与导出中的重复点击均有明确处理（接口 `GET /api/wiring-plan/export`）。方案详情弹窗的配件分区与导出共用同一套排序（分区排序号升序、同分区按配件名称、未分配分区最后），刷新或编辑后顺序保持一致。

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
