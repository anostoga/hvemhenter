# Merk: Navs Chainguard-registry (europe-north1-docker.pkg.dev/cgr-nav/...) krever
# Nav-nettverk/autentisering og er ikke tilgjengelig for dette hobbyprosjektet utenfor
# Nav. Bruker derfor offentlige eclipse-temurin-images, men følger ellers samme
# prinsipper: multi-stage build, minimal COPY, ikke-root bruker.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN ./gradlew --version --no-daemon
COPY src ./src
RUN ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:25-jre
RUN addgroup --system app && adduser --system --ingroup app app
WORKDIR /app
COPY --from=build /app/build/libs/barnehage-backend.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
