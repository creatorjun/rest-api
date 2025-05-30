package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

data class AppPasswordVerificationRequestDto(
    @field:NotBlank(message = "앱 비밀번호는 비워둘 수 없습니다.")
    val appPassword: String
)