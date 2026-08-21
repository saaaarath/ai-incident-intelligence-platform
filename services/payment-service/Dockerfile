FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/payment-service/pom.xml services/payment-service/pom.xml
RUN mvn -B -f services/payment-service/pom.xml dependency:go-offline
COPY services/payment-service/src services/payment-service/src
RUN mvn -B -f services/payment-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/payment-service/target/payment-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]