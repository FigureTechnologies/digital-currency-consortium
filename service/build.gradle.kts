import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED


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
    implementation(project(":database"))

    api(Libraries.LogbackCore)
    api(Libraries.LogbackClassic)
    api(Libraries.LogbackJackson)

    implementation.let {
        it(Libraries.FeignCore)
        it(Libraries.FeignJackson)
        it(Libraries.FeignSlf4j)
        it(Libraries.Jackson) {
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        }
        it(Libraries.JacksonHubspot)

        it(Libraries.PbcProto)

        it(Libraries.SpringBootDevTools)
        it(Libraries.SpringBootActuator)
        it(Libraries.SpringBootStartedJdbc)
        it(Libraries.SpringBootStarterWeb)
        it(Libraries.SpringBootStarterValidation)
        it(Libraries.JavaxValidation)
        it(Libraries.PbcClient)
        it(Libraries.PbcHDWallet)

        it(Libraries.Postgres)

        // websocket
        it(Libraries.Scarlet)
        it(Libraries.ScarletStreamAdapter)
        it(Libraries.ScarletWebsocket)
        it(Libraries.ScarletMessageAdapter)

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


// Spring boot settings
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"

