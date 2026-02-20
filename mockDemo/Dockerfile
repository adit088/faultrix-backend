FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY mockDemo/pom.xml .
RUN mvn dependency:go-offline -B
COPY mockDemo/src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-XX:+OptimizeStringConcat", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]