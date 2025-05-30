package com.company.rest.api.config

import com.company.rest.api.security.WebSocketAuthChannelInterceptor // 인터셉터 임포트
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // Spring Security의 WebSocket 설정과 충돌 방지 또는 우선순위 조절
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    @Autowired // JwtTokenProvider를 주입받은 인터셉터를 주입
    private lateinit var authChannelInterceptor: WebSocketAuthChannelInterceptor

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
        // .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.setApplicationDestinationPrefixes("/app")
        registry.enableSimpleBroker("/topic", "/queue")
        // registry.setUserDestinationPrefix("/user") // 기본값 사용 시 생략 가능
    }

    /**
     * 클라이언트 인바운드 채널에 인터셉터를 등록합니다.
     * 이 인터셉터는 STOMP 메시지가 컨트롤러 메소드로 전달되기 전에 JWT 인증을 수행합니다.
     */
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(authChannelInterceptor)
    }
}