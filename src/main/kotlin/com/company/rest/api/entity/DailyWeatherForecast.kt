package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "daily_weather_forecasts", indexes = [
        // 위치 기반 조회를 위해 위도와 경도에 인덱스 추가
        Index(name = "idx_weather_location_date", columnList = "latitude, longitude, forecast_date")
    ]
)
data class DailyWeatherForecast(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    // 위치 정보를 위도와 경도로 저장
    @Column(name = "latitude", nullable = false)
    val latitude: Double,

    @Column(name = "longitude", nullable = false)
    val longitude: Double,

    // regionName 필드 삭제됨

    @Column(name = "forecast_date", nullable = false)
    val forecastDate: LocalDate,

    @Column(name = "min_temp", nullable = true)
    var minTemp: Double? = null,

    @Column(name = "max_temp", nullable = true)
    var maxTemp: Double? = null,

    @Column(name = "weather_am", length = 100, nullable = true)
    var weatherAm: String? = null,

    @Column(name = "weather_pm", length = 100, nullable = true)
    var weatherPm: String? = null,

    @Column(name = "rain_prob", nullable = true)
    var rainProb: Double? = null,

    @Column(name = "humidity", nullable = true)
    var humidity: Double? = null,

    @Column(name = "wind_speed", nullable = true)
    var windSpeed: Double? = null,

    @Column(name = "uv_index", nullable = true)
    var uvIndex: Int? = null,

    @Column(name = "sunrise", nullable = true)
    var sunrise: String? = null,

    @Column(name = "sunset", nullable = true)
    var sunset: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}