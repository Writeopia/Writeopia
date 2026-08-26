FROM amazoncorretto:21-alpine3.22

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/false -D appuser

WORKDIR /app

COPY ./build/libs/gateway-all.jar gateway-all.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "gateway-all.jar"]
