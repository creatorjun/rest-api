package com.company.rest.api

import com.company.rest.api.config.AirQualityProperties
import com.company.rest.api.config.AppleWeatherProperties
import com.company.rest.api.config.HolidayApiProperties
import com.company.rest.api.config.JwtProperties
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
    HolidayApiProperties::class
)
class RestApiApplication

fun main(args: Array<String>) {
    runApplication<RestApiApplication>(*args)
}