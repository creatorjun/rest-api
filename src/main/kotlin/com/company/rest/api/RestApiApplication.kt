package com.company.rest.api

import com.company.rest.api.config.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(
    JwtProperties::class,
    AppleWeatherProperties::class,
    AirQualityProperties::class,
    HolidayApiProperties::class,
    LocationConfig::class
)
class RestApiApplication

fun main(args: Array<String>) {
    runApplication<RestApiApplication>(*args)
}