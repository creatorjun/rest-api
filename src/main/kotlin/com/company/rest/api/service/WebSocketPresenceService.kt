package com.company.rest.api.service

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor // StompHeaderAccessor 임포트 추가
import org.springframework.stereotype.Service
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Service
class WebSocketPresenceService { //
    private val logger = LoggerFactory.getLogger(WebSocketPresenceService::class.java)

    // 현재 접속 중인 사용자들의 UID를 저장하는 Set
    private val connectedUserUids: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    // WebSocketAuthChannelInterceptor와 동일한 키를 사용해야 함
    private val userUidSessionAttributeKey = "userUid"

    @EventListener
    fun handleSessionConnected(event: SessionConnectedEvent) { //
        val userPrincipal = event.user
        if (userPrincipal?.name != null) {
            val userUid = userPrincipal.name
            connectedUserUids.add(userUid)
            logger.info("WebSocket User Connected (via Principal): {}", userUid)

            // WebSocketAuthChannelInterceptor에서 이미 세션 속성에 userUid를 저장했지만,
            // 여기서도 StompHeaderAccessor를 통해 접근하여 확인할 수 있습니다. (주로 디버깅 또는 추가 로직용)
            // val accessor = StompHeaderAccessor.wrap(event.message)
            // val userUidFromAttribute = accessor.sessionAttributes?.get(userUidSessionAttributeKey) as? String
            // logger.debug("Session attribute userUid on connect: {}", userUidFromAttribute)
        } else {
            // 이 경우는 WebSocketAuthChannelInterceptor에서 인증 실패 시 예외를 던지도록 변경했으므로,
            // 정상적인 흐름에서는 발생 빈도가 줄어들 것입니다.
            // 하지만 여전히 발생 가능하다면, 여기서도 세션 속성을 확인해볼 수 있습니다.
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
    fun handleSessionDisconnect(event: SessionDisconnectEvent) { //
        var userUidToRemove: String? = null
        val sessionId = event.sessionId // 모든 disconnect 이벤트에는 sessionId가 존재

        // 1. Principal에서 userUid 가져오기 시도
        event.user?.name?.let {
            userUidToRemove = it
            logger.info("WebSocket User Disconnected (identified by Principal): {}. SessionId: {}", userUidToRemove, sessionId)
        }

        // 2. Principal이 없거나 userUid를 못가져왔다면, STOMP 세션 속성에서 userUid 가져오기 시도
        if (userUidToRemove == null) {
            try {
                // event.message의 헤더에서 StompHeaderAccessor를 가져와 세션 속성에 접근
                // SessionDisconnectEvent의 message 페이로드는 일반적으로 비어있거나 null일 수 있으므로,
                // 헤더 자체에 접근해야 합니다. StompHeaderAccessor.wrap(event.message) 사용.
                val accessor = StompHeaderAccessor.wrap(event.message)
                // STOMP 연결이 완전히 수립되기 전에 끊어진 경우 sessionAttributes가 null일 수 있습니다.
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

        // 3. userUid를 찾았다면 접속 목록에서 제거
        if (userUidToRemove != null) {
            if (connectedUserUids.remove(userUidToRemove)) {
                logger.info("User {} removed from connectedUserUids set. SessionId: {}", userUidToRemove, sessionId)
            } else {
                // 이 경우는 거의 없어야 하지만, 로깅은 해둡니다.
                logger.warn("Attempted to remove user {} from connectedUserUids set, but was not present. SessionId: {}", userUidToRemove, sessionId)
            }
        } else {
            logger.warn("WebSocket session disconnected (SessionId: {}), but could not identify userUid to remove from presence set. User might not have fully authenticated via STOMP.", sessionId)
        }

        // TODO: 필요한 경우, 다른 사용자들에게 접속 해제 상태 변경 알림
    }

    fun isUserOnline(userUid: String): Boolean { //
        return connectedUserUids.contains(userUid)
    }

    fun getOnlineUserUids(): Set<String> { //
        return connectedUserUids.toSet()
    }
}