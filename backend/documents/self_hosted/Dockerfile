FROM gradle:7.6.1-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle :backend:documents:self_hosted:shadowJar --no-daemon

FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/backend/documents/self_hosted/build/libs/writeopia-selfhosted.jar /app/writeopia-selfhosted.jar
VOLUME /root/.writeopia
EXPOSE 8080
CMD ["java", "-jar", "/app/writeopia-selfhosted.jar"]
