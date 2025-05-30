package com.company.rest.api.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.MessagingErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class FcmService(
    // UserService를 주입받아 유효하지 않은 토큰을 제거하는 메소드를 호출합니다.
    private val userService: UserService // UserService 주입
) {

    private val logger = LoggerFactory.getLogger(FcmService::class.java)

    /**
     * 특정 사용자에게 FCM 푸시 알림을 전송합니다.
     * 토큰이 유효하지 않은 경우, UserService를 통해 DB에서 해당 토큰을 제거하려고 시도합니다.
     *
     * @param userFcmToken 대상 사용자의 FCM 등록 토큰.
     * @param title 알림의 제목입니다.
     * @param body 알림의 본문 내용입니다.
     * @param data 알림과 함께 전송할 추가적인 데이터 맵입니다. (선택 사항)
     */
    // 이 메소드 자체는 외부 트랜잭션과 분리되거나, 읽기 전용 트랜잭션 내에서 호출될 수 있으므로
    // Propagation.REQUIRES_NEW 등을 고려할 수 있으나, 현재는 UserService의 removeFcmToken에서
    // 자체 트랜잭션을 관리하므로 특별한 전파 속성은 명시하지 않아도 됩니다.
    // 다만, 이 메소드가 매우 긴 트랜잭션의 일부로 호출되는 것을 피하는 것이 좋습니다.
    fun sendNotification(userFcmToken: String, title: String, body: String, data: Map<String, String>?) {
        if (userFcmToken.isBlank()) {
            logger.warn("FCM token is blank. Cannot send notification. Title: '{}', Body: '{}'", title, body)
            return
        }

        val notificationPayload = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build()

        val messageBuilder = Message.builder()
            .setNotification(notificationPayload)
            .setToken(userFcmToken)

        if (data != null && data.isNotEmpty()) {
            messageBuilder.putAllData(data)
            logger.debug("Sending FCM with data: {}", data)
        }

        val message = messageBuilder.build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("Successfully sent FCM message: {}. Target token (first 20 chars): {}...", response, userFcmToken.take(20))
        } catch (e: FirebaseMessagingException) {
            logger.error(
                "Failed to send FCM message to token (first 20 chars): {}... - ErrorCode: {}, Message: {}",
                userFcmToken.take(20),
                e.messagingErrorCode,
                e.message
            )

            // 등록되지 않았거나(UNREGISTERED) 유효하지 않은 인자(INVALID_ARGUMENT) 오류 코드인 경우
            // 해당 FCM 토큰을 DB에서 제거하도록 UserService에 요청합니다.
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
                e.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT) { // INVALID_ARGUMENT는 때로 유효하지 않은 토큰을 의미할 수 있음
                logger.warn(
                    "FCM token '{}' (first 20 chars) seems to be unregistered or invalid. " +
                            "Attempting to remove it from the database.",
                    userFcmToken.take(20)
                )
                try {
                    // UserService의 removeFcmToken 메소드 호출
                    userService.removeFcmToken(userFcmToken) //
                } catch (userServiceException: Exception) {
                    // UserService에서 토큰 제거 중 발생할 수 있는 예외 처리
                    logger.error(
                        "Error while trying to remove invalid FCM token '{}' (first 20 chars) via UserService: {}",
                        userFcmToken.take(20),
                        userServiceException.message,
                        userServiceException
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(
                "An unexpected error occurred while sending FCM message to token (first 20 chars): {}...",
                userFcmToken.take(20),
                e
            )
        }
    }
}