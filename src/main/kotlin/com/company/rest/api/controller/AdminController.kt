package com.company.rest.api.controller

import com.company.rest.api.service.GeminiService
import com.company.rest.api.service.WeatherService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "서버 관리용 API (내부 테스트용)")
class AdminController(
    private val weatherService: WeatherService,
    private val geminiService: GeminiService
) {
    private val logger = LoggerFactory.getLogger(AdminController::class.java)
    private val KOREA_ZONE_ID = ZoneId.of("Asia/Seoul")

    // --- Weather Admin Endpoints ---
    @Operation(
        summary = "모든 날씨 정보 수동으로 가져오기 (관리자용)",
        description = "서버에 설정된 모든 지역의 현재, 1시간, 일일 예보를 Apple WeatherKit으로부터 즉시 가져와 데이터베이스에 저장합니다."
    )
    @ApiResponse(responseCode = "200", description = "모든 날씨 정보 가져오기 작업이 성공적으로 실행됨")
    @PostMapping("/weather")
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

    // --- Luck Admin Endpoint ---
    @Operation(
        summary = "오늘의 운세 정보 수동으로 가져오기 (관리자용)",
        description = "오늘 날짜의 운세 정보를 제미나이로부터 즉시 가져와 데이터베이스에 저장/업데이트합니다."
    )
    @ApiResponse(responseCode = "200", description = "운세 정보 가져오기 작업이 성공적으로 시작됨.")
    @PostMapping("/luck")
    fun manuallyTriggerFetchTodaysLuck(): ResponseEntity<String> {
        val today = LocalDate.now(KOREA_ZONE_ID)
        logger.info("Manual trigger request received to fetch and store Lucks for today: {}", today)
        try {
            geminiService.fetchAndStoreDailyLuck(today)
            val message = "오늘 ($today) 운세 정보 가져오기 및 저장 작업이 시작되었습니다. 처리 결과는 서버 로그와 데이터베이스를 확인해주세요."
            logger.info(message)
            return ResponseEntity.ok(message)
        } catch (e: Exception) {
            val errorMessage = "오늘 ($today) 운세 정보 가져오기 작업 시작 중 오류가 발생했습니다: ${e.message}"
            logger.error(errorMessage, e)
            return ResponseEntity.internalServerError().body(errorMessage)
        }
    }
}