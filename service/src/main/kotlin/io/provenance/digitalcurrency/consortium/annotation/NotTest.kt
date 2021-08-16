package io.provenance.digitalcurrency.consortium.annotation

import org.springframework.context.annotation.Profile

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Profile("!test & !pbctest")
annotation class NotTest
