package com.company.rest.api.controller

import com.company.rest.api.dto.WeeklyForecastResponseDto
import com.company.rest.api.service.WeatherService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "Weather Forecasts", description = "주간 일기 예보 조회 API")
class WeatherController(
    private val weatherService: WeatherService
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
                content = [Content(mediaType = "application/json", schema = Schema(implementation = WeeklyForecastResponseDto::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효하지 않은 날짜 형식)"),
            ApiResponse(responseCode = "404", description = "해당 도시의 예보를 찾을 수 없음 (잘못된 도시 이름 또는 데이터 없음)")
        ]
    )
    @GetMapping("/weekly/by-city-name/{cityName}")
    fun getWeeklyForecastByCityName(
        @Parameter(description = "조회할 도시 이름 (예: 서울, 부산)", required = true, example = "서울")
        @PathVariable cityName: String,
        @Parameter(description = "조회 시작 날짜 (YYYY-MM-DD 형식). 제공하지 않으면 오늘 날짜 기준.", required = false, example = "2025-06-03")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<WeeklyForecastResponseDto> {
        val startDate = date ?: LocalDate.now(KOREA_ZONE_ID)
        logger.info("Request received for weekly forecast by city name. CityName: {}, StartDate: {}", cityName, startDate)

        val weeklyForecastDto = weatherService.getWeeklyForecastsByCityName(cityName, startDate)

        return if (weeklyForecastDto != null) {
            ResponseEntity.ok(weeklyForecastDto)
        } else {
            logger.warn("No weekly forecast data found for CityName: {}, StartDate: {}", cityName, startDate)
            ResponseEntity.notFound().build()
        }
    }

    // `/api/v1/weather/regions` 엔드포인트 및 getAvailableRegions() 메서드 삭제됨

    @Operation(
        summary = "수동으로 특정 도시의 단기 및 중기 예보 모두 가져오기 (관리자/테스트용)",
        description = "특정 도시의 단기 예보(05시 발표 기준) 및 중기 예보(06시 발표 기준)를 즉시 가져와 DB에 저장/업데이트합니다. 날짜를 지정하면 해당 날짜 기준으로 가져옵니다. 도시 식별자로 '기온 예보 구역 코드'를 사용합니다.",
        responses = [
            ApiResponse(responseCode = "200", description = "단기 및 중기 예보 가져오기 작업이 성공적으로 요청됨."),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효하지 않은 지역 코드 또는 날짜 형식)"),
            ApiResponse(responseCode = "500", description = "예보 가져오기 작업 중 내부 서버 오류 발생")
        ]
    )
    @PostMapping("/admin/trigger-fetch-city")
    fun manuallyTriggerFetchForCity(
        @Parameter(description = "예보를 가져올 도시의 기온 예보 구역 코드 (RegionCodeConfig의 키 값)", required = true, example = "11B10101")
        @RequestParam cityTempRegId: String,
        @Parameter(description = "기준 발표 날짜 (YYYY-MM-DD 형식). 제공하지 않으면 오늘 날짜 기준.", required = false, example = "2025-06-03")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        targetDate: LocalDate?
    ): ResponseEntity<String> {
        val regionDetails = weatherService.getRegionCodeMap()[cityTempRegId] // 이 부분에서 getRegionCodeMap()이 여전히 필요함
        if (regionDetails == null) {
            return ResponseEntity.badRequest().body("지원하지 않거나 유효하지 않은 도시 지역 코드입니다: $cityTempRegId")
        }

        val kmaTargetDate = targetDate ?: LocalDate.now(KOREA_ZONE_ID)

        val baseDateTimeForShortTerm = kmaTargetDate.atTime(LocalTime.of(5, 0))
        val baseDateTimeForMidTerm = kmaTargetDate.atTime(LocalTime.of(6, 0))

        var successMessage = StringBuilder()
        var errorMessage = StringBuilder()

        try {
            logger.info("Manual trigger for SHORT-TERM forecast for city: {} (Code: {}), baseDateTime: {}", regionDetails.cityName, cityTempRegId, baseDateTimeForShortTerm)
            weatherService.fetchAndStoreShortTermForecastsForRegion(cityTempRegId, baseDateTimeForShortTerm)
            successMessage.append("단기 예보(${baseDateTimeForShortTerm.toLocalTime()} 발표 기준) 가져오기 요청 성공. ")
        } catch (e: Exception) {
            logger.error("Error during manual trigger for fetching SHORT-TERM city {} forecast: {}", cityTempRegId, e.message, e)
            errorMessage.append("단기 예보 가져오기 중 오류 발생: ${e.message}. ")
        }

        try {
            logger.info("Manual trigger for MID-TERM forecast for city: {} (Code: {}), baseDateTime: {}", regionDetails.cityName, cityTempRegId, baseDateTimeForMidTerm)
            weatherService.fetchAndStoreWeeklyForecastsForCity(cityTempRegId, baseDateTimeForMidTerm)
            successMessage.append("중기 예보(${baseDateTimeForMidTerm.toLocalTime()} 발표 기준) 가져오기 요청 성공.")
        } catch (e: Exception) {
            logger.error("Error during manual trigger for fetching MID-TERM city {} forecast: {}", cityTempRegId, e.message, e)
            errorMessage.append("중기 예보 가져오기 중 오류 발생: ${e.message}.")
        }

        if (errorMessage.isNotEmpty()) {
            val finalMessage = "도시(${regionDetails.cityName}, 코드: $cityTempRegId) 예보 가져오기 요청 결과: ${successMessage}${errorMessage}처리 결과는 서버 로그를 확인해주세요."
            return ResponseEntity.internalServerError().body(finalMessage)
        }

        val finalMessage = "도시(${regionDetails.cityName}, 코드: $cityTempRegId)에 대한 단기 및 중기 예보 가져오기 작업이 요청되었습니다. ${successMessage}처리 결과는 서버 로그를 확인해주세요."
        logger.info(finalMessage)
        return ResponseEntity.ok(finalMessage)
    }
}