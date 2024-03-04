import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "no.nav.syfo"
version = "0.0.1"

object Version {
    const val FLYWAY = "10.8.1"
    const val HIKARI = "5.1.0"
    const val POSTGRES = "42.7.2"
    const val POSTGRES_EMBEDDED = "2.0.6"
    const val KAFKA = "3.6.1"
    const val LOGBACK = "1.4.14"
    const val LOGSTASH_ENCODER = "7.4"
    const val MICROMETER_REGISTRY = "1.12.2"
    const val JACKSON_DATATYPE = "2.16.1"
    const val KTOR = "2.3.8"
    const val SPEK = "2.0.19"
    const val MOCKK = "1.13.9"
    const val NIMBUS_JOSE_JWT = "9.37.3"
    const val KLUENT = "1.73"
}

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-client-apache:${Version.KTOR}")
    implementation("io.ktor:ktor-client-content-negotiation:${Version.KTOR}")
    implementation("io.ktor:ktor-serialization-jackson:${Version.KTOR}")
    implementation("io.ktor:ktor-server-auth-jwt:${Version.KTOR}")
    implementation("io.ktor:ktor-server-call-id:${Version.KTOR}")
    implementation("io.ktor:ktor-server-content-negotiation:${Version.KTOR}")
    implementation("io.ktor:ktor-server-netty:${Version.KTOR}")
    implementation("io.ktor:ktor-server-status-pages:${Version.KTOR}")

    // Logging
    implementation("ch.qos.logback:logback-classic:${Version.LOGBACK}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Version.LOGSTASH_ENCODER}")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:${Version.KTOR}")
    implementation("io.micrometer:micrometer-registry-prometheus:${Version.MICROMETER_REGISTRY}")

    // Database
    implementation("org.postgresql:postgresql:${Version.POSTGRES}")
    implementation("com.zaxxer:HikariCP:${Version.HIKARI}")
    implementation("org.flywaydb:flyway-database-postgresql:${Version.FLYWAY}")
    testImplementation("io.zonky.test:embedded-postgres:${Version.POSTGRES_EMBEDDED}")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:${Version.KAFKA}", excludeLog4j)

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Version.JACKSON_DATATYPE}")

    // Tests
    testImplementation("io.ktor:ktor-server-tests:${Version.KTOR}")
    testImplementation("io.mockk:mockk:${Version.MOCKK}")
    testImplementation("io.ktor:ktor-client-mock:${Version.KTOR}")
    testImplementation("com.nimbusds:nimbus-jose-jwt:${Version.NIMBUS_JOSE_JWT}")
    testImplementation("org.amshove.kluent:kluent:${Version.KLUENT}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Version.SPEK}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Version.SPEK}")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<ShadowJar> {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
