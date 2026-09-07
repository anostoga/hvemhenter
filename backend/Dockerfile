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
# JVM-flagg valgt for RASK OPPSTART på en liten (256 MB, delt CPU) Fly.io-VM som
# stopper/starter ofte (scale-to-zero) — ikke for gjennomstrømning:
#   -XX:TieredStopAtLevel=1  hopper over den tunge C2-JIT-kompilatoren. For en
#     app som kjører kort/med lav trafikk mellom cold starts rekker C2 uansett
#     aldri å bli lønnsomt — man betaler kun oppstartskostnaden for ingenting.
#   -XX:+UseSerialGC         seriell GC har langt lavere oppstarts-/minne-
#     overhead enn G1 (JVM-default) på en så liten heap — G1 er designet for
#     store hauger og bruker tid på oppsett appen her aldri drar nytte av.
#   -Xshare:auto             bruk CDS (Class Data Sharing)-arkivet som følger
#     med JDK-et for raskere klasselasting ved oppstart (ingen custom AppCDS-
#     bygg nødvendig for denne gevinsten).
#   -Xmx200m                 hold heapen godt innenfor VM-ens 256 MB slik at vi
#     ikke risikerer OOM ved samtidige requests rett etter en cold start.
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xshare:auto", "-Xmx200m", "-jar", "app.jar"]
