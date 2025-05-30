package com.company.rest.api.controller

import com.company.rest.api.dto.ChatMessageDto
import com.company.rest.api.dto.MessageReadEventDto
// MessageReadConfirmationDto는 ChatService에서 사용되므로 컨트롤러에는 직접 필요 없을 수 있습니다.
// import com.company.rest.api.service.WebSocketPresenceService // ChatService로 이동
// import com.company.rest.api.repository.UserRepository // ChatService로 이동
// import com.company.rest.api.repository.ChatMessageRepository // ChatService로 이동
// import org.springframework.messaging.simp.SimpMessagingTemplate // ChatService로 이동 (만약 모든 메시지 발송을 서비스에 위임했다면)
import com.company.rest.api.service.ChatService // 새로 만든 ChatService 임포트
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
// @Transactional 어노테이션은 서비스 계층으로 이동했으므로 여기서 제거
import java.security.Principal

@Controller
class ChatController(
    // 이제 ChatService가 SimpMessagingTemplate 등을 포함하여 대부분의 로직을 처리합니다.
    private val chatService: ChatService // ChatService 주입
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)

    @MessageMapping("/chat.sendMessage")
    // @Transactional // 서비스 계층으로 이동
    fun sendMessage(@Payload chatMessageDto: ChatMessageDto, principal: Principal?) {
        val senderPrincipalName = principal?.name
        logger.info("Controller: Received /chat.sendMessage from Principal UID {}: content='{}', to UID={}",
            senderPrincipalName, chatMessageDto.content?.take(20), chatMessageDto.receiverUid)

        chatService.processNewMessage(chatMessageDto, senderPrincipalName)
    }

    @MessageMapping("/chat.addUser")
    // @Transactional(readOnly = true) // 서비스 계층으로 이동
    fun addUser(@Payload chatMessageDto: ChatMessageDto, headerAccessor: SimpMessageHeaderAccessor, principal: Principal?) {
        val senderPrincipalName = principal?.name
        val sessionAttributes = headerAccessor.sessionAttributes ?: mutableMapOf() // null일 경우 빈 맵 전달

        logger.info("Controller: Received /chat.addUser from Principal UID {}. Nickname: {}",
            senderPrincipalName, chatMessageDto.senderNickname)

        chatService.handleUserJoin(chatMessageDto, senderPrincipalName, sessionAttributes)

        // ChatService의 handleUserJoin이 sessionAttributes를 직접 수정하므로,
        // 컨트롤러에서 headerAccessor.sessionAttributes를 다시 설정할 필요는 없습니다.
        // (만약 ChatService가 수정된 map을 반환한다면 여기서 설정할 수 있지만, 현재는 void)
    }

    @MessageMapping("/chat.messageRead")
    // @Transactional // 서비스 계층으로 이동
    fun handleMessageReadEvent(@Payload readEventDto: MessageReadEventDto, principal: Principal?) {
        val readerPrincipalName = principal?.name
        logger.info("Controller: Received /chat.messageRead from Principal UID {}: messageId={}",
            readerPrincipalName, readEventDto.messageId)

        chatService.markMessageAsRead(readEventDto, readerPrincipalName)
    }
}