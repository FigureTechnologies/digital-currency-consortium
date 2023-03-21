object PluginIds {
    const val Kotlin = "kotlin"

    const val DependencyAnalysis = "com.autonomousapps.dependency-analysis"
    const val Flyway = "org.flywaydb.flyway"
    const val Idea = "idea"
    const val Jacoco = "jacoco"
    const val ProjectReport = "project-report"

    const val SpringBoot = "org.springframework.boot"
    const val KotlinAllOpen = "org.jetbrains.kotlin.plugin.allopen"
    const val KotlinSpring = "org.jetbrains.kotlin.plugin.spring"
}

object PluginVersions {
    const val Kotlin = "1.8.10"
    const val DependencyAnalysis = "1.19.0"
    const val Flyway = "7.15.0"
    // TODO - upgrading spring boot requires switching from swagger2 to open api deps
    const val SpringBoot = "2.5.7"
}

object Versions {
    const val Kotlin = PluginVersions.Kotlin

    const val Jacoco = "0.8.8"

    const val PbcProto = "1.8.0"
    const val PbcClient = "1.3.0"
    const val PbcHDWallet = "0.1.15"
    const val EventStream = "0.8.1"

    const val CommonsCsv = "1.10.0"
    const val Exposed = "0.41.1"
    const val Feign = "12.2"
    const val Flyway = PluginVersions.Flyway
    const val Jackson = "2.12.7"
    const val JacksonHubspot = "0.9.13"
    const val JavaxValidation = "2.0.1.Final"
    const val Logback = "0.1.5"
    const val Moshi = "1.14.0"
    const val Postgres = "42.6.0"
    const val Swagger = "3.0.0"
    const val SwaggerUi = "3.0.0"
    const val SpringBoot = PluginVersions.SpringBoot

    // Testing
    const val JunitJupiter = "5.9.2"
    const val JunitCommons = "1.9.2"
    const val Mockito = "4.1.0"
    const val Mockk = "1.13.4"
    const val TestContainers = "1.17.6"

    const val KtLint = "0.48.2"
}

object Libraries {
    const val KotlinReflect = "org.jetbrains.kotlin:kotlin-reflect"
    const val KotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"
    const val KotlinStdlibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.Kotlin}"
    const val KotlinAllOpen = "org.jetbrains.kotlin:kotlin-allopen:${Versions.Kotlin}"

    const val LogbackCore = "ch.qos.logback.contrib:logback-json-core:${Versions.Logback}"
    const val LogbackClassic = "ch.qos.logback.contrib:logback-json-classic:${Versions.Logback}"
    const val LogbackJackson = "ch.qos.logback.contrib:logback-jackson:${Versions.Logback}"

    const val PbcProto = "io.provenance:proto-kotlin:${Versions.PbcProto}"
    const val PbcClient = "io.provenance.client:pb-grpc-client-kotlin:${Versions.PbcClient}"
    const val PbcHDWallet = "io.provenance.hdwallet:hdwallet:${Versions.PbcHDWallet}"

    const val EventStreamCore = "tech.figure.eventstream:es-core:${Versions.EventStream}"
    const val EventStreamApi = "tech.figure.eventstream:es-api:${Versions.EventStream}"
    const val EventStreamApiModel = "tech.figure.eventstream:es-api-model:${Versions.EventStream}"

    const val FeignCore = "io.github.openfeign:feign-core:${Versions.Feign}"
    const val FeignJackson = "io.github.openfeign:feign-jackson:${Versions.Feign}"
    const val FeignSlf4j = "io.github.openfeign:feign-slf4j:${Versions.Feign}"
    const val Jackson = "com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.Jackson}"
    const val JacksonHubspot = "com.hubspot.jackson:jackson-datatype-protobuf:${Versions.JacksonHubspot}"

    const val CommonsCsv = "org.apache.commons:commons-csv:${Versions.CommonsCsv}"

    const val Postgres = "org.postgresql:postgresql:${Versions.Postgres}"

    const val Moshi = "com.squareup.moshi:moshi:${Versions.Moshi}"

    const val Swagger2 = "io.springfox:springfox-swagger2:${Versions.Swagger}"
    const val SwaggerStarter = "io.springfox:springfox-boot-starter:${Versions.Swagger}"
    const val SwaggerUi = "io.springfox:springfox-swagger-ui:${Versions.SwaggerUi}"
    const val Flyway = "org.flywaydb:flyway-core:${Versions.Flyway}"
    const val Exposed = "org.jetbrains.exposed:exposed-core:${Versions.Exposed}"
    const val ExposedDao = "org.jetbrains.exposed:exposed-dao:${Versions.Exposed}"
    const val ExposedJdbc = "org.jetbrains.exposed:exposed-jdbc:${Versions.Exposed}"

    const val SpringBootDevTools = "org.springframework.boot:spring-boot-devtools:${Versions.SpringBoot}"
    const val SpringBootActuator = "org.springframework.boot:spring-boot-starter-actuator:${Versions.SpringBoot}"
    const val SpringBootStartedJdbc = "org.springframework.boot:spring-boot-starter-jdbc:${Versions.SpringBoot}"
    const val SpringBootStarterWeb = "org.springframework.boot:spring-boot-starter-web:${Versions.SpringBoot}"
    const val SpringBootStarterValidation = "org.springframework.boot:spring-boot-starter-validation:${Versions.SpringBoot}"
    const val JavaxValidation = "javax.validation:validation-api:${Versions.JavaxValidation}"

    // Testing
    const val JunitJupiterApi = "org.junit.jupiter:junit-jupiter-api:${Versions.JunitJupiter}"
    const val JunitJupiterEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.JunitJupiter}"
    const val JunitJupiterParams = "org.junit.jupiter:junit-jupiter-params:${Versions.JunitJupiter}"
    const val JunitCommons = "org.junit.platform:junit-platform-commons:${Versions.JunitCommons}"
    const val SpringBootStarterTest = "org.springframework.boot:spring-boot-starter-test:${Versions.SpringBoot}"
    const val Mockito = "org.mockito.kotlin:mockito-kotlin:${Versions.Mockito}"
    const val Mockk = "io.mockk:mockk:${Versions.Mockk}"
    const val TestContainersPostgres = "org.testcontainers:postgresql:${Versions.TestContainers}"
    const val TestContainers = "org.testcontainers:testcontainers:${Versions.TestContainers}"
    const val TestContainersJunitJupiter = "org.testcontainers:junit-jupiter:${Versions.TestContainers}"

    const val KtLint = "com.pinterest:ktlint:${Versions.KtLint}"
}

// gradle configurations
const val api = "api"
const val implementation = "implementation"
const val testImplementation = "testImplementation"
const val testRuntimeOnly = "testRuntimeOnly"
