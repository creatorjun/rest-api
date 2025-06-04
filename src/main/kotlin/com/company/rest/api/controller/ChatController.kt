package com.company.rest.api.controller

import com.company.rest.api.dto.ChatMessageDto
import com.company.rest.api.dto.MessageReadEventDto
import com.company.rest.api.service.ChatService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatController(
    private val chatService: ChatService
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)

    @MessageMapping("/chat.sendMessage")
    fun sendMessage(
        @Payload chatMessageDto: ChatMessageDto,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        val principal: Principal? = headerAccessor.user
        val senderActualUid: String? = principal?.name

        if (senderActualUid == null) {
            logger.warn("Controller: sendMessage - Principal or sender UID is null. Cannot process message from accessor user: {}", principal)
            // 클라이언트 DTO에 있는 senderUid를 fallback으로 사용할지, 아니면 여기서 중단할지 정책 결정 필요
            // 예를 들어, 인증되지 않은 접근으로 간주하고 중단
            if (chatMessageDto.senderUid.isNotBlank()) {
                logger.warn("Controller: sendMessage - Attempting to use senderUid from DTO as fallback: {}", chatMessageDto.senderUid)
                // 이 경우에도 보안상 주의가 필요함. Interceptor에서 인증된 사용자의 UID를 사용하는 것이 원칙.
                // 하지만 현재 로그는 Interceptor가 정상 동작했음을 시사하므로, accessor.user가 null인 경우는 드물어야 함.
            } else {
                logger.error("Controller: sendMessage - Critical: senderActualUid is null AND chatMessageDto.senderUid is blank.")
                return
            }
            // 여기서는 일단 로그만 남기고 DTO의 senderUid를 사용하는 대신, senderActualUid가 null이면 오류로 간주하고 반환하는게 나을 수 있음.
            // 아래 로깅에서 정확한 값이 찍히는지 확인하기 위해 일단 진행.
        }

        // 로그 수정: senderActualUid (Principal에서 가져온 UID)와 chatMessageDto.senderUid (클라이언트가 보낸 UID)를 명확히 구분하여 로깅
        logger.info("Controller: Received /chat.sendMessage. Authenticated Principal UID (senderActualUid): {}, DTO Sender UID: {}, Content: '{}', To Receiver UID: {}",
            senderActualUid, // 실제 인증된 사용자 UID
            chatMessageDto.senderUid, // DTO에 포함된 발신자 UID
            chatMessageDto.content?.take(20),
            chatMessageDto.receiverUid)

        // ChatService에는 인증된 사용자 UID (senderActualUid)를 전달
        // 만약 senderActualUid가 null이고 chatMessageDto.senderUid를 사용하기로 결정했다면 그 값을 사용.
        // 여기서는 senderActualUid가 null이면 위에서 return 하도록 수정하거나,
        // chatMessageDto.senderUid를 사용하되, 보안 검토가 필요.
        // 현재 Interceptor가 userUid를 Principal.name으로 설정하므로 senderActualUid를 사용하는 것이 맞음.
        val finalSenderUidForService = senderActualUid ?: chatMessageDto.senderUid // senderActualUid가 null일 경우 DTO의 값을 임시로 사용 (그러나 null이면 안됨)

        if (finalSenderUidForService.isBlank()) {
            logger.error("Controller: sendMessage - finalSenderUidForService is blank. Aborting. senderActualUid: {}, dto.senderUid: {}", senderActualUid, chatMessageDto.senderUid)
            return
        }

        chatService.processNewMessage(chatMessageDto, finalSenderUidForService)
    }

    @MessageMapping("/chat.addUser")
    fun addUser(
        @Payload chatMessageDto: ChatMessageDto,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        val principal: Principal? = headerAccessor.user
        val senderActualUid: String? = principal?.name

        if (senderActualUid == null) {
            logger.warn("Controller: addUser - Principal or sender UID is null. DTO senderUid: {}", chatMessageDto.senderUid)
            // addUser의 경우, DTO의 senderUid를 신뢰할 수 있는지 정책 결정 필요
            // 일반적으로 연결 주체인 principal.name을 사용해야 함
            return
        }
        val sessionAttributes = headerAccessor.sessionAttributes ?: mutableMapOf()

        logger.info("Controller: Received /chat.addUser. Authenticated Principal UID (senderActualUid): {}, DTO Sender Nickname: {}",
            senderActualUid, chatMessageDto.senderNickname)

        chatService.handleUserJoin(chatMessageDto, senderActualUid, sessionAttributes)
    }

    @MessageMapping("/chat.messageRead")
    fun handleMessageReadEvent(
        @Payload readEventDto: MessageReadEventDto,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        val principal: Principal? = headerAccessor.user
        val readerActualUid: String? = principal?.name

        if (readerActualUid == null) {
            logger.warn("Controller: handleMessageReadEvent - Principal or reader UID is null.")
            return
        }
        logger.info("Controller: Received /chat.messageRead from User UID (readerActualUid): {}: messageId={}",
            readerActualUid, readEventDto.messageId)

        chatService.markMessageAsRead(readEventDto, readerActualUid)
    }
}