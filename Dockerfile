# ===============================================
# 拾味 HM-DianPing 镜像构建（多阶段）
#
#   阶段一  builder：用 Maven 把源码编成可执行 fat jar
#   阶段二  runtime：只带 JRE，把 jar 拷进去跑
#
# 最终镜像不含 Maven、不含源码、不含 ~/.m2，体积约为构建阶段的 1/8。
# ===============================================

# ========== 阶段一：构建 ==========
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 换阿里云镜像源。不换的话容器内从 Maven Central 拉依赖会慢到十几分钟，
# 甚至因为连接重置直接失败（这台机器实测过）。
COPY deploy/maven-settings.xml /root/.m2/settings.xml

# 先只拷 pom 并预下载依赖：只要 pom 没变，这一层就走缓存，
# 改代码后重建不需要重新拉几百 MB 依赖。
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷源码编译。跳过测试：src/test 里有练习残留文件编不过，
# 而且单元测试依赖真实中间件，容器构建阶段不该跑。
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ========== 阶段二：运行 ==========
FROM eclipse-temurin:17-jre-alpine

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo "${TZ}" > /etc/timezone && \
    apk del tzdata

WORKDIR /app

# fat jar 已经包含全部依赖，java -jar 即可启动
COPY --from=builder /build/target/*.jar /app/app.jar

# 应用自己会建这个目录；预先建好并交给非 root 用户，避免挂载卷权限报错
RUN mkdir -p /var/log/hmdp /var/www/hmdp/imgs && \
    addgroup -S hmdp && adduser -S -G hmdp hmdp && \
    chown -R hmdp:hmdp /app /var/log/hmdp /var/www/hmdp

USER hmdp

# 这里不用 /actuator/health —— 项目没引 spring-boot-starter-actuator，
# 探这个路径永远 404，健康检查会一直失败导致容器被判不健康。
# 改成探一个真实存在的业务接口。
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/shop-type/list || exit 1

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=70.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
