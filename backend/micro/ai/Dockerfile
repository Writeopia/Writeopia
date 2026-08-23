FROM ghcr.io/graalvm/jdk-community:21

COPY ./build/libs/ai-all.jar ai-all.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "ai-all.jar"]
