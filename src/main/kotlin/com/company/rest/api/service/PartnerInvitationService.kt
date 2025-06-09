package com.company.rest.api.service

import com.company.rest.api.dto.PartnerInvitationResponseDto
import com.company.rest.api.dto.PartnerRelationResponseDto
import com.company.rest.api.entity.PartnerInvitation
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.repository.PartnerInvitationRepository
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

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
                throw CustomException(ErrorCode.USER_NOT_FOUND)
            }

        // 1. 사용자가 이미 파트너가 있는지 확인
        if (issuerUser.partnerUserUid != null) {
            logger.info(
                "User UID: {} already has a partner (partner UID: {}). Cannot create new invitation.",
                issuerUserUid,
                issuerUser.partnerUserUid
            )
            throw CustomException(ErrorCode.PARTNER_ALREADY_EXISTS)
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
                throw CustomException(ErrorCode.USER_NOT_FOUND)
            }

        // 2. 수락하려는 사용자가 이미 파트너가 있는지 확인
        if (accepterUser.partnerUserUid != null) {
            logger.info(
                "User UID: {} already has a partner (partner UID: {}). Cannot accept new invitation.",
                accepterUserUid,
                accepterUser.partnerUserUid
            )
            throw CustomException(ErrorCode.PARTNER_ALREADY_EXISTS)
        }

        // 3. 초대 코드 유효성 검사
        val invitation = partnerInvitationRepository.findByIdAndIsUsedFalseAndExpiresAtAfter(invitationId, now)
            .orElseThrow {
                logger.warn(
                    "Invalid, used, or expired invitation ID: {} attempted by user UID: {}",
                    invitationId,
                    accepterUserUid
                )
                throw CustomException(ErrorCode.INVITATION_NOT_FOUND)
            }

        // 4. 초대를 생성한 사용자(issuerUser) 정보 가져오기
        val issuerUser = invitation.issuerUser

        // 5. 자기 자신의 초대를 수락하는지 확인
        if (accepterUser.uid == issuerUser.uid) {
            logger.warn("User UID: {} attempted to accept their own invitation ID: {}", accepterUserUid, invitationId)
            throw CustomException(ErrorCode.CANNOT_INVITE_SELF)
        }

        // 6. 초대를 생성한 사용자(issuerUser)가 이미 다른 파트너가 있는지 확인 (동시성 문제 고려)
        val currentIssuerUser = userRepository.findById(issuerUser.uid)
            .orElseThrow {
                logger.error(
                    "Critical: Issuer user (UID: {}) for invitation ID: {} not found in DB during acceptance by user UID: {}",
                    issuerUser.uid,
                    invitationId,
                    accepterUserUid
                )
                // 초대장을 만든 유저가 사라진 매우 드문 경우
                throw CustomException(ErrorCode.USER_NOT_FOUND)
            }

        if (currentIssuerUser.partnerUserUid != null) {
            logger.warn(
                "Issuer user UID: {} of invitation ID: {} already has a partner (partner UID: {}). Invitation cannot be accepted by user UID: {}.",
                issuerUser.uid,
                invitationId,
                currentIssuerUser.partnerUserUid,
                accepterUserUid
            )
            throw CustomException(ErrorCode.INVITATION_ISSUER_HAS_PARTNER)
        }

        // 7. 파트너 관계 설정
        val partnerSinceTime = LocalDateTime.now()

        accepterUser.partnerUserUid = issuerUser.uid
        accepterUser.partnerSince = partnerSinceTime

        currentIssuerUser.partnerUserUid = accepterUser.uid
        currentIssuerUser.partnerSince = partnerSinceTime

        // 8. 초대 코드 사용됨으로 상태 변경
        invitation.isUsed = true
        invitation.acceptedByUserUid = accepterUser.uid
        invitation.expiresAt = now

        // 9. 변경사항 저장
        userRepository.save(accepterUser)
        userRepository.save(currentIssuerUser)
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