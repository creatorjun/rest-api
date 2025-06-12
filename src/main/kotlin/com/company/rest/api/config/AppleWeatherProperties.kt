package com.company.rest.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apple.weather")
data class AppleWeatherProperties(
    val teamId: String,
    val keyId: String,
    val serviceId: String,
    val keyPath: String
)