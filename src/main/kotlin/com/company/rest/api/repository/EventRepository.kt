package com.company.rest.api.repository

import com.company.rest.api.entity.Event
import com.company.rest.api.entity.User // User 엔티티 임포트 추가
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EventRepository : JpaRepository<Event, String> {
    // 특정 사용자의 모든 이벤트를 생성 시간(createdAt) 기준으로 내림차순 정렬하여 조회
    fun findByUserUidOrderByCreatedAtDesc(userUid: String): List<Event>

    // 특정 사용자의 모든 이벤트를 삭제하는 메소드 추가
    @Modifying // 데이터 변경을 위한 어노테이션
    @Query("DELETE FROM Event e WHERE e.user = :user") // User 엔티티를 직접 비교
    fun deleteAllByUser(@Param("user") user: User): Int // 삭제된 행의 수를 반환할 수 있음

    // 또는 User의 UID를 사용하여 삭제 (위의 deleteAllByUser와 기능적으로 유사하나, 파라미터 타입만 다름)
    // @Modifying
    // @Query("DELETE FROM Event e WHERE e.user.uid = :userUid")
    // fun deleteAllByUserUid(@Param("userUid") userUid: String): Int
}