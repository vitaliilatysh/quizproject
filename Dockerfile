# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY api/build.gradle ./api/

# Keep dependency resolution in a separate layer for faster rebuilds.
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew :api:dependencies --no-daemon

COPY api/src ./api/src

RUN ./gradlew :api:bootJar --no-daemon \
    && find api/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' \
        -exec cp '{}' /workspace/quiz-api.jar ';' \
    && test -f /workspace/quiz-api.jar

FROM eclipse-temurin:25-jre-noble

LABEL org.opencontainers.image.source="https://github.com/vitaliilatysh/quizproject"

RUN groupadd --gid 10001 quiz \
    && useradd --uid 10001 --gid 10001 --no-create-home --home-dir /app \
        --shell /usr/sbin/nologin quiz

WORKDIR /app

COPY --from=builder --chown=10001:10001 /workspace/quiz-api.jar /app/quiz-api.jar

USER 10001:10001

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/quiz-api.jar"]
