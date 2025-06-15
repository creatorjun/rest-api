package com.company.rest.api.service

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Service
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.*
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
        val accessor = StompHeaderAccessor.wrap(event.message)
        val userPrincipal = event.user

        val userUid = userPrincipal?.name ?: accessor.sessionAttributes?.get(userUidSessionAttributeKey) as? String

        if (userUid != null) {
            connectedUserUids.add(userUid)
            val logSource = if (userPrincipal?.name != null) "Principal" else "session attribute"
            logger.info(
                "WebSocket User Connected (via {}): {}. SessionId: {}",
                logSource,
                userUid,
                accessor.sessionId
            )
        } else {
            logger.warn(
                "WebSocket session connected, but could not identify user. SessionId: {}. Message: {}",
                accessor.sessionId,
                event.message
            )
        }
    }

    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        val sessionId = event.sessionId

        val userUidToRemove: String? = event.user?.name?.also { uid ->
            logger.info("WebSocket User Disconnected (identified by Principal): {}. SessionId: {}", uid, sessionId)
        } ?: try {
            val accessor = StompHeaderAccessor.wrap(event.message)
            (accessor.sessionAttributes?.get(userUidSessionAttributeKey) as? String)?.also { uid ->
                logger.info(
                    "WebSocket User Disconnected (identified by session attribute): {}. SessionId: {}",
                    uid,
                    sessionId
                )
            }
        } catch (e: Exception) {
            logger.warn(
                "Error accessing session attributes on disconnect for SessionId: {}. Might be a non-STOMP disconnect or early abort. Error: {}",
                sessionId,
                e.message
            )
            null
        }

        userUidToRemove?.let { uid ->
            if (connectedUserUids.remove(uid)) {
                logger.info("User {} removed from connectedUserUids set. SessionId: {}", uid, event.sessionId)
                webSocketActivityService.userDisconnected(uid)
            } else {
                logger.warn(
                    "Attempted to remove user {} from connectedUserUids set, but was not present. SessionId: {}",
                    uid,
                    event.sessionId
                )
            }
        } ?: logger.warn(
            "WebSocket session disconnected (SessionId: {}), but could not identify userUid to remove from presence set.",
            sessionId
        )
    }

    fun isUserOnline(userUid: String): Boolean {
        return connectedUserUids.contains(userUid)
    }

    fun getOnlineUserUids(): Set<String> {
        return connectedUserUids.toSet()
    }
}