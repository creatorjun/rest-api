package com.company.rest.api.repository

import com.company.rest.api.entity.HourlyForecast
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface HourlyForecastRepository : JpaRepository<HourlyForecast, String> {

    /**
     * 특정 위도와 경도에 해당하는 1시간 예보 정보를 조회합니다.
     * @param latitude 조회할 위도
     * @param longitude 조회할 경도
     * @return Optional<HourlyForecast>
     */
    fun findByLatitudeAndLongitude(latitude: Double, longitude: Double): Optional<HourlyForecast>

    /**
     * 특정 위도와 경도에 해당하는 1시간 예보 정보를 모두 삭제합니다.
     * (유니크 제약조건 때문에 최대 1개만 삭제됨)
     * @param latitude 삭제할 위도
     * @param longitude 삭제할 경도
     */
    @Transactional
    @Modifying
    fun deleteByLatitudeAndLongitude(latitude: Double, longitude: Double)
}