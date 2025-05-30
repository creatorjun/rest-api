package com.company.rest.api.dto

import com.company.rest.api.entity.DailyWeatherForecast
import java.time.format.DateTimeFormatter

/**
 * 클라이언트에게 반환될 일일 날씨 예보 정보를 담는 DTO 입니다.
 */
data class WeatherForecastResponseDto(
    val date: String,             // 예보 날짜 (YYYY-MM-DD 형식)
    val regionCode: String,       // 예보 지역 코드
    val regionName: String?,      // 예보 지역 이름 (예: "서울")
    val minTemp: Int?,            // 최저 기온 (℃)
    val maxTemp: Int?,            // 최고 기온 (℃)
    val weatherAm: String?,       // 오전 날씨 상태 (예: "맑음", "구름많음")
    val weatherPm: String?,       // 오후 날씨 상태
    val rainProbAm: Int?,         // 오전 강수 확률 (%)
    val rainProbPm: Int?          // 오후 강수 확률 (%)
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // YYYY-MM-DD

        /**
         * DailyWeatherForecast 엔티티를 WeatherForecastResponseDto로 변환합니다.
         */
        fun fromEntity(entity: DailyWeatherForecast): WeatherForecastResponseDto {
            return WeatherForecastResponseDto(
                date = entity.forecastDate.format(dateFormatter),
                regionCode = entity.regionCode,
                regionName = entity.regionName,
                minTemp = entity.minTemp,
                maxTemp = entity.maxTemp,
                weatherAm = entity.weatherAm,
                weatherPm = entity.weatherPm,
                rainProbAm = entity.rainProbAm,
                rainProbPm = entity.rainProbPm
            )
        }
    }
}

/**
 * 특정 지역의 주간(또는 여러 날짜의) 날씨 예보 목록을 담는 응답 DTO 입니다.
 */
data class WeeklyForecastResponseDto(
    val regionCode: String,
    val regionName: String?,
    val forecasts: List<WeatherForecastResponseDto> // 일별 예보 DTO 목록
)