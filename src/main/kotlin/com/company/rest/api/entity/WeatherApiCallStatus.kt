package com.company.rest.api.entity

enum class WeatherApiCallStatus {
    PENDING,            // 처리 대기 중 (API 호출 전 또는 처리 중)
    SUCCESS,            // 모든 API 호출 및 데이터 파싱, 저장 성공
    PARTIAL_SUCCESS,    // 일부 API 호출 또는 데이터 처리만 성공 (예: 기온 정보는 성공, 육상 예보는 실패)
    API_ERROR_LAND,     // 중기육상예보 API 호출 실패
    API_ERROR_TEMP,     // 중기기온예보 API 호출 실패
    API_ERROR_UNKNOWN,  // 기타 알 수 없는 API 호출 오류 (예: 기상청 서버의 일반 오류 응답)
    PARSING_FAILED,     // API 응답은 성공했으나, JSON 파싱 실패
    NO_DATA_FOUND       // API 응답은 성공했으나, 필요한 데이터 항목이 없는 경우 (예: items 리스트가 비어있음)
}