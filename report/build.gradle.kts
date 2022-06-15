import org.springframework.boot.gradle.tasks.run.BootRun

dependencies {
    implementation.let {
        it(Libraries.CommonsCsv)
    }
}

// Spring boot settings
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"

tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

springBoot.mainClass.set("io.provenance.digitalcurrency.report.ApplicationKt")
