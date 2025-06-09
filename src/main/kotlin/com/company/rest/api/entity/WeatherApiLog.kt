package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "weather_api_logs", uniqueConstraints = [
        UniqueConstraint(name = "uk_weather_log_base_datetime_region", columnNames = ["base_date_time", "region_code"])
    ]
)
data class WeatherApiLog(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "base_date_time", nullable = false)
    val baseDateTime: LocalDateTime, // API 호출의 기준이 되는 발표 시각 (tmFc 값에 해당, 예: 2023-10-27 06:00:00)

    @Column(name = "region_code", nullable = false)
    val regionCode: String, // 예보를 요청한 주 지역 코드 (예: 특정 도시의 기온예보구역코드)

    @Column(name = "request_params_land", columnDefinition = "TEXT", nullable = true)
    var requestParamsLand: String? = null, // 중기육상예보 API 요청 파라미터 (JSON 문자열)

    @Column(name = "raw_response_land", columnDefinition = "TEXT", nullable = true)
    var rawResponseLand: String? = null, // 중기육상예보 API로부터 받은 원본 응답 (JSON 문자열)

    @Column(name = "request_params_temp", columnDefinition = "TEXT", nullable = true)
    var requestParamsTemp: String? = null, // 중기기온예보 API 요청 파라미터 (JSON 문자열)

    @Column(name = "raw_response_temp", columnDefinition = "TEXT", nullable = true)
    var rawResponseTemp: String? = null, // 중기기온예보 API로부터 받은 원본 응답 (JSON 문자열)

    @Enumerated(EnumType.STRING)
    @Column(name = "api_call_status", nullable = false)
    var apiCallStatus: WeatherApiCallStatus = WeatherApiCallStatus.PENDING, // API 호출 및 파싱 상태

    @OneToMany(mappedBy = "logEntry", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var dailyForecasts: MutableList<DailyWeatherForecast> = mutableListOf(), // 이 로그에 의해 생성된 일일 예보 목록

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    // 양방향 연관관계 편의 메소드
    fun addDailyWeatherForecast(forecast: DailyWeatherForecast) {
        dailyForecasts.add(forecast)
        forecast.logEntry = this
    }

    fun removeDailyWeatherForecast(forecast: DailyWeatherForecast) {
        dailyForecasts.remove(forecast)
        forecast.logEntry = null
    }
}