import org.gradle.api.Project
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.logging.text.StyledTextOutput.Style
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.serviceOf

class CustomTestLoggingListener(
    project: Project
): TestListener {

    private val output = project.serviceOf<StyledTextOutputFactory>().create("colored-test-output")

    private val testResults = mutableMapOf<TestDescriptor, TestResult>()

    init {
        System.setProperty("org.gradle.color.failure", "RED")
        System.setProperty("org.gradle.color.progressstatus", "YELLOW")
        System.setProperty("org.gradle.color.success", "GREEN")
        output.style(Style.Normal)
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {
        return
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent != null) { // will match the outermost suite
            output
                .style(Style.Normal)
                .println(
                "Results: ${result.resultType} " +
                    "(${result.testCount} tests, " +
                    "${result.successfulTestCount} successes, " +
                    "${result.failedTestCount} failures, " +
                    "${result.skippedTestCount} skipped, " +
                    String.format("%.2fs runtime)", duration(result) / 1000.0)
            )
        }
    }

    override fun beforeSuite(suite: TestDescriptor) {
        if (suite.name.startsWith("Test Run") || suite.name.startsWith("Gradle Worker"))
            return
        else
            output
                .style(Style.Normal)
                .println("\n${suite.name}")
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        val skipped = result.skippedTestCount > 0
        testResults.putIfAbsent(testDescriptor, result)
        output
            .style(Style.Normal)
            .text(" > ")
            .style(styleBasedOnResult(result))
            .text(testDescriptor.name)
            .text(" (")
            .text(if (skipped) "SKIPPED" else String.format("%.2fs", duration(result) / 1000.0))
            .println(")")
    }

    fun printStats(project: Project) {
        output
            .style(Style.Normal)
            .text("Slowest [${project.displayName}]:")
            .println()

        testResults.toSortedMap(compareBy {
            testResults[it]?.let { -1 * duration(it) }
        }).toList().take(5).forEach {
            output
                .style(Style.Normal)
                .text(" > ")
                .style(Style.ProgressStatus)
                .text("${it.first.displayName} => ")
                .text(String.format("%.2fs", duration(it.second) / 1000.0))
                .println()
        }
    }

    private fun styleBasedOnResult(result: TestResult): Style {
        return when {
            result.failedTestCount > 0 -> Style.Failure
            result.skippedTestCount > 0 -> Style.ProgressStatus
            else -> Style.Success
        }
    }

    private fun duration(result: TestResult): Long {
        return result.endTime - result.startTime
    }
}