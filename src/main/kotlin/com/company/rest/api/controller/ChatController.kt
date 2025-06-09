package com.company.rest.api.controller

import com.company.rest.api.dto.ChatMessageDto
import com.company.rest.api.dto.MessageReadEventDto
import com.company.rest.api.service.ChatService
import com.company.rest.api.service.WebSocketActivityService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatController(
    private val chatService: ChatService,
    private val webSocketActivityService: WebSocketActivityService // 의존성 주입
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
            if (chatMessageDto.senderUid.isNotBlank()) {
                logger.warn("Controller: sendMessage - Attempting to use senderUid from DTO as fallback: {}", chatMessageDto.senderUid)
            } else {
                logger.error("Controller: sendMessage - Critical: senderActualUid is null AND chatMessageDto.senderUid is blank.")
                return
            }
        }

        logger.info("Controller: Received /chat.sendMessage. Authenticated Principal UID (senderActualUid): {}, DTO Sender UID: {}, Content: '{}', To Receiver UID: {}",
            senderActualUid,
            chatMessageDto.senderUid,
            chatMessageDto.content?.take(20),
            chatMessageDto.receiverUid)

        val finalSenderUidForService = senderActualUid ?: chatMessageDto.senderUid

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
            return
        }
        val sessionAttributes = headerAccessor.sessionAttributes ?: mutableMapOf()

        logger.info("Controller: Received /chat.addUser. Authenticated Principal UID (senderActualUid): {}, DTO Sender Nickname: {}",
            senderActualUid, chatMessageDto.senderNickname)

        chatService.handleUserJoin(chatMessageDto, senderActualUid, sessionAttributes)
    }

    // --- 아래 두 메소드가 새로 추가되었습니다 ---

    /**
     * 클라이언트가 특정 대화창에 진입했음을 서버에 알립니다.
     */
    @MessageMapping("/chat.activity.enter")
    fun handleUserEnterChat(
        @Payload payload: Map<String, String>, // {"partnerUid": "..."}
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        val userUid = headerAccessor.user?.name
        val partnerUid = payload["partnerUid"]

        if (userUid != null && partnerUid != null) {
            webSocketActivityService.userEnteredChat(userUid, partnerUid)
        } else {
            logger.warn("handleUserEnterChat: userUid or partnerUid is null. userUid={}, payload={}", userUid, payload)
        }
    }

    /**
     * 클라이언트가 특정 대화창에서 벗어났음을 서버에 알립니다.
     */
    @MessageMapping("/chat.activity.leave")
    fun handleUserLeaveChat(
        @Payload payload: Map<String, String>, // {"partnerUid": "..."}
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        val userUid = headerAccessor.user?.name
        val partnerUid = payload["partnerUid"]
        if (userUid != null && partnerUid != null) {
            webSocketActivityService.userLeftChat(userUid, partnerUid)
        } else {
            logger.warn("handleUserLeaveChat: userUid or partnerUid is null. userUid={}, payload={}", userUid, payload)
        }
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

        logger.info("Controller: Received /chat.messageRead from User UID (readerActualUid): {}: partnerUid={}",
            readerActualUid, readEventDto.partnerUid)

        chatService.markMessageAsRead(readEventDto, readerActualUid)
    }
}