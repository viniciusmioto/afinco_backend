FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode dependency:resolve

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode clean package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup --system afinco \
    && adduser --system --ingroup afinco afinco \
    && mkdir -p /application /data \
    && chown -R afinco:afinco /application /data

WORKDIR /application
COPY --from=build --chown=afinco:afinco /workspace/target/afinco-backend-*.jar app.jar

ENV AFINCO_DATABASE_PATH=/data/afinco.db

USER afinco
EXPOSE 8080
VOLUME ["/data"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
