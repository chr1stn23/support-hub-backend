# Etapa 1: compilar
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Capa de dependencias: solo se invalida si cambian los scripts de build
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Etapa 2: ejecutar
FROM eclipse-temurin:25-jre

RUN groupadd --system app && useradd --system --gid app --no-create-home app \
 && mkdir -p /data/attachments && chown -R app:app /data

WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar app.jar

USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    FILE_STORAGE_PATH=/data/attachments \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

VOLUME /data/attachments
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]