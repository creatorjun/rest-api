package com.company.rest.api.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

/**
 * 애플리케이션 전역의 예외를 처리하는 핸들러입니다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * 클라이언트에게 반환될 표준 에러 응답 형식입니다.
     */
    data class ErrorResponse(
        val status: Int,
        val code: String,
        val message: String,
        val errors: List<FieldErrorDetail> = emptyList()
    )

    data class FieldErrorDetail(
        val field: String,
        val value: Any?,
        val reason: String?
    )

    /**
     * 서비스 로직에서 발생하는 커스텀 예외를 처리합니다.
     */
    @ExceptionHandler(CustomException::class)
    protected fun handleCustomException(e: CustomException): ResponseEntity<ErrorResponse> {
        val errorCode = e.errorCode
        logger.warn("CustomException occurred: code={}, message='{}'", errorCode.name, errorCode.message)
        val response = ErrorResponse(
            status = errorCode.status.value(),
            code = errorCode.name,
            message = errorCode.message
        )
        return ResponseEntity(response, errorCode.status)
    }

    /**
     * @Valid 어노테이션을 통한 유효성 검증 실패 시 발생하는 예외를 처리합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    protected fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        logger.warn("MethodArgumentNotValidException occurred: {}", e.message)
        val fieldErrors = e.bindingResult.fieldErrors.map {
            FieldErrorDetail(
                field = it.field,
                value = it.rejectedValue,
                reason = it.defaultMessage
            )
        }
        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            code = "INVALID_INPUT_VALUE",
            message = "입력값 유효성 검사에 실패했습니다.",
            errors = fieldErrors
        )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    /**
     * 기존 코드에서 사용되던 ResponseStatusException을 처리합니다.
     * 점진적인 리팩토링을 위해 남겨둡니다.
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
        logger.warn("ResponseStatusException occurred: status={}, reason='{}'", ex.statusCode, ex.reason)
        val response = ErrorResponse(
            status = ex.statusCode.value(),
            code = "RESPONSE_STATUS_EXCEPTION",
            message = ex.reason ?: "No reason provided"
        )
        return ResponseEntity(response, ex.statusCode)
    }


    /**
     * 위에서 처리되지 않은 모든 예외를 처리하는 최종 핸들러입니다.
     */
    @ExceptionHandler(Exception::class)
    protected fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception occurred: {}", e.message, e)
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        val response = ErrorResponse(
            status = errorCode.status.value(),
            code = errorCode.name,
            message = errorCode.message
        )
        return ResponseEntity(response, errorCode.status)
    }
}