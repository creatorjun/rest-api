package com.company.rest.api.dto

import com.company.rest.api.entity.ChatMessage
import org.springframework.data.domain.Slice
import java.time.ZoneId

data class PaginatedChatMessagesResponseDto(
    val messages: List<ChatMessageDto>,
    val hasNextPage: Boolean,
    val oldestMessageTimestamp: Long?
) {
    companion object {
        fun fromSlice(
            slice: Slice<ChatMessage>,
            eventDetailsMap: Map<String, EventResponseDto>
        ): PaginatedChatMessagesResponseDto {
            val messageDtos = slice.content.map { entity ->
                val dto = ChatMessageDto(
                    id = entity.id,
                    type = entity.type, // DB에 저장된 실제 타입으로 설정
                    content = entity.content,
                    senderUid = entity.sender.uid,
                    senderNickname = entity.sender.nickname,
                    receiverUid = entity.receiver.uid,
                    timestamp = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    isRead = entity.isRead
                )

                // 메시지 타입이 SCHEDULE이면, 미리 조회해온 eventDetailsMap에서 상세 정보를 찾아 설정
                if (dto.type == MessageType.SCHEDULE) {
                    dto.eventDetails = eventDetailsMap[entity.content] // entity.content가 eventId임
                }

                dto
            }

            val oldestTimestamp = if (messageDtos.isNotEmpty()) {
                messageDtos.last().timestamp
            } else {
                null
            }

            return PaginatedChatMessagesResponseDto(
                messages = messageDtos,
                hasNextPage = slice.hasNext(),
                oldestMessageTimestamp = oldestTimestamp
            )
        }
    }
}