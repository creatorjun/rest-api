package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

/**
 * 클라이언트(메시지 수신자)가 특정 파트너와의 대화 메시지를 모두 읽었음을 서버에 알릴 때 사용하는 DTO 입니다.
 */
data class MessageReadEventDto(
    @field:NotBlank(message = "읽음 처리할 대화 상대의 UID는 비워둘 수 없습니다.")
    val partnerUid: String // 내가 대화하고 있는 상대방(메시지를 보낸 사람)의 UID
)