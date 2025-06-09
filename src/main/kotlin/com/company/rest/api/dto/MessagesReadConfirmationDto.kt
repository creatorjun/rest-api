package com.company.rest.api.dto

/**
 * 서버가 메시지 발신자에게 여러 메시지가 수신자에 의해 읽혔음을 한 번에 알릴 때 사용하는 DTO 입니다.
 * 이 DTO는 WebSocket을 통해 /queue/readReceipts 토픽으로 전송됩니다.
 */
data class MessagesReadConfirmationDto(
    val updatedMessageIds: List<String>, // 읽음 처리된 메시지들의 ID 목록
    val readerUid: String,               // 메시지를 읽은 사용자(수신자)의 UID
    val readAt: Long?                    // 메시지들이 읽힌 시간 (타임스탬프, epoch milliseconds)
)