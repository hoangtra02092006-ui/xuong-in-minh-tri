# Stage 1: build with the Maven wrapper (no Maven needed on the host)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q package -DskipTests

# Stage 2: run on a small JRE image as a non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home xuongin && mkdir -p /app/storage && chown xuongin /app/storage
COPY --from=build /build/target/xuong-in-backend-*.jar app.jar
USER xuongin
ENV APP_STORAGE_PATH=/app/storage
# Memory flags (spec 10.6). The container limit (1.5 GB on the shared server) is set in deploy/;
# 60% heap leaves room for off-heap memory used by ImageIO, ICC conversion and PDFBox.
# G1 + periodic GC returns unused heap to the OS while idle.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseG1GC -XX:G1PeriodicGCInterval=60000 -XX:G1PeriodicGCSystemLoadThreshold=0 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
