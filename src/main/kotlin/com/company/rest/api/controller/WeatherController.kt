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
@RequestMapping("/api/v1/weather")
@Tag(name = "Weather", description = "날씨 정보 조회 및 관리 API")
class WeatherController(
    private val weatherService: WeatherService
) {
    private val logger = LoggerFactory.getLogger(WeatherController::class.java)

    @Operation(
        summary = "현재 날씨 정보 수동으로 가져오기 (테스트용)",
        description = "서버에 설정된 모든 지역의 현재 날씨 정보를 Apple WeatherKit으로부터 즉시 가져와 데이터베이스에 저장합니다."
    )
    @ApiResponse(responseCode = "200", description = "가져오기 작업이 성공적으로 실행됨")
    @PostMapping("/admin/fetch-current")
    fun triggerCurrentWeatherFetch(): ResponseEntity<String> {
        logger.info("Manual trigger request received for fetching current weather.")
        return try {
            weatherService.fetchAndStoreCurrentWeather()
            val message = "모든 지역의 현재 날씨 정보 가져오기 작업이 성공적으로 실행되었습니다. 서버 로그와 데이터베이스를 확인해주세요."
            logger.info(message)
            ResponseEntity.ok(message)
        } catch (e: Exception) {
            val errorMessage = "현재 날씨 정보 수동 실행 중 오류 발생: ${e.message}"
            logger.error(errorMessage, e)
            ResponseEntity.internalServerError().body(errorMessage)
        }
    }

    @Operation(
        summary = "1시간 예보 정보 수동으로 가져오기 (테스트용)",
        description = "서버에 설정된 모든 지역의 1시간 분단위 예보를 Apple WeatherKit으로부터 즉시 가져와 데이터베이스에 저장합니다."
    )
    @ApiResponse(responseCode = "200", description = "가져오기 작업이 성공적으로 실행됨")
    @PostMapping("/admin/fetch-hourly")
    fun triggerHourlyForecastFetch(): ResponseEntity<String> {
        logger.info("Manual trigger request received for fetching hourly forecasts.")
        return try {
            weatherService.fetchAndStoreHourlyForecasts()
            val message = "모든 지역의 1시간 예보 정보 가져오기 작업이 성공적으로 실행되었습니다. 서버 로그와 데이터베이스를 확인해주세요."
            logger.info(message)
            ResponseEntity.ok(message)
        } catch (e: Exception) {
            val errorMessage = "1시간 예보 정보 수동 실행 중 오류 발생: ${e.message}"
            logger.error(errorMessage, e)
            ResponseEntity.internalServerError().body(errorMessage)
        }
    }

    @Operation(
        summary = "일일 예보 정보 수동으로 가져오기 (테스트용)",
        description = "서버에 설정된 모든 지역의 일일 예보를 Apple WeatherKit으로부터 즉시 가져와 데이터베이스에 저장합니다."
    )
    @ApiResponse(responseCode = "200", description = "가져오기 작업이 성공적으로 실행됨")
    @PostMapping("/admin/fetch-daily")
    fun triggerDailyForecastFetch(): ResponseEntity<String> {
        logger.info("Manual trigger request received for fetching daily forecasts.")
        return try {
            weatherService.fetchAndStoreDailyForecasts()
            val message = "모든 지역의 일일 예보 정보 가져오기 작업이 성공적으로 실행되었습니다. 서버 로그와 데이터베이스를 확인해주세요."
            logger.info(message)
            ResponseEntity.ok(message)
        } catch (e: Exception) {
            val errorMessage = "일일 예보 정보 수동 실행 중 오류 발생: ${e.message}"
            logger.error(errorMessage, e)
            ResponseEntity.internalServerError().body(errorMessage)
        }
    }
}