FROM amazoncorretto:21-alpine3.22

COPY ./build/libs/gateway-all.jar gateway-all.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "gateway-all.jar"]
