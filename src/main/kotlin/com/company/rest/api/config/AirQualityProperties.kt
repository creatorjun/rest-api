package com.company.rest.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "airkorea")
data class AirQualityProperties(
    val serviceKey: String,
    val baseUrl: String = "https://api.odcloud.kr/api/MinuDustFrcstDspthSvrc/v1"
)