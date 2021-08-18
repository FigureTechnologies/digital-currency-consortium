package io.provenance.digitalcurrency.consortium

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone

@SpringBootApplication(
    scanBasePackages = ["io.provenance.digitalcurrency.consortium"],
    exclude = [
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
        org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration::class
    ]
)
@EnableScheduling
class Application

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    SpringApplication.run(Application::class.java, *args)
}
