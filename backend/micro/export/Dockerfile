FROM ghcr.io/graalvm/jdk-community:21

COPY ./build/libs/export-all.jar export-all.jar

ENTRYPOINT ["java", "-jar", "export-all.jar"]
