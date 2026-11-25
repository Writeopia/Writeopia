FROM amazoncorretto:21-alpine3.22

# Java2D needs fontconfig and a font to draw text: the equations are rendered with JLaTeXMath
# when the gateway serves the cloud AI routes.
RUN apk add --no-cache fontconfig ttf-dejavu

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/false -D appuser

WORKDIR /app

COPY ./build/libs/gateway-all.jar gateway-all.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "gateway-all.jar"]
