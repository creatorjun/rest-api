package com.company.rest.api.repository

import com.company.rest.api.entity.DailyLuckLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface DailyLuckLogRepository : JpaRepository<DailyLuckLog, String> {

    /**
     * 특정 요청 날짜(requestDate)에 해당하는 DailyHoroscopeLog를 조회합니다.
     * 해당 날짜에 이미 운세 정보가 저장되어 있는지 확인할 때 사용됩니다.
     *
     * @param requestDate 조회할 운세 요청 날짜
     * @return Optional<DailyLuckLog> 해당 날짜의 로그 (존재하지 않으면 Optional.empty())
     */
    fun findByRequestDate(requestDate: LocalDate): Optional<DailyLuckLog>
}