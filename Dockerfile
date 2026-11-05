# Контекст сборки — корень репозитория: сначала общая библиотека libs/platform, потом сервис.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Сервисы собираются параллельно с общим кэшем ~/.m2 — блокировки файлов, чтобы сборки не мешали друг другу
ENV MAVEN_ARGS="-Daether.syncContext.named.factory=file-lock -Daether.syncContext.named.nameMapper=file-gav"
COPY libs/platform libs/platform
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -f libs/platform/pom.xml install -DskipTests
COPY services/payment/pom.xml services/payment/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -f services/payment/pom.xml dependency:go-offline
COPY services/payment/src services/payment/src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -f services/payment/pom.xml package && cp services/payment/target/payment-*.jar /app.jar

FROM eclipse-temurin:21-jre
# Не root: у процесса в контейнере нет лишних прав
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /app.jar /app.jar
# Куча — доля лимита памяти контейнера; при нехватке памяти процесс падает, и его перезапускают
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app.jar"]
