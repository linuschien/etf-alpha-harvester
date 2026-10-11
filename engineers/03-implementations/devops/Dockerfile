# 1. Base Runtime Image - Use alpine-based JRE for smallest footprint
FROM eclipse-temurin:25-jre-alpine

# 2. Security & Setup - Statically assign secure UID/GID 10001 to prevent permission drift
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup && \
    mkdir -p /app/data && \
    chown -R appuser:appgroup /app

WORKDIR /app

# 3. Code Injection - Set ownership directly during COPY to eliminate extra chown layer
COPY --chown=appuser:appgroup *.jar app.jar

# 4. Environment & Security
ENV SPRING_PROFILES_ACTIVE=docker

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
