package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

data class AppPasswordRemoveRequestDto(
    @field:NotBlank(message = "현재 앱 비밀번호를 입력해주세요.")
    val currentAppPassword: String
)