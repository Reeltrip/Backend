ARG BUILD_IMAGE=eclipse-temurin:21-jdk-jammy
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy
FROM ${BUILD_IMAGE} AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN sed -i 's/\r$//' gradlew
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew --no-daemon test bootJar verifyMockIsolation

FROM ${RUNTIME_IMAGE}
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod PORT=8080 TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
COPY --from=build --chown=10001:10001 /workspace/build/libs/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
