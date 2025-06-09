package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "daily_weather_forecasts", indexes = [
        Index(name = "idx_weather_forecast_date_region", columnList = "forecast_date, region_code")
    ]
)
data class DailyWeatherForecast(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "log_entry_id",
        nullable = true,
        foreignKey = ForeignKey(name = "fk_weather_forecast_log_entry")
    ) // nullable = false -> true 로 변경
    var logEntry: WeatherApiLog? = null,

    @Column(name = "region_code", nullable = false)
    val regionCode: String,

    @Column(name = "region_name", nullable = true)
    val regionName: String?,

    @Column(name = "forecast_date", nullable = false)
    val forecastDate: LocalDate,

    @Column(name = "min_temp", nullable = true)
    var minTemp: Int? = null,

    @Column(name = "max_temp", nullable = true)
    var maxTemp: Int? = null,

    @Column(name = "weather_am", length = 100, nullable = true)
    var weatherAm: String? = null,

    @Column(name = "weather_pm", length = 100, nullable = true)
    var weatherPm: String? = null,

    @Column(name = "rain_prob_am", nullable = true)
    var rainProbAm: Int? = null,

    @Column(name = "rain_prob_pm", nullable = true)
    var rainProbPm: Int? = null,

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