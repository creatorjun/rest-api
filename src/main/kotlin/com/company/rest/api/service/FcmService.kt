package com.company.rest.api.service

import com.company.rest.api.event.FcmTokenInvalidatedEvent
import com.google.firebase.messaging.*
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class FcmService(
    private val eventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(FcmService::class.java)

    fun sendNotification(userFcmToken: String, title: String?, body: String?, data: Map<String, String>?) {
        if (userFcmToken.isBlank()) {
            logger.warn("FCM token is blank. Cannot send notification. Title: '{}', Body: '{}'", title, body)
            return
        }

        val messageBuilder = Message.builder()
            .setToken(userFcmToken)

        if (!title.isNullOrBlank() && !body.isNullOrBlank()) {
            val notificationPayload = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build()
            messageBuilder.setNotification(notificationPayload)
        }

        if (data != null && data.isNotEmpty()) {
            messageBuilder.putAllData(data)
            logger.debug("Sending FCM with data: {}", data)
        }

        val message = messageBuilder.build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            logger.info(
                "Successfully sent FCM message: {}. Target token (first 20 chars): {}...",
                response,
                userFcmToken.take(20)
            )
        } catch (e: FirebaseMessagingException) {
            logger.error(
                "Failed to send FCM message to token (first 20 chars): {}... - ErrorCode: {}, Message: {}",
                userFcmToken.take(20),
                e.messagingErrorCode,
                e.message
            )

            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
                e.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT
            ) {
                logger.warn(
                    "FCM token '{}' (first 20 chars) seems to be unregistered or invalid. " +
                            "Publishing FcmTokenInvalidatedEvent to handle cleanup.",
                    userFcmToken.take(20)
                )
                eventPublisher.publishEvent(FcmTokenInvalidatedEvent(userFcmToken))
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