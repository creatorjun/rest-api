package com.company.rest.api.security

import com.company.rest.api.exception.ErrorCode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    private val logger = LoggerFactory.getLogger(CustomAuthenticationEntryPoint::class.java)

    data class ErrorResponse(
        val status: Int,
        val code: String,
        val message: String
    )

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {

        val hasTokenError = request.getAttribute("token_error") as? Boolean ?: false

        val errorCode = if (hasTokenError) {
            logger.warn(
                "Authentication Entry Point: Invalid token error detected for {}. Responding with 401.",
                request.requestURI
            )
            ErrorCode.INVALID_TOKEN
        } else {
            logger.warn(
                "Authentication Entry Point: No token provided for {}. Responding with 403.",
                request.requestURI
            )
            ErrorCode.FORBIDDEN_ACCESS
        }

        val errorResponse = ErrorResponse(
            status = errorCode.status.value(),
            code = errorCode.name,
            message = errorCode.message
        )

        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}