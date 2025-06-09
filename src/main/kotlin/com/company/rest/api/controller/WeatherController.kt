package com.company.rest.api.controller

import com.company.rest.api.dto.WeeklyForecastResponseDto
import com.company.rest.api.scheduler.WeatherScheduler
import com.company.rest.api.service.WeatherService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "Weather Forecasts", description = "주간 일기 예보 조회 및 스케줄러 수동 실행 API")
class WeatherController(
    private val weatherService: WeatherService,
    private val weatherScheduler: WeatherScheduler
) {
    private val logger = LoggerFactory.getLogger(WeatherController::class.java)
    private val KOREA_ZONE_ID = ZoneId.of("Asia/Seoul")

    @Operation(
        summary = "특정 도시 이름으로 주간 일기 예보 조회",
        description = """
            지정된 도시 이름(예: "서울", "부산")에 대해 특정 시작일로부터 일주일간의 일기 예보를 반환합니다.
            도시 이름은 시스템에 미리 정의된 이름을 사용해야 합니다. (예: RegionCodeConfig 참조)
            시작 날짜를 제공하지 않으면 오늘 날짜를 기준으로 조회합니다.
        """,
        responses = [
            ApiResponse(
                responseCode = "200", description = "주간 예보 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = WeeklyForecastResponseDto::class)
                )]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효하지 않은 날짜 형식)"),
            ApiResponse(responseCode = "404", description = "해당 도시의 예보를 찾을 수 없음 (잘못된 도시 이름 또는 데이터 없음)")
        ]
    )
    @GetMapping("/weekly/by-city-name/{cityName}")
    fun getWeeklyForecastByCityName(
        @Parameter(description = "조회할 도시 이름 (예: 서울, 부산)", required = true, example = "서울")
        @PathVariable cityName: String,
        @Parameter(
            description = "조회 시작 날짜 (YYYY-MM-DD 형식). 제공하지 않으면 오늘 날짜 기준.",
            required = false,
            example = "2025-06-04"
        )
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<WeeklyForecastResponseDto> {
        val startDate = date ?: LocalDate.now(KOREA_ZONE_ID)
        logger.info(
            "Request received for weekly forecast by city name. CityName: {}, StartDate: {}",
            cityName,
            startDate
        )

        val weeklyForecastDto = weatherService.getWeeklyForecastsByCityName(cityName, startDate)

        return if (weeklyForecastDto != null) {
            ResponseEntity.ok(weeklyForecastDto)
        } else {
            logger.warn("No weekly forecast data found for CityName: {}, StartDate: {}", cityName, startDate)
            ResponseEntity.notFound().build()
        }
    }

    @Operation(
        summary = "날씨 정보 스케줄러 수동 실행 (관리자/테스트용)",
        description = """
            등록된 날씨 정보 스케줄러 작업(단기예보 및 중기예보 가져오기)들을 즉시 일괄 실행합니다.
            한국 시간 오전 6시 10분을 기준으로, 이전 시간에 요청하면 전날 데이터를, 이후 시간에 요청하면 당일 데이터를 가져옵니다.
            이는 오전 5시 15분 및 6시 10분에 발표되는 최신 데이터를 올바르게 가져오기 위함입니다.
        """,
        responses = [
            ApiResponse(responseCode = "200", description = "날씨 정보 스케줄러 작업들이 성공적으로 실행 요청됨."),
            ApiResponse(responseCode = "500", description = "스케줄러 작업 실행 중 내부 서버 오류 발생")
        ]
    )
    @PostMapping("/admin/fetch")
    fun triggerWeatherTasks(): ResponseEntity<String> {
        logger.info("Manual trigger request received for all weather tasks.")

        val nowInKorea = LocalTime.now(KOREA_ZONE_ID)
        val cutoffTime = LocalTime.of(6, 10)

        // 오전 6시 10분 이전에 실행하면 전날 데이터를, 이후에 실행하면 당일 데이터를 기준으로 함
        val targetDate = if (nowInKorea.isBefore(cutoffTime)) {
            LocalDate.now(KOREA_ZONE_ID).minusDays(1)
        } else {
            LocalDate.now(KOREA_ZONE_ID)
        }

        logger.info(
            "Manual trigger logic: Current time is {}. Cutoff is {}. Target date for fetching is set to {}.",
            nowInKorea,
            cutoffTime,
            targetDate
        )

        try {
            logger.info("Triggering runFetchShortTermForecasts manually for date: {}", targetDate)
            weatherScheduler.runFetchShortTermForecasts(targetDate)

            logger.info("Triggering runFetchMidTermForecasts manually for date: {}", targetDate)
            weatherScheduler.runFetchMidTermForecasts(targetDate)

            val message = "단기 및 중기 예보 가져오기 작업이 수동으로 실행 요청되었습니다. (기준일: $targetDate) 처리 결과는 서버 로그를 확인해주세요."
            logger.info(message)
            return ResponseEntity.ok(message)
        } catch (e: Exception) {
            logger.error("Error during manual trigger of weather tasks for date {}: {}", targetDate, e.message, e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("날씨 스케줄러 작업 실행 중 오류가 발생했습니다: ${e.message}")
        }
    }
}