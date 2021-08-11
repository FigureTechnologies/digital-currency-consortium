import org.gradle.api.Project

fun Project.artifactVersion(project: Project): String = project.findProperty("artifactVersion")?.toString() ?: "1.0-SNAPSHOT"