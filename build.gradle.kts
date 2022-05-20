import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version PluginVersions.Kotlin apply false
    `java-library`
    id(PluginIds.DependencyAnalysis) version PluginVersions.DependencyAnalysis
    id(PluginIds.Idea)
    id(PluginIds.Jacoco)
    id(PluginIds.ProjectReport)
}

allprojects {
    group = "io.provenance.digitalcurrency.consortium"
    version = artifactVersion(this)

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

task<Exec>("generateDccSpec") {
    workingDir("scripts/swagger-generation")
    commandLine("node", "generator.mjs")
}

tasks.htmlDependencyReport {
    projects = project.subprojects
}

subprojects {
    project.ext.properties["kotlin_version"] = Versions.Kotlin

    apply {
        plugin(PluginIds.Kotlin)
        plugin(PluginIds.Idea)
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

    val testListener = CustomTestLoggingListener(project)
    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty("spring.profiles.active", "development")
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = true
        }
        addTestListener(testListener)
        doLast {
            testListener.printStats(project)
        }
    }

    dependencies {
        ktlint(Libraries.KtLint)
    }

    if (project.name == "report" || project.name == "service") {
        dependencies {
            api.let {
                it(Libraries.LogbackCore)
                it(Libraries.LogbackClassic)
                it(Libraries.LogbackJackson)
            }

            implementation.let {
                it(Libraries.KotlinAllOpen)
                it(Libraries.KotlinReflect)
                it(Libraries.KotlinStdlib)
                it(Libraries.KotlinStdlibJdk8)

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

                it(Libraries.Postgres)

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
                exceptionFormat = FULL
            }
        }
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
