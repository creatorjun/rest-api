package com.company.rest.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 최상위 응답 구조
@JsonIgnoreProperties(ignoreUnknown = true)
data class WeatherKitResponse(
    val currentWeather: CurrentWeatherDto?,
    val forecastDaily: DailyForecastDto?,
    val forecastNextHour: HourlyForecastDto?
)

// 메타데이터
@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaDataDto(
    @JsonProperty("expireTime")
    val expireTime: String
)

// 현재 날씨 정보를 담는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
data class CurrentWeatherDto(
    @JsonProperty("metadata")
    val metadata: MetaDataDto,
    val asOf: String,
    val temperature: Double,
    val temperatureApparent: Double,
    val conditionCode: String,
    val humidity: Double,
    val wind: WindDto?,
    val uvIndex: Int
)

// 바람 정보를 담는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
data class WindDto(
    val speed: Double,
    val gust: Double?,
    val direction: Int?
)

// 1시간 예보 (forecastNextHour) DTO
@JsonIgnoreProperties(ignoreUnknown = true)
data class HourlyForecastDto(
    @JsonProperty("metadata")
    val metadata: MetaDataDto,
    val summary: List<ForecastPeriodSummary>?,
    val minutes: List<MinuteForecastDto>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ForecastPeriodSummary(
    val startTime: String,
    val condition: String,
    val probability: Double
)

// 일일 예보 (forecastDaily) DTO
@JsonIgnoreProperties(ignoreUnknown = true)
data class DailyForecastDto(
    @JsonProperty("metadata")
    val metadata: MetaDataDto,
    val days: List<DayWeatherForecastDto>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DayWeatherForecastDto(
    val forecastStart: String,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val conditionCode: String,
    val precipitationChance: Double,
    val precipitationAmount: Double,
    val humidity: Double,
    val wind: WindDto?,
    val uvIndex: UvIndexDto?,
    val sunrise: String?,
    val sunset: String?,
    val daytimeForecast: DayPartForecastDto?,
    val overnightForecast: DayPartForecastDto?
)

// 주간/야간 예보 상세 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
data class DayPartForecastDto(
    val conditionCode: String,
    val precipitationChance: Double,
    val precipitationAmount: Double,
    val wind: WindDto?
)

// UV 지수 상세 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
data class UvIndexDto(
    val value: Int,
    val category: String
)