package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

data class FcmTokenRequestDto(
    @field:NotBlank(message = "FCM 토큰은 비워둘 수 없습니다.")
    val fcmToken: String
)