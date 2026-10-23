# 多阶段构建：编译阶段需要完整 JDK 和 Maven，运行阶段只保留 JRE。
FROM maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584 AS build

# 固定镜像中的 Maven，并使用校验后的官方包完整替换旧 JDK，避免浮动标签漏掉安全补丁。
COPY docker/prepare-temurin.sh /tmp/prepare-temurin.sh
RUN sh /tmp/prepare-temurin.sh jdk /opt/java/patched-jdk \
    && sh /tmp/prepare-temurin.sh jre /opt/java/patched-jre \
    && rm -rf /opt/java/openjdk \
    && mv /opt/java/patched-jdk /opt/java/openjdk \
    && java -version

WORKDIR /build

# 使用 Maven 镜像源，避免 Docker 构建时直连 Maven Central 超时或 TLS 握手失败。
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 先复制 pom.xml 预下载依赖，提高重复构建速度。
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre@sha256:e04fa72be13b257a59fdec6dc14af78c8f80f035d3c1ed4e03e45552a231d4c8

# 运行镜像保留原发行版和证书入口脚本，完整替换 Java 目录，最终仅携带 JRE。
RUN rm -rf /opt/java/openjdk
COPY --from=build /opt/java/patched-jre /opt/java/openjdk
RUN grep -Fx 'JAVA_RUNTIME_VERSION="17.0.21+9"' /opt/java/openjdk/release \
    && java -version

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
