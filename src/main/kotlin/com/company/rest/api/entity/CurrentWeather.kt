package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "current_weather", uniqueConstraints = [
        // 각 위치(위도/경도)별로 하나의 현재 날씨 정보만 있도록 유니크 제약조건 설정
        UniqueConstraint(name = "uk_current_weather_location", columnNames = ["latitude", "longitude"])
    ]
)
data class CurrentWeather(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    // 위치 정보
    @Column(name = "latitude", nullable = false)
    val latitude: Double,

    @Column(name = "longitude", nullable = false)
    val longitude: Double,

    // regionName 필드 삭제됨

    // 날씨 데이터가 측정된 시각 (WeatherKit의 asOf 필드에 해당)
    @Column(name = "measured_at", nullable = false)
    var measuredAt: LocalDateTime,

    // 현재 기온 (℃)
    @Column(name = "temperature", nullable = false)
    var temperature: Double,

    // 현재 체감 온도 (℃)
    @Column(name = "apparent_temperature", nullable = false)
    var apparentTemperature: Double,

    // 현재 날씨 상태 코드 (예: "Cloudy")
    @Column(name = "condition_code", nullable = false)
    var conditionCode: String,

    // 현재 습도 (0.0 ~ 1.0)
    @Column(name = "humidity", nullable = false)
    var humidity: Double,

    // 현재 풍속 (km/h)
    @Column(name = "wind_speed", nullable = false)
    var windSpeed: Double,

    // 현재 자외선 지수
    @Column(name = "uv_index", nullable = false)
    var uvIndex: Int,

    // 데이터가 DB에 마지막으로 업데이트된 시각
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}