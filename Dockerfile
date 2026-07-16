# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY custom-m2/settings.xml custom-m2/settings.xml
# Cache project dependencies without traversing every plugin/BOM repository.
RUN mvn -s custom-m2/settings.xml -B -DskipTests dependency:resolve
COPY src ./src
RUN mvn -s custom-m2/settings.xml -B -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# Run as a non-root user.
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/OnTheWay-1.0.0.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS=""
ENTRYPOINT ["java", "-jar", "app.jar"]
