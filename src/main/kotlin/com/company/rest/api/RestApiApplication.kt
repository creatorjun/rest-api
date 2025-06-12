package com.company.rest.api

import com.company.rest.api.config.AppleWeatherProperties // 임포트 추가
import com.company.rest.api.config.JwtProperties // 임포트 추가
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties // 임포트 추가
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, AppleWeatherProperties::class) // 어노테이션 추가
class RestApiApplication

fun main(args: Array<String>) {
    runApplication<RestApiApplication>(*args)
}