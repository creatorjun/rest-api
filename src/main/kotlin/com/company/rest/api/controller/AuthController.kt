package com.company.rest.api.controller

import com.company.rest.api.dto.AuthResponseDto
import com.company.rest.api.dto.SocialLoginRequestDto // 수정된 DTO를 사용하는 것으로 간주
import com.company.rest.api.service.AuthService
import com.company.rest.api.service.SocialLoginService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

// Refresh Token 요청을 위한 DTO
data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String
)

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "사용자 인증 및 소셜 로그인 API")
class AuthController(
    private val socialLoginService: SocialLoginService,
    private val authService: AuthService // Access Token 재발급을 위한 서비스 주입
) {

    @Operation(
        summary = "소셜 로그인",
        description = "네이버 또는 카카오 계정으로 로그인/회원가입 후 서비스 JWT(Access Token, Refresh Token)를 발급합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "로그인 성공 및 JWT 발급",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AuthResponseDto::class))]
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 형식 (예: 필수 필드 누락, 유효하지 않은 platform 값)")
    @ApiResponse(responseCode = "401", description = "소셜 인증 실패 또는 사용자 ID 불일치")
    @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    @PostMapping("/social/login")
    fun socialLogin(
        @Valid @SwaggerRequestBody(description = "소셜 로그인 요청 정보 (id, nickname(선택), platform, socialAccessToken)") @RequestBody
        loginRequest: SocialLoginRequestDto // 타입은 SocialLoginRequestDto (내부적으로 변경된 필드명과 enum 처리 방식 적용됨)
    ): Mono<ResponseEntity<AuthResponseDto>> {
        return socialLoginService.processLogin(loginRequest)
            .map { authResponse -> ResponseEntity.ok(authResponse) }
        // 에러 처리는 SocialLoginService 또는 @ControllerAdvice에서 처리
    }

    @Operation(
        summary = "Access Token 재발급",
        description = "유효한 Refresh Token을 사용하여 새로운 Access Token 을 발급받습니다."
    )
    @ApiResponse(
        responseCode = "200", description = "새로운 Access Token 발급 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AuthResponseDto::class))]
    )
    @ApiResponse(responseCode = "401", description = "Refresh Token이 유효하지 않거나 만료됨")
    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @SwaggerRequestBody(description = "Refresh Token 요청 정보") @RequestBody
        request: RefreshTokenRequest
    ): Mono<ResponseEntity<AuthResponseDto>> {
        return authService.refreshAccessToken(request.refreshToken)
            .map { authResponse -> ResponseEntity.ok(authResponse) }
    }
}