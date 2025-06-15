package com.company.rest.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kapi")
data class AirQualityProperties(
    @Value("\${kapi.service.key}")
    val serviceKey: String,
    val baseUrl: String = "https://api.odcloud.kr/api/MinuDustFrcstDspthSvrc/v1"
)