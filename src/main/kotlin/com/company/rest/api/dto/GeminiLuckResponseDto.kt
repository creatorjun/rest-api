package com.company.rest.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GeminiLuckResponseDto(
    @JsonProperty("띠별운세")
    val zodiacLucks: List<ZodiacLuckDetailDto>? = null
)