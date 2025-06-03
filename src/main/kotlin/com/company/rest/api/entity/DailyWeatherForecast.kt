package com.company.rest.api.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "daily_weather_forecasts", indexes = [
    Index(name = "idx_weather_forecast_date_region", columnList = "forecast_date, region_code")
])
data class DailyWeatherForecast(
    @Id
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_entry_id", nullable = false, foreignKey = ForeignKey(name = "fk_weather_forecast_log_entry"))
    var logEntry: WeatherApiLog? = null, // 이 예보를 가져온 API 호출 로그 (WeatherApiLog 엔티티와 연결)

    @Column(name = "region_code", nullable = false)
    val regionCode: String, // 기상청 예보 구역 코드 (API 조회 시 사용된 regId)

    @Column(name = "region_name", nullable = true)
    val regionName: String?, // 지역 이름 (예: "서울", "부산" 등, 편의를 위해 추가)

    @Column(name = "forecast_date", nullable = false)
    val forecastDate: LocalDate, // 예보 대상 날짜

    @Column(name = "min_temp", nullable = true)
    var minTemp: Int?, // 최저 기온 (섭씨)

    @Column(name = "max_temp", nullable = true)
    var maxTemp: Int?, // 최고 기온 (섭씨)

    @Column(name = "weather_am", length = 100, nullable = true)
    var weatherAm: String?, // 오전 날씨 상태 (예: "맑음", "구름많음", "흐리고 비")

    @Column(name = "weather_pm", length = 100, nullable = true)
    var weatherPm: String?, // 오후 날씨 상태

    @Column(name = "rain_prob_am", nullable = true)
    var rainProbAm: Int?, // 오전 강수 확률 (%)

    @Column(name = "rain_prob_pm", nullable = true)
    var rainProbPm: Int?, // 오후 강수 확률 (%)

    // 참고: API가 8~10일차 예보는 오전/오후 구분 없이 제공할 경우,
    // weatherAm/rainProbAm에 해당 값을 저장하고 weatherPm/rainProbPm은 null로 두거나
    // 동일한 값을 저장하는 방식으로 서비스 로직에서 처리할 수 있습니다.

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