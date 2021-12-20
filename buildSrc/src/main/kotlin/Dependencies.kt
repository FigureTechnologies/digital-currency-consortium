object PluginIds {
    const val Kotlin = "kotlin"
    const val KotlinSpring = "plugin.spring"

    const val DependencyAnalysis = "com.autonomousapps.dependency-analysis"
    const val Flyway = "org.flywaydb.flyway"
    const val Idea = "idea"
    const val Jacoco = "jacoco"
    const val Protobuf = "com.google.protobuf"
    const val SpringBoot = "org.springframework.boot"
    const val KotlinAllOpen = "org.jetbrains.kotlin.plugin.allopen"
}

object PluginVersions {
    const val Kotlin = "1.5.21"

    const val DependencyAnalysis = "0.69.0"
    const val Flyway = "7.12.1"
    const val Protobuf = "0.8.17"
    const val SpringBoot = "2.5.3"
}

object Versions {
    const val Kotlin = PluginVersions.Kotlin
    const val Coroutines = "1.5.1"

    const val Jacoco = "0.8.7"

    const val Grpc = "1.39.0"
    // upgrading this to 0.1.12 causes moshi/reflect errors that I did not try to resolve
    const val ScarletForEventStream = "0.1.11"
    const val PbcProto = "1.7.0-0.0.2"

    const val BouncyCastle = "1.63"
    const val Exposed = "0.33.1"
    const val Feign = "11.6"
    const val Flyway = PluginVersions.Flyway
    const val Jackson = "2.12.2"
    const val JacksonHubspot = "0.9.12"
    const val JavaxValidation = "2.0.0.Final"
    const val Kethereum = "0.83.4"
    const val Komputing = "0.1"
    const val Logback = "0.1.5"
    const val Postgres = "42.2.23"
    const val Protobuf = "3.6.1"
    const val Swagger = "3.0.0"
    const val SwaggerUi = "2.9.2"
    const val SpringBoot = PluginVersions.SpringBoot

    // Testing
    const val JunitJupiter = "5.7.1"
    const val JunitCommons = "1.7.0"
    const val Mockito = "3.2.0"
    const val Mockk = "1.12.0"
    const val TestContainers = "1.15.1"

    const val KtLint = "0.42.1"
}

object Libraries {
    const val KotlinReflect = "org.jetbrains.kotlin:kotlin-reflect"
    const val KotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"
    const val KotlinStdlibJdk8 = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.Kotlin}"
    const val KotlinAllOpen = "org.jetbrains.kotlin:kotlin-allopen:${Versions.Kotlin}"
    const val Coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Coroutines}"

    const val LogbackCore = "ch.qos.logback.contrib:logback-json-core:${Versions.Logback}"
    const val LogbackClassic = "ch.qos.logback.contrib:logback-json-classic:${Versions.Logback}"
    const val LogbackJackson = "ch.qos.logback.contrib:logback-jackson:${Versions.Logback}"

    const val GoogleProto = "com.google.protobuf:protobuf-java:${Versions.Protobuf}"
    const val GoogleProtoJavaUtil = "com.google.protobuf:protobuf-java-util:${Versions.Protobuf}"
    const val Protobuf = "com.google.protobuf:protobuf-java:${PluginVersions.Protobuf}"
    const val PbcProto = "io.provenance.protobuf:pb-proto-java:${Versions.PbcProto}"

    const val FeignCore = "io.github.openfeign:feign-core:${Versions.Feign}"
    const val FeignJackson = "io.github.openfeign:feign-jackson:${Versions.Feign}"
    const val FeignSlf4j = "io.github.openfeign:feign-slf4j:${Versions.Feign}"
    const val Jackson = "com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.Jackson}"
    const val JacksonHubspot = "com.hubspot.jackson:jackson-datatype-protobuf:${Versions.JacksonHubspot}"

    const val GrpcAlts = "io.grpc:grpc-alts:${Versions.Grpc}"
    const val GrpcNetty = "io.grpc:grpc-netty:${Versions.Grpc}"
    const val GrpcProto = "io.grpc:grpc-protobuf:${Versions.Grpc}"
    const val GrpcStub = "io.grpc:grpc-stub:${Versions.Grpc}"

    const val Postgres = "org.postgresql:postgresql:${Versions.Postgres}"

    const val Scarlet = "com.tinder.scarlet:scarlet:${Versions.ScarletForEventStream}"
    const val ScarletStreamAdapter = "com.tinder.scarlet:stream-adapter-rxjava2:${Versions.ScarletForEventStream}"
    const val ScarletWebsocket = "com.tinder.scarlet:websocket-okhttp:${Versions.ScarletForEventStream}"
    const val ScarletMessageAdapter = "com.tinder.scarlet:message-adapter-moshi:${Versions.ScarletForEventStream}"

    const val Swagger2 = "io.springfox:springfox-swagger2:${Versions.Swagger}"
    const val SwaggerStarter = "io.springfox:springfox-boot-starter:${Versions.Swagger}"
    const val SwaggerUi = "io.springfox:springfox-swagger-ui:${Versions.SwaggerUi}"
    const val Flyway = "org.flywaydb:flyway-core:${Versions.Flyway}"
    const val Exposed = "org.jetbrains.exposed:exposed-core:${Versions.Exposed}"
    const val ExposedDao = "org.jetbrains.exposed:exposed-dao:${Versions.Exposed}"
    const val ExposedJavaTime = "org.jetbrains.exposed:exposed-jodatime:${Versions.Exposed}"
    const val ExposedJdbc = "org.jetbrains.exposed:exposed-jdbc:${Versions.Exposed}"

    const val BouncyCastle = "org.bouncycastle:bcprov-jdk15on:${Versions.BouncyCastle}"
    const val KethereumBip32 = "com.github.komputing.kethereum:bip32:${Versions.Kethereum}"
    const val KethereumBip39 = "com.github.komputing.kethereum:bip39:${Versions.Kethereum}"
    const val KethereumCrypto = "com.github.komputing.kethereum:crypto:${Versions.Kethereum}"
    const val KethereumCryptoApi = "com.github.komputing.kethereum:crypto_api:${Versions.Kethereum}"
    const val KethereumCryptoImplBc = "com.github.komputing.kethereum:crypto_impl_bouncycastle:${Versions.Kethereum}"
    const val KethereumKotlinExtensions = "com.github.komputing.kethereum:extensions_kotlin:${Versions.Kethereum}"
    const val KethereumModel = "com.github.komputing.kethereum:model:${Versions.Kethereum}"
    const val KomputingBase58 = "com.github.komputing:kbase58:${Versions.Komputing}"
    const val KomputingBip44 = "com.github.komputing:kbip44:${Versions.Komputing}"

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
    const val TestCoroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Coroutines}"

    const val KtLint = "com.pinterest:ktlint:${Versions.KtLint}"
}

// gradle configurations
const val api = "api"
const val implementation = "implementation"
const val testImplementation = "testImplementation"
const val testRuntimeOnly = "testRuntimeOnly"
