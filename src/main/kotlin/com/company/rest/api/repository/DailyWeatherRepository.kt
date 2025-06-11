package com.company.rest.api.repository

import com.company.rest.api.entity.DailyWeatherForecast
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

// DailyWeatherForecast 엔티티를 다루므로 클래스명은 DailyWeatherForecastRepository로 변경하는 것을 추천하지만,
// 일단 기존 파일명을 유지하며 내용만 수정합니다.
@Repository
interface DailyWeatherForecastRepository : JpaRepository<DailyWeatherForecast, String> {

    /**
     * 특정 위도와 경도에 해당하는 모든 일일 예보 정보 목록을 예보 날짜 오름차순으로 조회합니다.
     * @param latitude 조회할 위도
     * @param longitude 조회할 경도
     * @return List<DailyWeatherForecast>
     */
    fun findByLatitudeAndLongitudeOrderByForecastDateAsc(
        latitude: Double,
        longitude: Double
    ): List<DailyWeatherForecast>

    /**
     * 특정 위도와 경도에 해당하는 모든 일일 예보 정보를 삭제합니다.
     * @param latitude 삭제할 위도
     * @param longitude 삭제할 경도
     */
    @Transactional
    @Modifying
    fun deleteAllByLatitudeAndLongitude(latitude: Double, longitude: Double)
}