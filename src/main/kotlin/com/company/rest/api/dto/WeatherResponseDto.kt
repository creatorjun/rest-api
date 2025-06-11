package com.company.rest.api.dto

import com.company.rest.api.entity.CurrentWeather
import com.company.rest.api.entity.DailyWeatherForecast
import com.company.rest.api.entity.HourlyForecast
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.format.DateTimeFormatter

// --- 이 파일 하나에 모든 날씨 관련 응답 DTO를 정의합니다. ---

// 최종 응답을 하나로 묶어줄 최상위 DTO
data class WeatherResponseDto(
    val currentWeather: CurrentWeatherResponseDto?,
    val hourlyForecast: HourlyForecastResponseDto?,
    val dailyForecast: List<DailyWeatherForecastResponseDto>?
)

// '현재 날씨' 응답용 DTO
data class CurrentWeatherResponseDto(
    val measuredAt: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val conditionCode: String,
    val humidity: Double,
    val windSpeed: Double,
    val uvIndex: Int
) {
    companion object {
        fun fromEntity(entity: CurrentWeather): CurrentWeatherResponseDto {
            return CurrentWeatherResponseDto(
                measuredAt = entity.measuredAt.format(DateTimeFormatter.ISO_DATE_TIME),
                temperature = entity.temperature,
                apparentTemperature = entity.apparentTemperature,
                conditionCode = entity.conditionCode,
                humidity = entity.humidity,
                windSpeed = entity.windSpeed,
                uvIndex = entity.uvIndex
            )
        }
    }
}

// '1시간 예보' 응답용 DTO
data class HourlyForecastResponseDto(
    val summary: String?,
    val forecastExpireTime: String,
    val minutes: List<MinuteForecastDto>
) {
    companion object {
        fun fromEntity(entity: HourlyForecast, objectMapper: ObjectMapper): HourlyForecastResponseDto? {
            return try {
                val minuteList: List<MinuteForecastDto> = objectMapper.readValue(
                    entity.minutesJson ?: "[]",
                    object : TypeReference<List<MinuteForecastDto>>() {}
                )
                HourlyForecastResponseDto(
                    summary = entity.summary,
                    forecastExpireTime = entity.forecastExpireTime.format(DateTimeFormatter.ISO_DATE_TIME),
                    minutes = minuteList
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

// '일일 예보' 응답용 DTO
data class DailyWeatherForecastResponseDto(
    val date: String,
    val minTemp: Double?,
    val maxTemp: Double?,
    val weatherAm: String?,
    val weatherPm: String?,
    val rainProb: Double?,
    val humidity: Double?,
    val windSpeed: Double?,
    val uvIndex: Int?,
    val sunrise: String?,
    val sunset: String?
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun fromEntity(entity: DailyWeatherForecast): DailyWeatherForecastResponseDto {
            return DailyWeatherForecastResponseDto(
                date = entity.forecastDate.format(dateFormatter),
                minTemp = entity.minTemp,
                maxTemp = entity.maxTemp,
                weatherAm = entity.weatherAm,
                weatherPm = entity.weatherPm,
                rainProb = entity.rainProb,
                humidity = entity.humidity,
                windSpeed = entity.windSpeed,
                uvIndex = entity.uvIndex,
                sunrise = entity.sunrise,
                sunset = entity.sunset
            )
        }
    }
}

// 1시간 예보의 분 단위 데이터를 담는 DTO
data class MinuteForecastDto(
    val startTime: String,
    val precipitationChance: Double,
    val precipitationIntensity: Double
)