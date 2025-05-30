package com.company.rest.api.dto

import com.company.rest.api.entity.ChatMessage // ChatMessage 엔티티 임포트
import org.springframework.data.domain.Slice // Slice 임포트
import java.time.ZoneId

/**
 * 페이징 처리된 채팅 메시지 목록을 담는 응답 DTO 입니다.
 * @param messages 현재 페이지의 ChatMessageDto 목록 (시간 역순, 즉 최신 메시지가 앞쪽)
 * @param hasNextPage 더 이전의 메시지 페이지가 있는지 여부
 * @param oldestMessageTimestamp 현재 로드된 메시지 중 가장 오래된 메시지의 타임스탬프 (다음 페이지 요청 시 커서로 사용)
 */
data class PaginatedChatMessagesResponseDto(
    val messages: List<ChatMessageDto>,
    val hasNextPage: Boolean,
    val oldestMessageTimestamp: Long? // Long 타입의 epoch milliseconds
) {
    companion object {
        /**
         * ChatMessage 엔티티의 Slice와 사용자 UID를 받아 PaginatedChatMessagesResponseDto를 생성합니다.
         * 메시지 목록은 ChatMessageDto로 변환됩니다.
         */
        fun fromSlice(
            slice: Slice<ChatMessage>
            // currentUserUid: String // 현재 사용자를 기준으로 isRead 등을 재확인하거나, DTO를 다르게 구성할 때 필요할 수 있음
        ): PaginatedChatMessagesResponseDto {
            // ChatMessage 엔티티 리스트를 ChatMessageDto 리스트로 변환
            // 리포지토리에서 createdAt DESC로 정렬했으므로, DTO 리스트도 최신 메시지가 맨 앞에 오게 됩니다.
            val messageDtos = slice.content.map { entity ->
                ChatMessageDto(
                    id = entity.id,
                    type = MessageType.CHAT, // 대화 내역 조회이므로 CHAT 타입으로 가정
                    content = entity.content,
                    senderUid = entity.sender.uid,
                    senderNickname = entity.sender.nickname, // User 엔티티에 nickname 필드가 있다고 가정
                    receiverUid = entity.receiver.uid,
                    timestamp = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    isRead = entity.isRead // DB에 저장된 읽음 상태 반영
                )
            }

            // 현재 페이지의 메시지 중 가장 오래된 메시지의 타임스탬프
            // 메시지 목록(messageDtos)이 비어있지 않다면, 마지막 메시지(가장 오래된 메시지)의 타임스탬프를 사용
            val oldestTimestamp = if (messageDtos.isNotEmpty()) {
                messageDtos.last().timestamp
            } else {
                null
            }

            return PaginatedChatMessagesResponseDto(
                messages = messageDtos,
                hasNextPage = slice.hasNext(), // Slice 객체는 다음 페이지 존재 여부를 알려줌
                oldestMessageTimestamp = oldestTimestamp
            )
        }
    }
}