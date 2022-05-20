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

    implementation.let {
        it(project(":database"))

        it(Libraries.FeignCore)
        it(Libraries.FeignJackson)
        it(Libraries.FeignSlf4j)

        it(Libraries.PbcProto)
        it(Libraries.PbcClient)
        it(Libraries.PbcHDWallet)

        // websocket
        it(Libraries.Scarlet)
        it(Libraries.ScarletStreamAdapter)
        it(Libraries.ScarletWebsocket)
        it(Libraries.ScarletMessageAdapter)
    }
}

// Spring boot settings
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"

tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

springBoot.mainClass.set("io.provenance.digitalcurrency.consortium.ApplicationKt")
