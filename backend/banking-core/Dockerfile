# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/banking-core-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system banking && useradd --system --gid banking --no-create-home --shell /usr/sbin/nologin banking
RUN mkdir -p /app/data/documents && chown -R banking:banking /app/data
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER banking
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
