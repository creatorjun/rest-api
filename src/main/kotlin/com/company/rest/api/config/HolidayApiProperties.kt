package com.company.rest.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kapi")
data class HolidayApiProperties(
    @Value("\${kapi.service.key}")
    val serviceKey: String,
    val baseUrl: String = "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService"
)