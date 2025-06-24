package com.company.rest.api.repository

import com.company.rest.api.entity.Event
import com.company.rest.api.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EventRepository : JpaRepository<Event, String> {

    /**
     * 특정 사용자의 특정 기간 내 모든 이벤트를 날짜순, 그리고 같은 날짜 내에서는 displayOrder 순으로 정렬하여 조회합니다.
     * @param userUid 조회할 사용자의 UID
     * @param startDate 조회 시작일
     * @param endDate 조회 종료일
     * @return List<Event>
     */
    fun findByUserUidAndEventDateBetweenOrderByEventDateAscDisplayOrderAsc(
        userUid: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Event>

    /**
     * 특정 사용자의 모든 이벤트를 삭제하는 메소드.
     * @param user 삭제할 이벤트를 소유한 사용자 엔티티
     * @return 삭제된 행의 수
     */
    @Modifying
    @Query("DELETE FROM Event e WHERE e.user = :user")
    fun deleteAllByUser(@Param("user") user: User): Int
}