FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/order-service/pom.xml services/order-service/pom.xml
RUN mvn -B -f services/order-service/pom.xml dependency:go-offline
COPY services/order-service/src services/order-service/src
RUN mvn -B -f services/order-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/order-service/target/order-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]