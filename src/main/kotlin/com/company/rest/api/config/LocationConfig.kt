package com.company.rest.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

data class LocationDetails(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val airkoreaRegionName: String
)

@Configuration
class LocationConfig {

    @Bean
    fun locationDetailsList(): List<LocationDetails> {
        return listOf(
            LocationDetails("서울특별시", 37.56, 127.00, "서울"),
            LocationDetails("인천광역시", 37.45, 126.70, "인천"),
            LocationDetails("경기도", 37.29, 127.06, "경기남부"),
            LocationDetails("강원도", 37.99, 128.02, "강원영서"),
            LocationDetails("충청북도", 36.74, 127.83, "충북"),
            LocationDetails("충청남도", 36.36, 126.97, "충남"),
            LocationDetails("전라북도", 35.85, 127.05, "전북"),
            LocationDetails("전라남도", 34.95, 126.79, "전남"),
            LocationDetails("경상북도", 36.43, 128.58, "경북"),
            LocationDetails("경상남도", 35.50, 128.30, "경남"),
            LocationDetails("부산광역시", 35.10, 129.03, "부산"),
            LocationDetails("대구광역시", 35.82, 128.58, "대구"),
            LocationDetails("광주광역시", 35.16, 126.89, "광주"),
            LocationDetails("대전광역시", 36.33, 127.36, "대전"),
            LocationDetails("울산광역시", 35.53, 129.33, "울산"),
            LocationDetails("세종특별자치시", 36.48, 127.26, "세종"),
            LocationDetails("제주특별자치도", 33.36, 126.56, "제주")
        )
    }
}