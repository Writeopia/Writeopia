FROM arm64v8/amazonlinux:2

# Set the working directory inside the container
WORKDIR /app

RUN yum -y update \
    && yum install -y tar unzip gzip git gcc gcc-c++ gcc-gfortran shadow-utils

ENV GRAALVM_FILE graalvm-community-openjdk-17.0.8+7.1
RUN curl -4 -L https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-17.0.8/graalvm-community-jdk-17.0.8_linux-aarch64_bin.tar.gz --output graalvm-community-jdk-17.0.8_linux-aarch64_bin.tar.gz
RUN tar -xzf graalvm-community-jdk-17.0.8_linux-aarch64_bin.tar.gz
RUN mv $GRAALVM_FILE /usr/lib/graalvm
RUN rm -rf $GRAALVM_FILE

ENV JAVA_HOME /usr/lib/graalvm

RUN git clone https://github.com/leandroBorgesFerreira/StoryTeller.git

WORKDIR ./StoryTeller
RUN ./gradlew backend:intronotes:nativeCompile  --no-daemon

# Create non-root user for security
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --shell /bin/bash appuser && \
    chown -R appuser:appgroup /app

USER appuser

ENTRYPOINT ["sh"]
