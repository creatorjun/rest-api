package com.company.rest.api.service

import com.company.rest.api.dto.PartnerInvitationAcceptRequestDto // DTO 임포트는 컨트롤러에서 사용
import com.company.rest.api.dto.PartnerInvitationResponseDto
import com.company.rest.api.dto.PartnerRelationResponseDto // 추가
import com.company.rest.api.entity.PartnerInvitation
import com.company.rest.api.entity.User // 추가
import com.company.rest.api.repository.PartnerInvitationRepository
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.UUID

@Service
class PartnerInvitationService(
    private val partnerInvitationRepository: PartnerInvitationRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(PartnerInvitationService::class.java)

    private val invitationValidityHours: Long = 24

    @Transactional
    fun createInvitation(issuerUserUid: String): PartnerInvitationResponseDto {
        logger.info("Attempting to create partner invitation for user UID: {}", issuerUserUid)

        val issuerUser = userRepository.findById(issuerUserUid)
            .orElseThrow {
                logger.warn("User not found with UID: {} when trying to create invitation.", issuerUserUid)
                ResponseStatusException(HttpStatus.NOT_FOUND, "초대장을 생성할 사용자를 찾을 수 없습니다.")
            }

        // 1. 사용자가 이미 파트너가 있는지 확인
        if (issuerUser.partnerUserUid != null) {
            logger.info("User UID: {} already has a partner (partner UID: {}). Cannot create new invitation.", issuerUserUid, issuerUser.partnerUserUid)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 파트너가 존재합니다. 새로운 초대장을 생성할 수 없습니다.")
        }

        val now = LocalDateTime.now()
        val existingActiveInvitation = partnerInvitationRepository
            .findByIssuerUserAndIsUsedFalseAndExpiresAtAfter(issuerUser, now)

        if (existingActiveInvitation.isPresent) {
            val activeInvitation = existingActiveInvitation.get()
            logger.info(
                "User UID: {} already has an active invitation code: {}. Returning existing one.",
                issuerUserUid,
                activeInvitation.id
            )
            return PartnerInvitationResponseDto.fromEntity(activeInvitation)
        }

        val newInvitationId = UUID.randomUUID().toString()
        val expiresAt = now.plusHours(invitationValidityHours)

        val newInvitation = PartnerInvitation(
            id = newInvitationId,
            issuerUser = issuerUser,
            expiresAt = expiresAt,
            isUsed = false
        )

        val savedInvitation = partnerInvitationRepository.save(newInvitation)
        logger.info(
            "New partner invitation created with ID: {} for user UID: {}. Expires at: {}",
            savedInvitation.id,
            issuerUserUid,
            expiresAt
        )

        return PartnerInvitationResponseDto.fromEntity(savedInvitation)
    }

    @Transactional
    fun acceptInvitation(accepterUserUid: String, invitationId: String): PartnerRelationResponseDto {
        logger.info("User UID: {} attempting to accept invitation ID: {}", accepterUserUid, invitationId)
        val now = LocalDateTime.now()

        // 1. 초대를 수락하려는 사용자(accepterUser) 조회
        val accepterUser = userRepository.findById(accepterUserUid)
            .orElseThrow {
                logger.warn("Accepter user not found with UID: {} for invitation ID: {}", accepterUserUid, invitationId)
                ResponseStatusException(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다.")
            }

        // 2. 수락하려는 사용자가 이미 파트너가 있는지 확인
        if (accepterUser.partnerUserUid != null) {
            logger.info("User UID: {} already has a partner (partner UID: {}). Cannot accept new invitation.", accepterUserUid, accepterUser.partnerUserUid)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 파트너가 존재합니다. 다른 초대를 수락할 수 없습니다.")
        }

        // 3. 초대 코드 유효성 검사 (ID로 조회, 사용되지 않았고, 만료되지 않았는지)
        val invitation = partnerInvitationRepository.findByIdAndIsUsedFalseAndExpiresAtAfter(invitationId, now)
            .orElseThrow {
                logger.warn("Invalid, used, or expired invitation ID: {} attempted by user UID: {}", invitationId, accepterUserUid)
                ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 초대 코드입니다.")
            }

        // 4. 초대를 생성한 사용자(issuerUser) 정보 가져오기
        val issuerUser = invitation.issuerUser

        // 5. 자기 자신의 초대를 수락하는지 확인
        if (accepterUser.uid == issuerUser.uid) {
            logger.warn("User UID: {} attempted to accept their own invitation ID: {}", accepterUserUid, invitationId)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "자신의 초대를 수락할 수 없습니다.")
        }

        // 6. 초대를 생성한 사용자(issuerUser)가 이미 다른 파트너가 있는지 확인
        //    (초대 생성 후 초대자가 다른 경로로 파트너를 맺었을 경우)
        //    이때는 issuerUser를 DB에서 최신 상태로 다시 한번 조회하는 것이 안전할 수 있음 (동시성 문제 고려)
        val currentIssuerUser = userRepository.findById(issuerUser.uid)
            .orElseThrow {
                // 이론적으로는 발생하기 어려움 (invitation.issuerUser가 존재하므로)
                logger.error("Critical: Issuer user (UID: {}) for invitation ID: {} not found in DB during acceptance by user UID: {}", issuerUser.uid, invitationId, accepterUserUid)
                ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "초대자 정보를 찾는 중 오류가 발생했습니다.")
            }

        if (currentIssuerUser.partnerUserUid != null) {
            logger.warn(
                "Issuer user UID: {} of invitation ID: {} already has a partner (partner UID: {}). Invitation cannot be accepted by user UID: {}.",
                issuerUser.uid,
                invitationId,
                currentIssuerUser.partnerUserUid,
                accepterUserUid
            )
            // 이 초대는 더 이상 유효하지 않으므로 '사용됨' 처리하거나 삭제할 수도 있음 (정책에 따라)
            // invitation.isUsed = true // 예: 재사용 방지
            // partnerInvitationRepository.save(invitation)
            throw ResponseStatusException(HttpStatus.CONFLICT, "초대자가 이미 다른 파트너와 연결되어 있어 초대를 수락할 수 없습니다.")
        }

        // 7. 파트너 관계 설정 (양쪽 모두에게)
        val partnerSinceTime = LocalDateTime.now()

        accepterUser.partnerUserUid = issuerUser.uid
        accepterUser.partnerSince = partnerSinceTime

        currentIssuerUser.partnerUserUid = accepterUser.uid
        currentIssuerUser.partnerSince = partnerSinceTime

        // 8. 초대 코드 사용됨으로 상태 변경 및 수락자 정보 기록
        invitation.isUsed = true
        invitation.acceptedByUserUid = accepterUser.uid
        invitation.expiresAt = now // 이미 사용되었으므로 만료 처리 (선택적)

        // 9. 변경사항 저장
        userRepository.save(accepterUser)
        userRepository.save(currentIssuerUser) // issuerUser의 파트너 정보도 업데이트
        partnerInvitationRepository.save(invitation)

        logger.info(
            "Partner relation established between user UID: {} and user UID: {} via invitation ID: {}",
            accepterUserUid,
            issuerUser.uid,
            invitationId
        )

        return PartnerRelationResponseDto.fromUsers(accepterUser, currentIssuerUser)
    }
}