package com.company.rest.api.service

import com.company.rest.api.dto.PartnerInvitationResponseDto
import com.company.rest.api.dto.PartnerRelationResponseDto
import com.company.rest.api.entity.PartnerInvitation
import com.company.rest.api.event.PartnerRelationEstablishedEvent
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.repository.PartnerInvitationRepository
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class PartnerInvitationService(
    private val partnerInvitationRepository: PartnerInvitationRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher
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

        val accepterUser = userRepository.findById(accepterUserUid)
            .orElseThrow {
                logger.warn("Accepter user not found with UID: {} for invitation ID: {}", accepterUserUid, invitationId)
                throw CustomException(ErrorCode.USER_NOT_FOUND)
            }

        if (accepterUser.partnerUserUid != null) {
            logger.info(
                "User UID: {} already has a partner (partner UID: {}). Cannot accept new invitation.",
                accepterUserUid,
                accepterUser.partnerUserUid
            )
            throw CustomException(ErrorCode.PARTNER_ALREADY_EXISTS)
        }

        val invitation = partnerInvitationRepository.findByIdAndIsUsedFalseAndExpiresAtAfter(invitationId, now)
            .orElseThrow {
                logger.warn(
                    "Invalid, used, or expired invitation ID: {} attempted by user UID: {}",
                    invitationId,
                    accepterUserUid
                )
                throw CustomException(ErrorCode.INVITATION_NOT_FOUND)
            }

        val issuerUser = invitation.issuerUser

        if (accepterUser.uid == issuerUser.uid) {
            logger.warn("User UID: {} attempted to accept their own invitation ID: {}", accepterUserUid, invitationId)
            throw CustomException(ErrorCode.CANNOT_INVITE_SELF)
        }

        val currentIssuerUser = userRepository.findById(issuerUser.uid)
            .orElseThrow {
                logger.error(
                    "Critical: Issuer user (UID: {}) for invitation ID: {} not found in DB during acceptance by user UID: {}",
                    issuerUser.uid,
                    invitationId,
                    accepterUserUid
                )
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

        val partnerSinceTime = LocalDateTime.now()

        accepterUser.partnerUserUid = issuerUser.uid
        accepterUser.partnerSince = partnerSinceTime

        currentIssuerUser.partnerUserUid = accepterUser.uid
        currentIssuerUser.partnerSince = partnerSinceTime

        invitation.isUsed = true
        invitation.acceptedByUserUid = accepterUser.uid
        invitation.expiresAt = now

        userRepository.save(accepterUser)
        userRepository.save(currentIssuerUser)
        partnerInvitationRepository.save(invitation)

        eventPublisher.publishEvent(
            PartnerRelationEstablishedEvent(
                issuerUserId = currentIssuerUser.uid,
                accepterUserId = accepterUser.uid,
                accepterNickname = accepterUser.nickname,
                partnerSince = partnerSinceTime
            )
        )

        logger.info(
            "Partner relation established and event published for issuer {}. Accepter: {}, Invitation ID: {}",
            currentIssuerUser.uid,
            accepterUserUid,
            invitationId
        )

        return PartnerRelationResponseDto.fromUsers(accepterUser, currentIssuerUser)
    }

    @Transactional
    fun deleteInvitation(issuerUserUid: String, invitationId: String) {
        logger.info("User UID: {} attempting to delete invitation ID: {}", issuerUserUid, invitationId)

        val invitation = partnerInvitationRepository.findById(invitationId)
            .orElseThrow {
                logger.warn(
                    "Attempted to delete non-existent invitation ID: {} by user UID: {}",
                    invitationId,
                    issuerUserUid
                )
                throw CustomException(ErrorCode.INVITATION_NOT_FOUND)
            }

        if (invitation.issuerUser.uid != issuerUserUid) {
            logger.warn(
                "User UID: {} attempted to delete invitation ID: {} owned by another user UID: {}",
                issuerUserUid,
                invitationId,
                invitation.issuerUser.uid
            )
            throw CustomException(ErrorCode.FORBIDDEN_INVITATION_ACCESS)
        }

        if (invitation.isUsed) {
            logger.warn(
                "User UID: {} attempted to delete an already used invitation ID: {}",
                issuerUserUid,
                invitationId
            )
            throw CustomException(ErrorCode.INVITATION_ALREADY_USED)
        }

        partnerInvitationRepository.delete(invitation)
        logger.info(
            "Invitation ID: {} deleted successfully by issuer user UID: {}",
            invitationId,
            issuerUserUid
        )
    }
}