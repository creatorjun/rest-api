package com.company.rest.api.dto

/**
 * 서버가 메시지 발신자에게 특정 메시지가 수신자에 의해 읽혔음을 알릴 때 사용하는 DTO 입니다.
 */
data class MessageReadConfirmationDto(
    val messageId: String, // 읽음 처리된 메시지의 고유 ID

    val readerUid: String, // 메시지를 읽은 사용자(수신자)의 UID

    val readAt: Long?, // 메시지가 읽힌 시간 (타임스탬프, epoch milliseconds). nullable로 처리하여 시간이 중요하지 않거나 없을 경우 대비.

    // 만약 채팅방 ID 등 추가적인 컨텍스트 정보가 필요하다면 여기에 필드를 추가할 수 있습니다.
    // val chatRoomId: String?
)