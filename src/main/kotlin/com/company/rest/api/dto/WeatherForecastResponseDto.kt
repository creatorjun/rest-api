package com.company.rest.api.dto

import com.company.rest.api.entity.DailyWeatherForecast
import java.time.format.DateTimeFormatter

/**
 * 클라이언트에게 반환될 일일 날씨 예보 정보를 담는 DTO 입니다.
 */
data class WeatherForecastResponseDto(
    val date: String,             // 예보 날짜 (YYYY-MM-DD 형식)
    val minTemp: Double?,         // 최저 기온 (℃)
    val maxTemp: Double?,         // 최고 기온 (℃)
    val weatherAm: String?,       // 오전 날씨 상태 (예: "맑음", "구름많음")
    val weatherPm: String?,       // 오후 날씨 상태
    val rainProb: Double?,        // 강수 확률 (%)
    val humidity: Double?,        // 습도 (%)
    val windSpeed: Double?,       // 풍속 (km/h)
    val uvIndex: Int?,            // 자외선 지수
    val sunrise: String?,         // 일출 시간 (ISO 8601 형식)
    val sunset: String?           // 일몰 시간 (ISO 8601 형식)
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // YYYY-MM-DD

        /**
         * DailyWeatherForecast 엔티티를 WeatherForecastResponseDto로 변환합니다.
         */
        fun fromEntity(entity: DailyWeatherForecast): WeatherForecastResponseDto {
            return WeatherForecastResponseDto(
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

/**
 * 특정 위치의 여러 날짜의 날씨 예보 목록을 담는 응답 DTO 입니다.
 */
data class WeeklyForecastResponseDto(
    val latitude: Double,
    val longitude: Double,
    val forecasts: List<WeatherForecastResponseDto> // 일별 예보 DTO 목록
)