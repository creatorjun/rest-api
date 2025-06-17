package com.company.rest.api.repository

import com.company.rest.api.entity.Holiday
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface HolidayRepository : JpaRepository<Holiday, String> {

    /**
     * 특정 기간 사이의 모든 공휴일 정보를 날짜순으로 조회합니다.
     * @param startDate 시작일
     * @param endDate 종료일
     * @return List<Holiday>
     */
    fun findByDateBetweenOrderByDateAsc(startDate: LocalDate, endDate: LocalDate): List<Holiday>

    /**
     * 특정 기간 사이의 모든 공휴일 정보를 삭제합니다.
     * 연도별로 데이터를 업데이트하기 전에 기존 데이터를 지우는 용도로 사용됩니다.
     * @param startDate 시작일
     * @param endDate 종료일
     */
    @Modifying
    @Query("DELETE FROM Holiday h WHERE h.date BETWEEN :startDate AND :endDate")
    fun deleteAllByDateBetween(@Param("startDate") startDate: LocalDate, @Param("endDate") endDate: LocalDate)
}