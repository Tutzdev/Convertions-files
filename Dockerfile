# CI overrides the registry with a mirror, since anonymous Docker Hub pulls are rate limited on shared runners.
ARG BASE_REGISTRY=docker.io/library

FROM ${BASE_REGISTRY}/eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Ubuntu based rather than Alpine: Java2D, used by the image and PDF converters, needs fontconfig.
FROM ${BASE_REGISTRY}/eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system boost && useradd --system --gid boost boost
COPY --from=build /app/target/boostconvert-mini-*.jar app.jar
USER boost
ENV BOOSTCONVERT_STORAGE_DIR=/tmp/boostconvert
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
