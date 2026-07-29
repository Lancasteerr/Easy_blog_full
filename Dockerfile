# 多阶段构建：编译阶段需要完整 JDK 和 Maven，运行阶段只保留 JRE。
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 使用 Maven 镜像源，避免 Docker 构建时直连 Maven Central 超时或 TLS 握手失败。
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 先复制 pom.xml 预下载依赖，提高重复构建速度。
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

# 使用非 root 用户运行服务，并预创建上传文件目录。
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser \
    && mkdir -p /app/blog-storage \
    && chown -R appuser:appuser /app

COPY --from=build /build/target/*.jar /app/app.jar

ENV SERVER_PORT=5090
ENV BLOG_STORAGE_ROOT=/app/blog-storage

EXPOSE 5090

USER appuser

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
