FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY common/operational-logging/pom.xml common/operational-logging/pom.xml
COPY common/operational-logging/src common/operational-logging/src
RUN mvn -B -f common/operational-logging/pom.xml install -DskipTests
COPY incident-engine/log-processor/pom.xml incident-engine/log-processor/pom.xml
RUN mvn -B -f incident-engine/log-processor/pom.xml dependency:go-offline
COPY incident-engine/log-processor/src incident-engine/log-processor/src
RUN mvn -B -f incident-engine/log-processor/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/incident-engine/log-processor/target/log-processor-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
