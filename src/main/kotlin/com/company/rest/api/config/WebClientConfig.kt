package com.company.rest.api.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    @Qualifier("naverWebClient")
    fun naverWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://openapi.naver.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    @Bean
    @Qualifier("kakaoWebClient")
    fun kakaoWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://kapi.kakao.com")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    @Bean
    @Qualifier("weatherKitWebClient")
    fun weatherKitWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://weatherkit.apple.com")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    @Bean
    @Qualifier("airKoreaWebClient")
    fun airKoreaWebClient(airQualityProperties: AirQualityProperties): WebClient {
        return WebClient.builder()
            .baseUrl(airQualityProperties.baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    @Bean
    @Qualifier("holidayApiWebClient")
    fun holidayApiWebClient(holidayApiProperties: HolidayApiProperties): WebClient {
        return WebClient.builder()
            .baseUrl(holidayApiProperties.baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}