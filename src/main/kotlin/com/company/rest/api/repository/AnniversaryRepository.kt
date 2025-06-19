package com.company.rest.api.repository

import com.company.rest.api.entity.Anniversary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AnniversaryRepository : JpaRepository<Anniversary, String> {

    /**
     * 주어진 사용자 UID 목록에 해당하는 사용자가 생성한 모든 기념일을 조회합니다.
     * 기념일 날짜(date) 오름차순으로 정렬하여 반환합니다.
     *
     * @param userUids 조회할 사용자들의 UID 리스트
     * @return List<Anniversary>
     */
    fun findByUserUidInOrderByDateAsc(userUids: List<String>): List<Anniversary>
}