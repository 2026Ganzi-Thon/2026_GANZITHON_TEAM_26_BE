# ===== 1단계: 빌드 =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성만 먼저 받아서 캐시 (소스만 바뀌면 이 단계는 재사용됨)
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY config config
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ===== 2단계: 실행 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul
ENV SPRING_PROFILES_ACTIVE=dev
ENV JAVA_OPTS=""

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
