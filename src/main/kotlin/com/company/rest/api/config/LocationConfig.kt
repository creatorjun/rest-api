package com.company.rest.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 각 위치의 상세 정보를 담을 데이터 클래스
data class LocationDetails(
    val cityName: String,      // 도시 이름 (예: "서울특별시")
    val latitude: Double,      // 위도
    val longitude: Double      // 경도
)

@Configuration
class LocationConfig {

    @Bean
    fun locationDetailsList(): List<LocationDetails> {
        return listOf(
            LocationDetails("서울특별시", 37.56, 127.00),
            LocationDetails("인천광역시", 37.45, 126.70),
            LocationDetails("경기도", 37.29, 127.06),
            LocationDetails("강원도", 37.99, 128.02),
            LocationDetails("충청북도", 36.74, 127.83),
            LocationDetails("충청남도", 36.36, 126.97),
            LocationDetails("전라북도", 35.85, 127.05),
            LocationDetails("전라남도", 34.95, 126.79),
            LocationDetails("경상북도", 36.43, 128.58),
            LocationDetails("경상남도", 35.50, 128.30),
            LocationDetails("부산광역시", 35.10, 129.03),
            LocationDetails("대구광역시", 35.82, 128.58),
            LocationDetails("광주광역시", 35.16, 126.89),
            LocationDetails("대전광역시", 36.33, 127.36),
            LocationDetails("울산광역시", 35.53, 129.33),
            LocationDetails("세종특별자치시", 36.48, 127.26),
            LocationDetails("제주특별자치도", 33.36, 126.56)
        )
    }
}