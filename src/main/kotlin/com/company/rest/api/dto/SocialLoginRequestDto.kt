// src/main/kotlin/com/company/rest/api/dto/SocialLoginRequestDto.kt
package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

data class SocialLoginRequestDto(

    @field:NotBlank(message = "ID는 비워둘 수 없습니다.")
    val id: String,

    val nickname: String?,

    val platform: LoginProvider,

    @field:NotBlank(message = "소셜 액세스 토큰은 비워둘 수 없습니다.")
    val socialAccessToken: String
)