# 后端 Dockerfile - 多阶段构建，依赖层与源码层分离
# 仅使用 Docker 原生分层缓存，禁止 # syntax=docker/dockerfile:* 语法

ARG DOCKER_REGISTRY=
FROM ${DOCKER_REGISTRY}maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# ---- 依赖层（仅 pom.xml / settings.xml 变化才重新下载依赖）----
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn compile -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true -B

# ---- 源码层（仅业务源码变化才重新编译，不触发依赖下载）----
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

FROM ${DOCKER_REGISTRY}eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
