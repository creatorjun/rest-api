package com.company.rest.api.repository

import com.company.rest.api.entity.CurrentWeather
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface CurrentWeatherRepository : JpaRepository<CurrentWeather, String> {

    /**
     * 특정 위도와 경도에 해당하는 현재 날씨 정보를 조회합니다.
     * @param latitude 조회할 위도
     * @param longitude 조회할 경도
     * @return Optional<CurrentWeather>
     */
    fun findByLatitudeAndLongitude(latitude: Double, longitude: Double): Optional<CurrentWeather>

    /**
     * 특정 위도와 경도에 해당하는 현재 날씨 정보를 모두 삭제합니다.
     * (유니크 제약조건 때문에 최대 1개만 삭제됨)
     * @param latitude 삭제할 위도
     * @param longitude 삭제할 경도
     */
    @Transactional
    @Modifying
    fun deleteByLatitudeAndLongitude(latitude: Double, longitude: Double)
}