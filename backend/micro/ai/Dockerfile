FROM ghcr.io/graalvm/jdk-community:21

# Java2D needs fontconfig and freetype to draw text: the equations are rendered with JLaTeXMath.
RUN microdnf install -y fontconfig freetype && microdnf clean all

# Create non-root user for security
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --shell /bin/false appuser

WORKDIR /app

COPY ./build/libs/ai-all.jar ai-all.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "ai-all.jar"]
