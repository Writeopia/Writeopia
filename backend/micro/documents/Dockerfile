FROM ghcr.io/graalvm/jdk-community:21

COPY ./build/libs/documents-all.jar documents-all.jar

# Cloud Run will inject PORT env var (defaults to 8080)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "documents-all.jar"]
