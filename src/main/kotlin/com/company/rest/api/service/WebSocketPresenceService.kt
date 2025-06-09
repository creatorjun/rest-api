package com.company.rest.api.service

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Service
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Service
class WebSocketPresenceService(
    private val webSocketActivityService: WebSocketActivityService
) {
    private val logger = LoggerFactory.getLogger(WebSocketPresenceService::class.java)

    private val connectedUserUids: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val userUidSessionAttributeKey = "userUid"

    @EventListener
    fun handleSessionConnected(event: SessionConnectedEvent) {
        val userPrincipal = event.user
        if (userPrincipal?.name != null) {
            val userUid = userPrincipal.name
            connectedUserUids.add(userUid)
            logger.info("WebSocket User Connected (via Principal): {}", userUid)
        } else {
            val accessor = StompHeaderAccessor.wrap(event.message)
            val userUidFromAttribute = accessor.sessionAttributes?.get(userUidSessionAttributeKey) as? String
            if (userUidFromAttribute != null) {
                connectedUserUids.add(userUidFromAttribute)
                logger.info("WebSocket User Connected (via session attribute as fallback): {}. SessionId: {}", userUidFromAttribute, accessor.sessionId)
            } else {
                logger.warn("WebSocket session connected, but user principal/UID is null and also not found in session attributes. SessionId: {}. Message: {}", accessor.sessionId, event.message)
            }
        }
    }

    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        var userUidToRemove: String? = null
        val sessionId = event.sessionId

        event.user?.name?.let {
            userUidToRemove = it
            logger.info("WebSocket User Disconnected (identified by Principal): {}. SessionId: {}", userUidToRemove, sessionId)
        }

        if (userUidToRemove == null) {
            try {
                val accessor = StompHeaderAccessor.wrap(event.message)
                val attributes = accessor.sessionAttributes
                if (attributes != null) {
                    (attributes[userUidSessionAttributeKey] as? String)?.let {
                        userUidToRemove = it
                        logger.info("WebSocket User Disconnected (identified by session attribute): {}. SessionId: {}", userUidToRemove, sessionId)
                    }
                }
            } catch (e: Exception) {
                logger.warn("Error accessing session attributes on disconnect for SessionId: {}. Might be a non-STOMP disconnect or very early disconnect. Error: {}", sessionId, e.message)
            }
        }

        // --- 바로 이 부분입니다! ---
        // if (userUidToRemove != null) { ... } 블록을
        // userUidToRemove?.let { ... } 으로 변경하여 Smart Cast 문제를 해결합니다.
        userUidToRemove?.let { uid ->
            if (connectedUserUids.remove(uid)) {
                logger.info("User {} removed from connectedUserUids set. SessionId: {}", uid, event.sessionId)

                // 연결이 끊긴 사용자를 활동 추적에서도 제거합니다.
                webSocketActivityService.userDisconnected(uid)

            } else {
                logger.warn("Attempted to remove user {} from connectedUserUids set, but was not present. SessionId: {}", uid, event.sessionId)
            }
        } ?: logger.warn("WebSocket session disconnected (SessionId: {}), but could not identify userUid to remove from presence set. User might not have fully authenticated via STOMP.", sessionId)
    }

    fun isUserOnline(userUid: String): Boolean {
        return connectedUserUids.contains(userUid)
    }

    fun getOnlineUserUids(): Set<String> {
        return connectedUserUids.toSet()
    }
}