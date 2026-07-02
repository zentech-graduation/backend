FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -u 10001 luvax \
    && mkdir -p /var/log/luvax \
    && chown -R luvax:luvax /var/log/luvax /app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

USER luvax

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
