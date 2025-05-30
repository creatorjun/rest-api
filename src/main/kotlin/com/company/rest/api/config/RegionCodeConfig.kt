package com.company.rest.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RegionCodeConfig {

    @Bean
    fun regionCodeMap(): Map<String, Pair<String, String>> {
        return mapOf(
            // 기온 예보 구역 코드 to Pair(도시명, 중기육상예보 구역 코드)
            "11B10101" to Pair("서울", "11B00000"),
            "11H20201" to Pair("부산", "11H20000"),
            "11H10701" to Pair("대구", "11H10000"),
            "11B20201" to Pair("인천", "11B00000"),
            "11F20501" to Pair("광주", "11F20000"),
            "11C20401" to Pair("대전", "11C20000"),
            "11H20101" to Pair("울산", "11H20000"),
            "11B20601" to Pair("수원", "11B00000"), // 경기도 대표
            "11D10301" to Pair("춘천", "11D10000"), // 강원도 영서 대표
            "11D20501" to Pair("강릉", "11D20000"), // 강원도 영동 대표
            "11C10301" to Pair("청주", "11C10000"), // 충청북도 대표
            "11F10201" to Pair("전주", "11F10000"), // 전라북도 대표
            "11H10201" to Pair("포항", "11H10000"), // 경상북도 대표
            "11G00201" to Pair("제주", "11G00000")  // 제주도 대표
            // 필요에 따라 다른 지역 코드 추가
        )
    }
}