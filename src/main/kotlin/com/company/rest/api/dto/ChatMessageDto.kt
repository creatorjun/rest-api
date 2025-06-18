package com.company.rest.api.dto

// 메시지 유형 정의
enum class MessageType {
    CHAT, JOIN, LEAVE, DATE, SCHEDULE
}

data class ChatMessageDto(
    var id: String? = null, // DB에 저장된 후 생성되는 메시지 ID (클라이언트 -> 서버 시에는 null일 수 있음)
    val type: MessageType,
    val content: String?,
    val senderUid: String,
    var senderNickname: String?, // DB 조회 후 실제 닉네임으로 채워줄 수 있음
    val receiverUid: String?, // 1:1 채팅 시 필수
    var timestamp: Long = System.currentTimeMillis(), // 메시지 발송/생성 시간
    var isRead: Boolean = false, // 메시지 읽음 상태 (DB 조회 후 설정)

    // SCHEDULE 타입일 경우, 여기에 이벤트 상세 정보가 담겨 클라이언트로 전송됩니다.
    var eventDetails: EventResponseDto? = null
)