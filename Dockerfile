FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src src
RUN chmod 755 mvnw && ./mvnw -B -ntp -DskipTests -Djacoco.skip=true package

FROM maven:3.9.11-eclipse-temurin-21
WORKDIR /opt/platform
ENV HOME=/home/agentic AGENTIC_BUILD_ASSETS_ROOT=/opt/platform AGENTIC_REPOSITORY_ROOT=/opt/platform/scenario-repositories AGENTIC_WORKSPACE_ROOT=/var/lib/agentic/workspaces AGENTIC_MAVEN_REPOSITORY=/home/agentic/.m2/repository
COPY --from=build /build/target/agentic-url-shortener-0.1.0-SNAPSHOT.jar app.jar
COPY --from=build /root/.m2 /home/agentic/.m2
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src/main/java/dev/ajaymatta/agentic/shortener src/main/java/dev/ajaymatta/agentic/shortener
COPY src/main/resources/db/migration/V5__persistent_shortener.sql src/main/resources/db/migration/V5__persistent_shortener.sql
COPY scenario-repositories scenario-repositories
COPY scripts/HealthProbe.java HealthProbe.java
RUN mkdir -p /var/lib/agentic/workspaces && chown -R 10001:10001 /home/agentic /var/lib/agentic && chmod 755 mvnw
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=8s --start-period=40s --retries=12 CMD java HealthProbe.java http://127.0.0.1:8080/actuator/health/readiness
ENTRYPOINT ["java","-Xms64m","-Xmx512m","-jar","app.jar"]
