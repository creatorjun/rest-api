package com.company.rest.api.dto

import jakarta.validation.constraints.NotBlank

data class EventIdRequestDto( // 클래스 이름 변경: MemoIdRequestDto -> EventIdRequestDto
    @field:NotBlank(message = "이벤트 ID는 비워둘 수 없습니다.") // 필드 이름 및 메시지 변경: memoId -> eventId, 메모 -> 이벤트
    val eventId: String
)