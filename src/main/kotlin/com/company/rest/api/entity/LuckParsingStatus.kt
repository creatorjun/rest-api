package com.company.rest.api.entity

enum class LuckParsingStatus {
    PENDING,       // 처리 대기 중
    SUCCESS,       // 성공적으로 파싱 및 저장 완료
    PARSING_FAILED, // JSON 파싱 실패
    GEMINI_ERROR,  // 제미나이 API 응답 오류 또는 빈 응답
    NO_DATA        // 제미나이로부터 응답은 받았으나 내용이 없는 경우
}