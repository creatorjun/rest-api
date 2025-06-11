package com.company.rest.api.controller

import com.company.rest.api.service.WeatherService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/weather") // <--- 클래스 레벨의 기본 경로 변경
@Tag(name = "Weather Admin", description = "날씨 정보 수동 관리 API")
class WeatherController(
    private val weatherService: WeatherService
) {
    private val logger = LoggerFactory.getLogger(WeatherController::class.java)

    @Operation(
        summary = "모든 날씨 정보 수동으로 가져오기 (관리자용)",
        description = "서버에 설정된 모든 지역의 현재, 1시간, 일일 예보를 Apple WeatherKit으로부터 즉시 가져와 데이터베이스에 저장합니다."
    )
    @ApiResponse(responseCode = "200", description = "모든 날씨 정보 가져오기 작업이 성공적으로 실행됨")
    @PostMapping("/fetch-all") // <--- 하나의 통합 엔드포인트로 변경
    fun triggerAllWeatherFetches(): ResponseEntity<String> {
        logger.info("Manual trigger request received for fetching ALL weather data.")
        try {
            weatherService.fetchAndStoreCurrentWeather()
            weatherService.fetchAndStoreHourlyForecasts()
            weatherService.fetchAndStoreDailyForecasts()

            val message = "모든 종류(현재, 1시간, 일일)의 날씨 정보 가져오기 작업이 성공적으로 실행되었습니다."
            logger.info(message)
            return ResponseEntity.ok(message)
        } catch (e: Exception) {
            val errorMessage = "모든 날씨 정보 수동 실행 중 오류 발생: ${e.message}"
            logger.error(errorMessage, e)
            return ResponseEntity.internalServerError().body(errorMessage)
        }
    }
}