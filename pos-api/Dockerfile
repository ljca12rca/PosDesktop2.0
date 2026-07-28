FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY pos-domain/pom.xml pos-domain/pom.xml
COPY pos-api/pom.xml pos-api/pom.xml
COPY pos-desktop/pom.xml pos-desktop/pom.xml

RUN mvn -B -pl pos-api -am dependency:go-offline

COPY pos-domain/src pos-domain/src
COPY pos-api/src pos-api/src

RUN mvn -B -pl pos-api -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/pos-api/target/pos-api-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /var/posdesktop-media

EXPOSE 8083

ENV POS_API_PORT=8083

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
