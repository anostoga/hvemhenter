val kotlinVersion = "1.9.24"
val ktorVersion = "2.3.12"
val exposedVersion = "0.51.0"
val logbackVersion = "1.5.6"

buildscript {
    dependencies {
        // Flyway-gradle-tasks kjører i build-classpath, trenger driver + dialect-modul her også
        // (ikke bare i app sin `implementation`-classpath).
        classpath("org.postgresql:postgresql:42.7.4")
        classpath("org.flywaydb:flyway-database-postgresql:10.15.0")
    }
}

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("io.ktor.plugin") version "2.3.12"
    id("org.flywaydb.flyway") version "10.15.0"
    application
}

group = "no.pilot"
version = "0.1.0"

application {
    mainClass.set("no.pilot.barnehage.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // Ktor client (til å kalle Google Calendar API)
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

    // Database: Exposed + Postgres/Flyway. Postgres er eneste database — families,
    // parents, oauth_tokens og assignments er alle familie-scopet (se V1__init.sql).
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.15.0")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}

tasks.test {
    useJUnitPlatform()
    // Dummy testverdier — ikke ekte hemmeligheter. Sikrer at testene kjører
    // uavhengig av lokalt oppsatte miljøvariabler.
    environment("TOKEN_ENCRYPTION_KEY", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    environment("STATE_SIGNING_SECRET", "test-signing-secret")
    environment("GOOGLE_CLIENT_ID", "test-client-id")
    environment("GOOGLE_CLIENT_SECRET", "test-client-secret")
    environment("GOOGLE_REDIRECT_URI", "http://localhost:8080/oauth/callback")
}

ktor {
    fatJar {
        archiveFileName.set("barnehage-backend.jar")
    }
}

// Denne Gradle-tasken (`./gradlew flywayMigrate`) trengs FORTSATT lokalt og i CI-testjobben:
// enkelte tester (f.eks. FamilyScopedAssignmentRepositoryTest) kobler til databasen direkte
// via Exposed og går utenom `PostgresDatabase.connect()`/`module()`, så skjemaet må være
// migrert på forhånd før de kjører.
// Selve APPEN migrerer nå seg selv programmatisk ved oppstart (se PostgresDatabase.connect()),
// så produksjonsdeploy (Fly.io) er ikke lenger avhengig av et eget CI/manuelt migreringssteg
// mot Supabase — appen bruker samme DATABASE_URL den uansett kobler til med.
flyway {
    url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/barnehage"
    user = System.getenv("DATABASE_USER") ?: "postgres"
    password = System.getenv("DATABASE_PASSWORD") ?: "localdev"
    locations = arrayOf("classpath:db/migration")
}
