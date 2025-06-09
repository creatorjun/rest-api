package com.company.rest.api.service

import com.company.rest.api.dto.*
import com.company.rest.api.entity.ChatMessage
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.repository.ChatMessageRepository
import com.company.rest.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Service
class ChatService(
    private val userRepository: UserRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val webSocketPresenceService: WebSocketPresenceService,
    private val fcmService: FcmService,
    private val webSocketActivityService: WebSocketActivityService // 의존성 주입
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    private val fcmSentForOfflineConversation: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    private fun createCanonicalConversationId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "$uid1:$uid2" else "$uid2:$uid1"
    }

    @Transactional(readOnly = true)
    fun getPaginatedMessages(
        currentUserUid: String,
        otherUserUid: String,
        beforeTimestamp: Long?,
        size: Int
    ): PaginatedChatMessagesResponseDto {
        logger.info(
            "Fetching paginated messages for conversation between {} and {}. Before timestamp: {}, Size: {}",
            currentUserUid, otherUserUid, beforeTimestamp, size
        )

        val currentUser = userRepository.findById(currentUserUid)
            .orElseThrow { throw CustomException(ErrorCode.USER_NOT_FOUND) }
        val otherUser = userRepository.findById(otherUserUid)
            .orElseThrow { throw CustomException(ErrorCode.USER_NOT_FOUND) }

        val pageRequest = PageRequest.of(0, size)
        val messagesSlice: Slice<ChatMessage>

        if (beforeTimestamp != null) {
            val cursorLocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(beforeTimestamp), ZoneId.systemDefault())
            logger.debug("Fetching messages before: {}", cursorLocalDateTime)
            messagesSlice = chatMessageRepository.findMessagesBetweenUsersBefore(
                currentUser,
                otherUser,
                cursorLocalDateTime,
                pageRequest
            )
        } else {
            logger.debug("Fetching latest messages.")
            messagesSlice = chatMessageRepository.findLatestMessagesBetweenUsers(
                currentUser,
                otherUser,
                pageRequest
            )
        }

        logger.info("Found {} messages. Has next page: {}", messagesSlice.numberOfElements, messagesSlice.hasNext())
        return PaginatedChatMessagesResponseDto.fromSlice(messagesSlice)
    }

    @Transactional(readOnly = true)
    fun searchMessages(
        currentUserUid: String,
        otherUserUid: String,
        keyword: String,
        pageable: Pageable
    ): PaginatedChatMessagesResponseDto {
        logger.info(
            "Searching messages between {} and {} with keyword '{}'. Page: {}, Size: {}",
            currentUserUid, otherUserUid, keyword, pageable.pageNumber, pageable.pageSize
        )

        if (keyword.isBlank()) {
            logger.warn("Search keyword is blank for conversation between {} and {}.", currentUserUid, otherUserUid)
            return PaginatedChatMessagesResponseDto(emptyList(), false, null)
        }

        val currentUser = userRepository.findById(currentUserUid)
            .orElseThrow { throw CustomException(ErrorCode.USER_NOT_FOUND) }
        val otherUser = userRepository.findById(otherUserUid)
            .orElseThrow { throw CustomException(ErrorCode.USER_NOT_FOUND) }

        val messagesSlice = chatMessageRepository.searchMessagesBetweenUsers(
            currentUser,
            otherUser,
            keyword,
            pageable
        )

        logger.info(
            "Search found {} messages for keyword '{}' between {} and {}. Has next page: {}",
            messagesSlice.numberOfElements, keyword, currentUserUid, otherUserUid, messagesSlice.hasNext()
        )
        return PaginatedChatMessagesResponseDto.fromSlice(messagesSlice)
    }


    @Transactional
    fun processNewMessage(chatMessageDto: ChatMessageDto, senderPrincipalName: String?) {
        val actualSenderUid = senderPrincipalName ?: chatMessageDto.senderUid
        val receiverUid = chatMessageDto.receiverUid

        if (actualSenderUid.isBlank() || receiverUid == null || receiverUid.isBlank()) {
            logger.warn("processNewMessage: Sender or Receiver UID is missing. Sender: {}, Receiver: {}", actualSenderUid, receiverUid)
            return
        }

        val senderOptional = userRepository.findById(actualSenderUid)
        val receiverOptional = userRepository.findById(receiverUid)

        if (senderOptional.isEmpty || receiverOptional.isEmpty) {
            logger.warn("processNewMessage: Sender or Receiver not found in DB. SenderUID: {}, ReceiverUID: {}", actualSenderUid, receiverUid)
            return
        }
        val sender = senderOptional.get()
        val receiver = receiverOptional.get()

        // --- 바로 이 부분입니다! ---
        // 1. 수신자가 현재 발신자와의 대화창을 보고 있는지 확인
        val isReceiverActive = webSocketActivityService.isUserActiveInChat(
            userUid = receiver.uid,
            partnerUid = sender.uid
        )

        // 2. 활동 상태에 따라 isRead 값을 설정하여 엔티티 생성
        val chatMessageEntity = ChatMessage(
            sender = sender,
            receiver = receiver,
            content = chatMessageDto.content ?: "",
            isRead = isReceiverActive, // 수신자가 활성 상태이면 true, 아니면 false
            readAt = if (isReceiverActive) LocalDateTime.now() else null
        )
        val savedMessageEntity = chatMessageRepository.save(chatMessageEntity)
        logger.info("processNewMessage: Message ID {} saved. From: {}, To: {}. isRead set to: {}",
            savedMessageEntity.id, sender.uid, receiver.uid, savedMessageEntity.isRead)


        val messageToSendDto = ChatMessageDto(
            id = savedMessageEntity.id,
            type = chatMessageDto.type,
            content = savedMessageEntity.content,
            senderUid = sender.uid,
            senderNickname = sender.nickname,
            receiverUid = receiver.uid,
            timestamp = savedMessageEntity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            isRead = savedMessageEntity.isRead // DB에 저장된 최종 isRead 상태 반영
        )

        if (webSocketPresenceService.isUserOnline(receiver.uid)) {
            messagingTemplate.convertAndSendToUser(
                receiver.uid,
                "/queue/private",
                messageToSendDto
            )
            logger.info("processNewMessage: Message ID {} sent via WebSocket to online user UID: {}", savedMessageEntity.id, receiver.uid)
        } else {
            logger.info("processNewMessage: Receiver UID: {} is offline. Checking conditions for FCM.", receiver.uid)
            val resultSlice = chatMessageRepository.findLatestMessageTimeBetweenUsersBefore(
                sender,
                receiver,
                savedMessageEntity.createdAt,
                PageRequest.of(0, 1)
            )
            val lastInteractionTime: LocalDateTime? = if (resultSlice.hasContent()) resultSlice.content[0] else null
            val fiveMinutesAgo = LocalDateTime.now().minusMinutes(5)

            if (lastInteractionTime == null || lastInteractionTime.isBefore(fiveMinutesAgo)) {
                val conversationId = createCanonicalConversationId(sender.uid, receiver.uid)
                if (!fcmSentForOfflineConversation.contains(conversationId)) {
                    logger.info("processNewMessage: Receiver UID: {} is offline AND last interaction was >5 mins ago (or first message). Sending FCM for conversationId: {}.", receiver.uid, conversationId)

                    receiver.fcmToken?.let { token ->
                        if (token.isNotBlank()) {
                            val fcmTitle = "알림"
                            val fcmBody = "새로운 일정이 등록되었습니다."
                            fcmService.sendNotification(
                                token,
                                fcmTitle,
                                fcmBody,
                                mapOf(
                                    "type" to "NEW_CHAT_MESSAGE",
                                    "messageId" to savedMessageEntity.id,
                                    "senderUid" to sender.uid,
                                    "senderNickname" to (sender.nickname ?: "새로운 메시지")
                                )
                            )
                            fcmSentForOfflineConversation.add(conversationId)
                            logger.info("processNewMessage: FCM sent for conversationId: {} and marked as sent.", conversationId)
                        } else {
                            logger.warn("processNewMessage: Receiver UID {} has a blank FCM token. Cannot send FCM for conversationId: {}.", receiver.uid, conversationId)
                        }
                    } ?: logger.warn("processNewMessage: Receiver UID {} has no FCM token. Cannot send FCM for conversationId: {}.", receiver.uid, conversationId)
                } else {
                    logger.info("processNewMessage: FCM for conversationId: {} was already sent recently for this offline period. Suppressing new FCM for message ID {}.", conversationId, savedMessageEntity.id)
                }
            } else {
                logger.info("processNewMessage: Receiver UID: {} is offline BUT last interaction was within 5 mins. FCM not sent for message ID {}.", receiver.uid, savedMessageEntity.id)
            }
        }

        messagingTemplate.convertAndSendToUser(
            sender.uid,
            "/queue/private",
            messageToSendDto
        )
        logger.info("processNewMessage: Sent message feedback (ID {}) to sender UID: {}", savedMessageEntity.id, sender.uid)
    }

    @Transactional
    fun markMessageAsRead(readEventDto: MessageReadEventDto, readerPrincipalName: String?) {
        val readerUid = readerPrincipalName
        if (readerUid == null) {
            logger.warn("markMessageAsRead: User not authenticated. Cannot process read event: {}", readEventDto)
            return
        }

        val partnerUid = readEventDto.partnerUid
        logger.info("User {} is marking all messages from partner {} as read.", readerUid, partnerUid)

        val readerUser = userRepository.findById(readerUid)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        val partnerUser = userRepository.findById(partnerUid)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val unreadMessages = chatMessageRepository.findByReceiverAndSenderAndIsReadFalse(readerUser, partnerUser)

        if (unreadMessages.isEmpty()) {
            logger.info("No unread messages from partner {} to reader {}. Nothing to mark as read.", partnerUid, readerUid)
            return
        }

        val unreadMessageIds = unreadMessages.map { it.id }
        val now = LocalDateTime.now()

        val updatedCount = chatMessageRepository.markMessagesAsReadByIds(unreadMessageIds, now)
        logger.info("Marked {} messages from partner {} to reader {} as read.", updatedCount, partnerUid, readerUid)

        val confirmationDto = MessagesReadConfirmationDto(
            updatedMessageIds = unreadMessageIds,
            readerUid = readerUid,
            readAt = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

        messagingTemplate.convertAndSendToUser(
            partnerUid,
            "/queue/readReceipts",
            confirmationDto
        )
        logger.info("Sent bulk read confirmation for {} messages to original sender UID: {}", updatedCount, partnerUid)

        val conversationId = createCanonicalConversationId(partnerUid, readerUid)
        if (fcmSentForOfflineConversation.remove(conversationId)) {
            logger.info("markMessageAsRead: FCM sent flag cleared for conversationId: {} because receiver read messages.", conversationId)
        }
    }

    @Transactional(readOnly = true)
    fun handleUserJoin(chatMessageDto: ChatMessageDto, senderPrincipalName: String?, sessionAttributes: MutableMap<String, Any>) {
        val actualSenderUid = senderPrincipalName ?: chatMessageDto.senderUid
        if (actualSenderUid.isBlank()){
            logger.warn("handleUserJoin: Sender UID is missing.")
            return
        }

        val senderNickname = userRepository.findById(actualSenderUid).map { it.nickname }.orElse("Unknown User")

        sessionAttributes["userUid"] = actualSenderUid
        sessionAttributes["nickname"] = senderNickname!!
        logger.info("handleUserJoin: User {} ({}) session attributes set.", senderNickname, actualSenderUid)

        val joinMessage = ChatMessageDto(
            type = MessageType.JOIN,
            content = "$senderNickname 님이 입장했습니다.",
            senderUid = actualSenderUid,
            senderNickname = senderNickname,
            receiverUid = null,
            timestamp = System.currentTimeMillis(),
            isRead = true
        )
        messagingTemplate.convertAndSend("/topic/public", joinMessage)
        logger.info("handleUserJoin: Sent JOIN message to /topic/public for user: {}", senderNickname)
    }

    @Transactional
    fun deleteMessage(currentUserUid: String, messageId: String) {
        logger.info("User UID: {} attempting to delete message ID: {}", currentUserUid, messageId)

        val message = chatMessageRepository.findById(messageId)
            .orElseThrow {
                logger.warn("deleteMessage: Message not found with ID: {} for delete attempt by user UID: {}", messageId, currentUserUid)
                throw CustomException(ErrorCode.MESSAGE_NOT_FOUND)
            }

        if (message.sender.uid != currentUserUid) {
            logger.warn("deleteMessage: User UID: {} attempted to delete message ID: {} not owned by them (owner: {}). Forbidden.",
                currentUserUid, messageId, message.sender.uid)
            throw CustomException(ErrorCode.FORBIDDEN_MESSAGE_ACCESS)
        }

        if (message.isDeleted) {
            logger.info("deleteMessage: Message ID: {} was already marked as deleted.", messageId)
            return
        }

        message.isDeleted = true
        message.deletedAt = LocalDateTime.now()
        chatMessageRepository.save(message)
        logger.info("deleteMessage: Message ID: {} marked as deleted by user UID: {}", messageId, currentUserUid)
    }
}