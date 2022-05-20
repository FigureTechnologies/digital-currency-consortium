import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin(PluginIds.KotlinSpring) version PluginVersions.Kotlin
    id(PluginIds.KotlinAllOpen) version "1.5.30-RC"
    id(PluginIds.SpringBoot) version PluginVersions.SpringBoot
}

configurations {
    all {
        exclude(group = "log4j")
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.logging.log4j") {
                useVersion("2.17.0")
                because("CVE-2021-44228")
            }
        }
    }
}

dependencies {
    api(Libraries.LogbackCore)
    api(Libraries.LogbackClassic)
    api(Libraries.LogbackJackson)

    implementation.let {
        it(Libraries.Jackson) {
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        }
        it(Libraries.JacksonHubspot)

        it(Libraries.SpringBootDevTools)
        it(Libraries.SpringBootActuator)
        it(Libraries.SpringBootStartedJdbc)
        it(Libraries.SpringBootStarterWeb)
        it(Libraries.SpringBootStarterValidation)
        it(Libraries.JavaxValidation)

        it(Libraries.CommonsCsv)

        it(Libraries.Postgres)

        // ----- Event Stream -----
        it(Libraries.EventStreamCore)
        it(Libraries.EventStreamApi)
        it(Libraries.EventStreamApiModel)
        it(Libraries.Moshi)
        it(Libraries.MoshiKotlin)
        it(Libraries.OkHttp)

        // ----- Misc -----
        it(Libraries.Swagger2)
        it(Libraries.SwaggerStarter)
        it(Libraries.SwaggerUi)
        it(Libraries.Flyway)
        it(Libraries.Exposed)
        it(Libraries.ExposedDao)
        it(Libraries.ExposedJdbc)
    }

    testImplementation.let {
        it(Libraries.JunitJupiterApi)
        it(Libraries.JunitJupiterParams)
        it(Libraries.JunitCommons)
        it(Libraries.SpringBootStarterTest)
        it(Libraries.Mockito)
        it(Libraries.Mockk)
        it(Libraries.TestContainersPostgres)
        it(Libraries.TestContainers)
        it(Libraries.TestContainersJunitJupiter)
    }

    testRuntimeOnly(Libraries.JunitJupiterEngine)
}

tasks.test {
    useJUnitPlatform {}
    testLogging {
        events(
            PASSED,
            SKIPPED,
            FAILED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Spring boot settings
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"

tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

springBoot.mainClass.set("io.provenance.digitalcurrency.report.ApplicationKt")
