FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 shortener
WORKDIR /app
COPY --from=build /workspace/target/url-shortener-*.jar app.jar
USER shortener
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]