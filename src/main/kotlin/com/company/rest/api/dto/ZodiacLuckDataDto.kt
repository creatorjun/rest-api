package com.company.rest.api.dto

// 이전 엔티티 임포트 com.company.rest.api.entity.ZodiacSignHoroscope 제거
import com.company.rest.api.entity.ZodiacSignLuck
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate

data class ZodiacLuckDataDto( // 클래스 이름 변경
    val requestDate: LocalDate, // 운세 날짜
    val zodiacName: String, // 띠 이름 (예: "쥐띠")
    val applicableYears: List<String>, // 해당 년도 목록
    val overallLuck: String?, // 오늘의 총운
    val financialLuck: String?, // 금전운
    val loveLuck: String?, // 애정운
    val healthLuck: String?, // 건강운
    val luckyNumber: Int?, // 행운의 숫자
    val luckyColor: String?, // 행운의 색상
    val advice: String? // 오늘의 조언
) {
    companion object {
        // ZodiacSignLuck 엔티티를 ZodiacLuckDataDto로 변환하는 확장 함수 또는 정적 메소드
        fun fromEntity(
            entity: ZodiacSignLuck,
            requestDate: LocalDate,
            objectMapper: ObjectMapper
        ): ZodiacLuckDataDto { // 파라미터 타입 변경
            val applicableYearsList: List<String> = try {
                entity.applicableYearsJson?.let {
                    objectMapper.readValue(it, object : TypeReference<List<String>>() {})
                } ?: emptyList()
            } catch (e: Exception) {
                // 로깅 추가 가능
                emptyList() // 파싱 실패 시 빈 리스트 반환
            }

            return ZodiacLuckDataDto(
                requestDate = requestDate,
                zodiacName = entity.zodiacName,
                applicableYears = applicableYearsList,
                overallLuck = entity.overallLuck,
                financialLuck = entity.financialLuck,
                loveLuck = entity.loveLuck,
                healthLuck = entity.healthLuck,
                luckyNumber = entity.luckyNumber,
                luckyColor = entity.luckyColor,
                advice = entity.advice
            )
        }
    }
}