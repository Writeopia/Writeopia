FROM ghcr.io/graalvm/jdk-community:21

COPY ./build/libs/media-all.jar media-all.jar

# Cloud Run will inject PORT env var (defaults to 8080)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "media-all.jar"]
