import org.springframework.boot.gradle.tasks.run.BootRun

dependencies {
    implementation.let {
        it(Libraries.CommonsCsv)

        // ----- Event Stream -----
        it(Libraries.EventStreamCore)
        it(Libraries.EventStreamApiModel)
        it(Libraries.Moshi)
        it(Libraries.MoshiKotlin)
        it(Libraries.OkHttp)
    }
}

// Spring boot settings
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"

tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

springBoot.mainClass.set("io.provenance.digitalcurrency.report.ApplicationKt")
