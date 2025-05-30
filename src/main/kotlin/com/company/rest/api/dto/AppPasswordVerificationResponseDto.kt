package com.company.rest.api.dto

data class AppPasswordVerificationResponseDto(
    val isVerified: Boolean,
    val message: String
)