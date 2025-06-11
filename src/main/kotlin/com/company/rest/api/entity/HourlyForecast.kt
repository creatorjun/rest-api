package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "hourly_forecasts", uniqueConstraints = [
        // 각 위치(위도/경도)별로 하나의 1시간 예보 정보만 있도록 유니크 제약조건 설정
        UniqueConstraint(name = "uk_hourly_forecast_location", columnNames = ["latitude", "longitude"])
    ]
)
data class HourlyForecast(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    // 위치 정보
    @Column(name = "latitude", nullable = false)
    val latitude: Double,

    @Column(name = "longitude", nullable = false)
    val longitude: Double,

    // 예보 요약 (예: "앞으로 15분 동안 약한 비")
    @Column(name = "summary", length = 1000, nullable = true)
    var summary: String?,

    // 분 단위 예보 데이터 (JSON 배열 문자열)
    @Column(name = "minutes_json", columnDefinition = "TEXT", nullable = true)
    var minutesJson: String?,

    // 이 예보 데이터의 만료 시간
    @Column(name = "forecast_expire_time", nullable = false)
    var forecastExpireTime: LocalDateTime,

    // 데이터가 DB에 마지막으로 업데이트된 시각
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}