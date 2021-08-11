package io.provenance.digitalcurrency.consortium.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.Contact
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2

@Configuration
@EnableSwagger2
class SwaggerConfig {

    @Bean
    fun api(): Docket {
        val contact = Contact("Provenance", "provenance.io", "info@provenance.io")

        val info = ApiInfoBuilder()
            .title("Digital Currency Consortium Middleware")
            .description("Middleware for banks to use digital currency smart contracts.")
            .version("1")
            .contact(contact)
            .build()

        return Docket(DocumentationType.SWAGGER_2)
            .apiInfo(info)
            .forCodeGeneration(true)
            .select()
            .apis(RequestHandlerSelectors.basePackage("io.provenance.digitalcurrency.consortium.web"))
            .build()
    }
}
