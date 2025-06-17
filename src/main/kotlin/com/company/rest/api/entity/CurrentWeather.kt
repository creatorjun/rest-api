package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "current_weather", uniqueConstraints = [
        UniqueConstraint(name = "uk_current_weather_location", columnNames = ["latitude", "longitude"])
    ]
)
data class CurrentWeather(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "latitude", nullable = false)
    val latitude: Double,

    @Column(name = "longitude", nullable = false)
    val longitude: Double,

    @Column(name = "measured_at", nullable = false)
    var measuredAt: LocalDateTime,

    @Column(name = "temperature", nullable = false)
    var temperature: Double,

    @Column(name = "apparent_temperature", nullable = false)
    var apparentTemperature: Double,

    @Column(name = "condition_code", nullable = false)
    var conditionCode: String,

    @Column(name = "humidity", nullable = false)
    var humidity: Double,

    @Column(name = "wind_speed", nullable = false)
    var windSpeed: Double,

    @Column(name = "uv_index", nullable = false)
    var uvIndex: Int,

    @Column(name = "pm10_grade", nullable = true)
    var pm10Grade: String? = null,

    @Column(name = "pm25_grade", nullable = true)
    var pm25Grade: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}