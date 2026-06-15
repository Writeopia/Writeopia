FROM ghcr.io/graalvm/jdk-community:21

COPY ./build/libs/auth-all.jar auth-all.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "auth-all.jar"]
