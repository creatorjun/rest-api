package com.company.rest.api.event

import com.company.rest.api.dto.SystemNotificationDto
import com.company.rest.api.dto.SystemNotificationType
import com.company.rest.api.repository.UserRepository
import com.company.rest.api.service.FcmService
import com.company.rest.api.service.UserService
import com.company.rest.api.service.WebSocketPresenceService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class NotificationEventListener(
    private val userService: UserService,
    private val fcmService: FcmService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val webSocketPresenceService: WebSocketPresenceService,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationEventListener::class.java)

    @EventListener
    fun handleFcmTokenInvalidatedEvent(event: FcmTokenInvalidatedEvent) {
        logger.info("Handling FcmTokenInvalidatedEvent for token: {}...", event.fcmToken.take(10))
        try {
            userService.removeFcmToken(event.fcmToken)
        } catch (e: Exception) {
            logger.error(
                "Error while handling FcmTokenInvalidatedEvent for token: {}",
                event.fcmToken.take(10),
                e
            )
        }
    }

    @EventListener
    fun handleUserAccountDeletedEvent(event: UserAccountDeletedEvent) {
        val message = "회원 탈퇴가 정상적으로 처리되었습니다. 이용해주셔서 감사합니다."
        if (event.isOnline) {
            val notificationDto = SystemNotificationDto(
                type = SystemNotificationType.ACCOUNT_DELETED,
                message = message
            )
            messagingTemplate.convertAndSendToUser(event.userId, "/queue/private", notificationDto)
            logger.info("Sent ACCOUNT_DELETED notification to user {} via WebSocket.", event.userId)
        } else {
            event.fcmToken?.let { token ->
                if (token.isNotBlank()) {
                    val data = mapOf(
                        "type" to SystemNotificationType.ACCOUNT_DELETED.name,
                        "message" to message
                    )
                    fcmService.sendNotification(token, "계정 삭제 완료", message, data)
                    logger.info("Sent ACCOUNT_DELETED notification to user {} via FCM.", event.userId)
                }
            }
        }
    }

    @EventListener
    fun handlePartnerRelationTerminatedEvent(event: PartnerRelationTerminatedEvent) {
        val notificationDto = SystemNotificationDto(
            type = SystemNotificationType.PARTNER_RELATION_TERMINATED,
            message = "상대방이 파트너 관계를 해제했습니다."
        )
        messagingTemplate.convertAndSendToUser(event.notifiedPartnerId, "/queue/private", notificationDto)
        logger.info("Sent PARTNER_RELATION_TERMINATED notification to former partner UID: {}", event.notifiedPartnerId)
    }

    @EventListener
    fun handlePartnerRelationEstablishedEvent(event: PartnerRelationEstablishedEvent) {
        val message = "${event.accepterNickname ?: "상대방"}님이 파트너 초대를 수락했습니다!"
        val dataPayload = mapOf(
            "type" to SystemNotificationType.PARTNER_RELATION_ESTABLISHED.name,
            "partnerUid" to event.accepterUserId,
            "partnerNickname" to (event.accepterNickname ?: ""),
            "partnerSince" to event.partnerSince.toString()
        )

        if (webSocketPresenceService.isUserOnline(event.issuerUserId)) {
            val notificationDto = SystemNotificationDto(
                type = SystemNotificationType.PARTNER_RELATION_ESTABLISHED,
                message = message,
                data = dataPayload
            )
            messagingTemplate.convertAndSendToUser(event.issuerUserId, "/queue/private", notificationDto)
            logger.info("Sent PARTNER_RELATION_ESTABLISHED notification to online user {} via WebSocket.", event.issuerUserId)
        } else {
            userRepository.findById(event.issuerUserId).ifPresent { issuerUser ->
                issuerUser.fcmToken?.let { token ->
                    if (token.isNotBlank()) {
                        fcmService.sendNotification(token, "파트너 연결!", message, dataPayload)
                        logger.info("Sent PARTNER_RELATION_ESTABLISHED notification to offline user {} via FCM.", event.issuerUserId)
                    }
                }
            }
        }
    }
}