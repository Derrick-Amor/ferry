# ============================================================================
# 渡口 Ferry · 后端镜像（多阶段构建）
# ----------------------------------------------------------------------------
# 构建：docker build -t ferry-backend ./backend
# 或由 docker-compose.yml 统一编排
#
# 设计要点：
#   1. 分两阶段：构建阶段用完整 Maven + JDK，运行阶段只保留 JRE，
#      最终镜像不含源码与 Maven，体积从 ~700MB 降到 ~250MB。
#   2. 依赖层与源码层分离：先只 COPY pom.xml 跑一次 go-offline，
#      再 COPY src。这样只改 Java 代码时，依赖层缓存不会失效，
#      重复构建从数分钟降到十几秒。
#   3. 以非 root 用户 app 运行，符合容器最小权限原则。
#   4. 用 MaxRAMPercentage 而不是写死 -Xmx，让 JVM 按容器内存限额自动算堆。
# ============================================================================

# ---------------------------------------------------------------------------
# 阶段一：构建
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 0) Maven 镜像配置先落地：Maven Central 在部分网络下返回 403，
#    统一走阿里云公共仓库（详见 settings.xml 内注释）
COPY settings.xml /root/.m2/settings.xml

# 1) 先只拷 pom，把依赖下载独立成一层 —— 源码改动不会让这层缓存失效
COPY pom.xml ./
# go-offline 偶有插件元数据解析不全的情况，失败时回退到显式的依赖/插件解析，
# 两条路都依赖网络，因此不会掩盖真正的网络故障
RUN mvn -B dependency:go-offline \
    || mvn -B dependency:resolve dependency:resolve-plugins

# 2) 再拷源码构建；跳过测试（测试需要连数据库，镜像构建阶段不该跑）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# 阶段二：运行
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# 时区与编码：容器内 LocalDateTime.now() 需要正确时区，
# 中文注释与日志需要 UTF-8
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

# curl 仅用于容器 healthcheck 探测 /api/course/list；tzdata 保证 TZ 生效
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

# JVM 参数走 JAVA_TOOL_OPTIONS，这样 ENTRYPOINT 可以保持干净的 exec 形式
# （不用 sh -c 包一层，信号能正确传递给 JVM，docker stop 才会优雅停机）
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

# 只拷可执行 jar（Spring Boot repackage 会同时产出 *.jar 与 *.jar.original，
# 这里的 *.jar 不会匹配到后者）
COPY --from=build --chown=app:app /build/target/*.jar /app/app.jar

# 简历等上传文件的落盘目录（WebMvcConfig 映射的是相对路径 uploads/，
# 因此必须落在 WORKDIR 下的 /app/uploads）；预建并授权，避免以 app 身份运行时
# mkdir 失败
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
