package com.company.rest.api.repository

import com.company.rest.api.entity.PartnerInvitation
import com.company.rest.api.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface PartnerInvitationRepository : JpaRepository<PartnerInvitation, String> {

    /**
     * 특정 사용자가 생성한, 아직 사용되지 않았고 만료되지 않은 초대장을 찾습니다.
     * @param issuerUser 초대를 생성한 사용자
     * @param now 현재 시간 (만료 여부 비교용)
     * @return Optional<PartnerInvitation>
     */
    fun findByIssuerUserAndIsUsedFalseAndExpiresAtAfter(issuerUser: User, now: LocalDateTime): Optional<PartnerInvitation>

    /**
     * 초대 ID로 아직 사용되지 않았고 만료되지 않은 초대장을 찾습니다.
     * @param invitationId 초대 ID
     * @param now 현재 시간 (만료 여부 비교용)
     * @return Optional<PartnerInvitation>
     */
    fun findByIdAndIsUsedFalseAndExpiresAtAfter(invitationId: String, now: LocalDateTime): Optional<PartnerInvitation>

    /**
     * 특정 사용자가 생성한 모든 초대장을 찾습니다. (관리용 또는 히스토리 조회용으로 필요할 수 있음)
     * @param issuerUser 초대를 생성한 사용자
     * @return List<PartnerInvitation>
     */
    fun findByIssuerUser(issuerUser: User): List<PartnerInvitation>

    // 특정 사용자가 발행한 모든 파트너 초대를 삭제하는 메소드 추가
    @Modifying
    @Query("DELETE FROM PartnerInvitation pi WHERE pi.issuerUser = :issuerUser")
    fun deleteAllByIssuerUser(@Param("issuerUser") issuerUser: User): Int
}