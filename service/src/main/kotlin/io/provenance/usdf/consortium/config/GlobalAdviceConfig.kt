package io.provenance.usdf.consortium.config

import feign.FeignException
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

class NotFoundException(msg: String) : Exception(msg)
class PbcException(msg: String) : Exception(msg)

data class ErrorMessage(val errors: List<String>)

fun Throwable.toErrorMessage(override: String? = null) =
    ErrorMessage(listOf("${this::class.simpleName}: ${override ?: this.message}"))

private const val IDENTITY_HEADER_KEY = "x-uuid"
private fun HttpServletRequest.userWithIdentity() = "User with identity \"${this.getHeader(IDENTITY_HEADER_KEY)}\""

@ControllerAdvice
class GlobalControllerAdvice : ResponseEntityExceptionHandler() {

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseBody
    fun handleIllegalArgumentException(thrown: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<*> {
        logger().warn("${request.userWithIdentity()} sent illegal input to ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(NotFoundException::class)
    @ResponseBody
    fun handleNotFoundException(thrown: NotFoundException, request: HttpServletRequest): ResponseEntity<*> {
        logger().warn("${request.userWithIdentity()} not found error calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(FeignException::class)
    @ResponseBody
    fun handleFeignException(thrown: FeignException, request: HttpServletRequest): ResponseEntity<*> {
        logger().error("${request.userWithIdentity()} error encountered while calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(StatusRuntimeException::class)
    @ResponseBody
    fun handleStatusRuntimeGrpcException(thrown: StatusRuntimeException, request: HttpServletRequest): ResponseEntity<*> {
        logger().error("${request.userWithIdentity()} grpc status runtime error encountered while calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(PbcException::class)
    @ResponseBody
    fun handlePbcException(thrown: PbcException, request: HttpServletRequest): ResponseEntity<*> {
        logger().error("${request.userWithIdentity()} pbc error encountered while calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(Exception::class)
    @ResponseBody
    fun handleException(thrown: Exception, request: HttpServletRequest): ResponseEntity<*> {
        logger().error("${request.userWithIdentity()} prov error encountered while calling ${request.requestURI}", thrown)
        return ResponseEntity(thrown.toErrorMessage(), HttpStatus.INTERNAL_SERVER_ERROR)
    }

    override fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException, headers: HttpHeaders, status: HttpStatus, request: WebRequest): ResponseEntity<Any> {
        logger().info(request.toString())
        return ResponseEntity(ex.message.toString(), HttpStatus.BAD_REQUEST)
    }
}
