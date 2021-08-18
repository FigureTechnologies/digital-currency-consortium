package io.provenance.digitalcurrency.consortium.config

import cosmos.base.abci.v1beta1.Abci
import io.grpc.StatusRuntimeException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import javax.servlet.http.HttpServletRequest

class PbcException(private val msg: String, private val txResponse: Abci.TxResponse) : Exception(msg) {
    override fun toString(): String =
        """
            Error message: $msg
            Error Code: ${txResponse.code}
            Raw log: ${txResponse.rawLog}
        """.trimIndent()
}

data class ErrorMessage(val errors: List<String>)

fun Throwable.toErrorMessage(override: String? = null) =
    ErrorMessage(listOf("${this::class.simpleName}: ${override ?: this.message}"))

private const val IDENTITY_HEADER_KEY = "x-uuid"
private fun HttpServletRequest.userWithIdentity() = "User with identity \"${this.getHeader(IDENTITY_HEADER_KEY)}\""

@ControllerAdvice
class GlobalControllerAdvice : ResponseEntityExceptionHandler() {
    private val log by lazy { logger() }

    @ExceptionHandler(IllegalStateException::class)
    @ResponseBody
    fun handleIllegalStateException(thrown: IllegalStateException, request: HttpServletRequest): ResponseEntity<*> {
        log.warn("${request.userWithIdentity()} illegal state encountered for input to ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseBody
    fun handleIllegalArgumentException(thrown: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<*> {
        log.warn("${request.userWithIdentity()} sent illegal input to ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(StatusRuntimeException::class)
    @ResponseBody
    fun handleStatusRuntimeGrpcException(thrown: StatusRuntimeException, request: HttpServletRequest): ResponseEntity<*> {
        log.error("${request.userWithIdentity()} grpc status runtime error encountered while calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(PbcException::class)
    @ResponseBody
    fun handlePbcException(thrown: PbcException, request: HttpServletRequest): ResponseEntity<*> {
        log.error("${request.userWithIdentity()} pbc error encountered while calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(Exception::class)
    @ResponseBody
    fun handleException(thrown: Exception, request: HttpServletRequest): ResponseEntity<*> {
        log.error("${request.userWithIdentity()} prov error encountered while calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    override fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException, headers: HttpHeaders, status: HttpStatus, request: WebRequest): ResponseEntity<Any> {
        log.info(request.toString())
        return ResponseEntity(ex.message.toString(), HttpStatus.BAD_REQUEST)
    }
}
