FROM openjdk:17

ARG WRITEOPIA_FIREBASE_ID
ENV WRITEOPIA_FIREBASE_ID $WRITEOPIA_FIREBASE_ID

ARG DB_USER
ENV DB_USER $DB_USER

ARG IN_MEMORY_DATABASE
ENV IN_MEMORY_DATABASE $IN_MEMORY_DATABASE

# Create non-root user for security
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --shell /bin/false appuser

WORKDIR /app

COPY ./build/libs/api_editor_socket-all.jar api_editor.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "api_editor.jar"]
