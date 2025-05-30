package com.company.rest.api.security

import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

@Component
class WebSocketAuthChannelInterceptor(
    private val jwtTokenProvider: JwtTokenProvider // 기존 JwtTokenProvider 주입
) : ChannelInterceptor {

    private val logger = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor::class.java)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            // STOMP CONNECT 프레임의 헤더에서 'Authorization' (또는 다른 이름의 헤더) 값을 찾습니다.
            // 클라이언트는 STOMP 연결 시 이 헤더에 "Bearer <JWT_TOKEN>" 형태로 토큰을 보내야 합니다.
            val authHeader = accessor.getFirstNativeHeader("Authorization") // 클라이언트에서 보낸 헤더 이름과 일치해야 함
            logger.debug("WebSocket CONNECT attempt with Authorization header: {}", authHeader?.take(15))

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val jwtToken = authHeader.substring(7)
                if (jwtTokenProvider.validateToken(jwtToken)) {
                    val userUid = jwtTokenProvider.getUserUidFromToken(jwtToken)
                    if (userUid != null) {
                        // 인증된 사용자의 Principal을 생성하여 StompHeaderAccessor에 설정
                        val authorities = emptyList<SimpleGrantedAuthority>() // 필요에 따라 권한 설정
                        val authentication = UsernamePasswordAuthenticationToken(userUid, null, authorities)
                        accessor.user = authentication // 이 부분이 중요!
                        logger.info("WebSocket connection authenticated for user UID: {}", userUid)
                    } else {
                        logger.warn("WebSocket CONNECT: User UID could not be extracted from JWT, although token was valid.")
                        // 연결을 거부하거나, 인증되지 않은 사용자로 처리할 수 있습니다.
                        // 여기서 예외를 던지거나 null을 반환하면 연결이 거부될 수 있습니다. (구현에 따라 다름)
                    }
                } else {
                    logger.warn("WebSocket CONNECT: Invalid JWT token received.")
                }
            } else {
                logger.warn("WebSocket CONNECT: Authorization header missing or not Bearer type.")
                // 인증되지 않은 연결 처리 (예: 익명 사용자 허용 또는 연결 거부)
            }
        }
        // 다른 STOMP 명령어(SUBSCRIBE, SEND 등)에 대해서도 필요하다면 여기서 처리 가능
        // 예를 들어, SUBSCRIBE 시 특정 토픽에 대한 권한 검사 등

        return message
    }
}