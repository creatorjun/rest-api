package com.company.rest.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 각 지역의 상세 정보를 담을 데이터 클래스
data class RegionDetails(
    val cityName: String,          // 도시 이름 (예: "서울")
    val landFcstRegId: String,     // 중기 육상 예보 구역 코드 (예: "11B00000")
    val nx: Int,                   // 단기 예보 X 좌표
    val ny: Int                    // 단기 예보 Y 좌표
)

@Configuration
class RegionCodeConfig {

    @Bean
    fun regionCodeMap(): Map<String, RegionDetails> {
        return mapOf(
            // 기온 예보 구역 코드 to RegionDetails
            "11B10101" to RegionDetails("서울", "11B00000", 60, 127),
            "11H20201" to RegionDetails("부산", "11H20000", 98, 76),
            "11H10701" to RegionDetails("대구", "11H10000", 89, 90),
            "11B20201" to RegionDetails("인천", "11B00000", 55, 124),
            "11F20501" to RegionDetails("광주", "11F20000", 58, 74),
            "11C20401" to RegionDetails("대전", "11C20000", 67, 100),
            "11H20101" to RegionDetails("울산", "11H20000", 102, 84),
            "11B20601" to RegionDetails("수원", "11B00000", 60, 121), // 경기도 대표
            "11D10301" to RegionDetails("춘천", "11D10000", 73, 134), // 강원도 영서 대표
            "11D20501" to RegionDetails("강릉", "11D20000", 92, 131), // 강원도 영동 대표
            "11C10301" to RegionDetails("청주", "11C10000", 69, 106), // 충청북도 대표
            "11F10201" to RegionDetails("전주", "11F10000", 63, 89), // 전라북도 대표
            "11H10201" to RegionDetails("포항", "11H10000", 102, 94), // 경상북도 대표
            "11G00201" to RegionDetails("제주", "11G00000", 52, 38)   // 제주도 대표
            // 필요에 따라 다른 지역 코드 및 정보 추가
        )
    }
}