# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY custom-m2/settings.xml custom-m2/settings.xml
# Cache dependencies first for faster rebuilds.
RUN mvn -s custom-m2/settings.xml -B dependency:go-offline
COPY src ./src
RUN mvn -s custom-m2/settings.xml -B -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# Run as a non-root user.
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/ontheway-backend-1.0.0.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
