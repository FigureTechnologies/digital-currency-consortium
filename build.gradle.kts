import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version PluginVersions.Kotlin apply false
    `java-library`
    id(PluginIds.DependencyAnalysis) version PluginVersions.DependencyAnalysis
    id(PluginIds.Idea)
    id(PluginIds.Jacoco)
    id(PluginIds.Protobuf) version PluginVersions.Protobuf
    id(PluginIds.TestLogger) version PluginVersions.TestLogger apply false
}

allprojects {
    group = "io.provenance.digitalcurrency.consortium"
    version = artifactVersion(this)

    repositories {
        mavenCentral()
        mavenLocal()

        // For KEthereum library
        maven("https://jitpack.io")
    }
}

subprojects {
    project.ext.properties["kotlin_version"] = Versions.Kotlin

    apply {
        plugin(PluginIds.Kotlin)
        plugin(PluginIds.Idea)
        plugin(PluginIds.Protobuf)
        plugin(PluginIds.TestLogger)
        plugin(PluginIds.Jacoco)
    }

    repositories {
        mavenCentral()
        mavenLocal()
    }

    val ktlint: Configuration by configurations.creating

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    plugins.withType<com.adarshr.gradle.testlogger.TestLoggerPlugin> {
        configure<com.adarshr.gradle.testlogger.TestLoggerExtension> {
            theme = com.adarshr.gradle.testlogger.theme.ThemeType.STANDARD
            showCauses = true
            slowThreshold = 1000
            showSummary = true
        }
    }

    jacoco {
        toolVersion = Versions.Jacoco
        reportsDirectory.set(layout.buildDirectory.dir("customJacocoReportDir"))
    }

    tasks.test {
        finalizedBy(tasks.jacocoTestReport)
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)

        reports {
            xml.required.set(false)
            csv.required.set(false)
            html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
        }
    }

    dependencies {
        implementation.let {
            it(Libraries.KotlinAllOpen)
            it(Libraries.KotlinReflect)
            it(Libraries.KotlinStdlib)
            it(Libraries.KotlinStdlibJdk8)
        }

        ktlint(Libraries.KtLint)
    }

    tasks.register<Copy>("installGitHooks") {
        from("${project.rootDir}/hooks/pre-push")
        into("${project.rootDir}/.git/hooks")
    }

    tasks.named("build") {
        dependsOn("installGitHooks")
    }

    tasks.register<JavaExec>("ktlint") {
        group = "verification"
        description = "Check Kotlin code style."
        classpath = ktlint
        main = "com.pinterest.ktlint.Main"
        args("src/**/*.kt")
        // to generate report in checkstyle format prepend following args:
        // "--reporter=plain", "--reporter=checkstyle,output=${buildDir}/ktlint.xml"
        // see https://github.com/pinterest/ktlint#usage for more
    }

    tasks.named("check") {
        dependsOn("ktlint")
    }

    tasks.register<JavaExec>("ktlintFormat") {
        group = "formatting"
        description = "Fix Kotlin code style deviations."
        classpath = ktlint
        main = "com.pinterest.ktlint.Main"
        args("-F", "src/**/*.kt")
    }
}
