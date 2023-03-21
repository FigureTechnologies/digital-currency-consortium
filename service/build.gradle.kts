import org.springframework.boot.gradle.tasks.run.BootRun

dependencies {
    implementation.let {
        it(project(":database"))

        it(Libraries.FeignCore)
        it(Libraries.FeignJackson)
        it(Libraries.FeignSlf4j)

        it(Libraries.PbcProto)
        it(Libraries.PbcClient)
        it(Libraries.PbcHDWallet)

        // websocket
//        it(Libraries.Scarlet)
//        it(Libraries.ScarletStreamAdapter)
//        it(Libraries.ScarletWebsocket)
//        it(Libraries.ScarletMessageAdapter)
    }
}

// Spring boot settings
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"

tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

springBoot.mainClass.set("io.provenance.digitalcurrency.consortium.ApplicationKt")
