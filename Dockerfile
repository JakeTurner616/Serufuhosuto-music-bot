FROM maven:3.9-eclipse-temurin-25-noble AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /build/target/Serufuhosuto-music-bot-1.6.jar /app/app.jar

RUN mkdir -p /app/tools /app/tmp \
    && curl -L -o /app/tools/yt-dlp https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp \
    && chmod +x /app/tools/yt-dlp

ENTRYPOINT ["java", "-Djava.io.tmpdir=/app/tmp", "-jar", "/app/app.jar"]
