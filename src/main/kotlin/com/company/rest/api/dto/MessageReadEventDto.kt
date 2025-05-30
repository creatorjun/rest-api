package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

/**
 * 클라이언트(메시지 수신자)가 특정 메시지를 읽었음을 서버에 알릴 때 사용하는 DTO 입니다.
 */
data class MessageReadEventDto(
    @field:NotBlank(message = "읽음 처리할 메시지 ID는 비워둘 수 없습니다.")
    val messageId: String // 수신자가 읽은 메시지의 고유 ID

    // 만약 채팅방 ID 등 추가적인 컨텍스트 정보가 필요하다면 여기에 필드를 추가할 수 있습니다.
    // val chatRoomId: String?
)