FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/attractions.jar ./attractions.jar
USER 10001:10001
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "/app/attractions.jar"]
