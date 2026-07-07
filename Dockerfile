FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY certs/ca.pem /workspace/certs/ca.pem
COPY parser-service/pom.xml /workspace/pom.xml
COPY parser-service/src /workspace/src

RUN keytool -importcert -noprompt \
    -alias AivenCA \
    -file /workspace/certs/ca.pem \
    -keystore /workspace/client.truststore.jks \
    -storepass changeit

RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar
COPY --from=build /workspace/client.truststore.jks /app/client.truststore.jks

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]