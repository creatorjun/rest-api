package com.company.rest.api.repository

import com.company.rest.api.entity.WeatherApiCallStatus
import com.company.rest.api.entity.WeatherApiLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface WeatherApiLogRepository : JpaRepository<WeatherApiLog, String> {

    /**
     * 특정 발표 시각(baseDateTime)과 지역 코드(regionCode)에 해당하는 WeatherApiLog를 조회합니다.
     * 스케줄러 등에서 동일한 조건으로 이미 처리된 로그가 있는지 확인할 때 사용됩니다.
     *
     * @param baseDateTime API 발표 기준 시각
     * @param regionCode 예보를 요청한 주 지역 코드
     * @return Optional<WeatherApiLog>
     */
    fun findByBaseDateTimeAndRegionCode(baseDateTime: LocalDateTime, regionCode: String): Optional<WeatherApiLog>

    /**
     * 특정 기간 동안의 WeatherApiLog 목록을 생성 시간(createdAt) 내림차순으로 조회합니다.
     * 관리 목적으로 특정 기간의 로그를 확인할 때 사용할 수 있습니다.
     *
     * @param startDate 시작일시
     * @param endDate 종료일시
     * @return List<WeatherApiLog>
     */
    fun findByCreatedAtBetweenOrderByCreatedAtDesc(startDate: LocalDateTime, endDate: LocalDateTime): List<WeatherApiLog>

    /**
     * 특정 API 호출 상태(apiCallStatus)를 가진 WeatherApiLog 목록을 조회합니다.
     * 오류가 발생한 로그나 특정 상태의 로그를 필터링할 때 사용할 수 있습니다.
     *
     * @param status 조회할 API 호출 상태
     * @return List<WeatherApiLog>
     */
    fun findByApiCallStatus(status: WeatherApiCallStatus): List<WeatherApiLog>

    /**
     * 특정 지역 코드(regionCode)에 대한 모든 WeatherApiLog 항목을 삭제합니다.
     * (주의: 해당 지역에 대한 모든 과거 API 호출 기록이 삭제되므로 신중히 사용해야 합니다.
     * 또한, WeatherApiLog에 cascade 설정이 되어 있으므로 관련된 DailyWeatherForecast 데이터도 함께 삭제됩니다.)
     *
     * @param regionCode 예보 구역 코드
     * @return 삭제된 레코드 수
     */
    @Query("DELETE FROM WeatherApiLog wal WHERE wal.regionCode = :regionCode")
    fun deleteAllByRegionCode(@Param("regionCode") regionCode: String): Int
}