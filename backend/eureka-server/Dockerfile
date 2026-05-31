FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml /build/pom.xml
COPY backend/config-server/pom.xml /build/backend/config-server/pom.xml
COPY backend/eureka-server/pom.xml /build/backend/eureka-server/pom.xml
COPY backend/gateway/pom.xml /build/backend/gateway/pom.xml
COPY backend/auth-service/pom.xml /build/backend/auth-service/pom.xml
COPY backend/game-service/pom.xml /build/backend/game-service/pom.xml
COPY backend/eureka-server/src /build/backend/eureka-server/src
RUN mvn -pl backend/eureka-server -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appuser && adduser -S appuser -G appuser -u 1001
USER appuser
WORKDIR /app
COPY --from=builder /build/backend/eureka-server/target/*.jar app.jar
EXPOSE 8761
ENV JAVA_TOOL_OPTIONS="-Xmx160m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=5s --retries=10 \
  CMD wget -qO- http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
