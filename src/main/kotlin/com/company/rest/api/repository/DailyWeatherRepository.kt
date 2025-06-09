package com.company.rest.api.repository

import com.company.rest.api.entity.DailyWeatherForecast
import com.company.rest.api.entity.WeatherApiLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface DailyWeatherForecastRepository : JpaRepository<DailyWeatherForecast, String> {

    /**
     * 특정 지역 코드와 예보 날짜에 해당하는 일일 예보 정보를 조회합니다.
     *
     * @param regionCode 예보 구역 코드
     * @param forecastDate 예보 대상 날짜
     * @return Optional<DailyWeatherForecast>
     */
    fun findByRegionCodeAndForecastDate(regionCode: String, forecastDate: LocalDate): Optional<DailyWeatherForecast>

    /**
     * 특정 지역 코드와 특정 날짜 범위에 해당하는 일일 예보 정보 목록을 예보 날짜 오름차순으로 조회합니다.
     *
     * @param regionCode 예보 구역 코드
     * @param startDate 시작 날짜 (포함)
     * @param endDate 종료 날짜 (포함)
     * @return List<DailyWeatherForecast>
     */
    fun findByRegionCodeAndForecastDateBetweenOrderByForecastDateAsc(
        regionCode: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyWeatherForecast>

    /**
     * 특정 WeatherApiLog 항목에 연결된 모든 DailyWeatherForecast 항목들을 삭제합니다.
     * WeatherApiLog 엔티티에서 dailyForecasts 필드에 cascade = CascadeType.ALL, orphanRemoval = true가
     * 설정되어 있다면, Log 엔티티 삭제 시 자동으로 처리되므로 이 메소드가 직접 필요하지 않을 수 있습니다.
     * 다만, 특정 로그와 관련된 예보만 선별적으로 지워야 할 경우 유용할 수 있습니다.
     *
     * @param logEntry WeatherApiLog 엔티티
     * @return 삭제된 레코드 수
     */
    fun deleteAllByLogEntry(logEntry: WeatherApiLog): Int

    /**
     * 특정 지역 코드에 해당하는 모든 일일 예보 정보를 삭제합니다.
     * (주의: 특정 지역의 모든 과거 및 미래 예보 데이터를 삭제하므로 신중히 사용해야 합니다.)
     *
     * @param regionCode 예보 구역 코드
     * @return 삭제된 레코드 수
     */
    @Query("DELETE FROM DailyWeatherForecast dwf WHERE dwf.regionCode = :regionCode")
    fun deleteAllByRegionCode(@Param("regionCode") regionCode: String): Int
}