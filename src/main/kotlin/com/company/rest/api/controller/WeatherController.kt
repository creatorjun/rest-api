package com.company.rest.api.controller

import com.company.rest.api.dto.WeatherForecastResponseDto
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
        summary = "특정 지역의 주간 일기 예보 조회",
        description = """
            지정된 지역 코드에 대해 특정 시작일로부터 일주일간의 일기 예보를 반환합니다.
            지역 코드는 기상청의 기온 예보 구역 코드를 사용합니다 (예: 서울 "11B10101").
            시작 날짜를 제공하지 않으면 오늘 날짜를 기준으로 조회합니다.
        """,
        responses = [
            ApiResponse(
                responseCode = "200", description = "주간 예보 조회 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = WeeklyForecastResponseDto::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효하지 않은 지역 코드 또는 날짜 형식)"),
            ApiResponse(responseCode = "404", description = "해당 지역의 예보를 찾을 수 없음")
        ]
    )
    @GetMapping("/weekly/{regionCode}")
    fun getWeeklyForecast(
        @Parameter(description = "조회할 지역의 기온 예보 구역 코드 (예: 11B10101)", required = true, example = "11B10101")
        @PathVariable regionCode: String,
        @Parameter(description = "조회 시작 날짜 (YYYY-MM-DD 형식). 제공하지 않으면 오늘 날짜 기준.", required = false, example = "2023-10-28")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<WeeklyForecastResponseDto> {
        val startDate = date ?: LocalDate.now(KOREA_ZONE_ID)
        logger.info("Request received for weekly forecast. RegionCode: {}, StartDate: {}", regionCode, startDate)

        if (!weatherService.representativeCityTempCodes.contains(regionCode)) {
            logger.warn("Invalid or unsupported region code provided: {}", regionCode)
            return ResponseEntity.badRequest().build()
        }

        val dailyForecastEntities = weatherService.getWeeklyForecasts(regionCode, startDate)

        if (dailyForecastEntities.isEmpty()) {
            logger.warn("No weekly forecast data found for RegionCode: {}, StartDate: {}", regionCode, startDate)
            return ResponseEntity.notFound().build()
        }

        val forecastDtos = dailyForecastEntities.map { WeatherForecastResponseDto.fromEntity(it) }
        // ✍️ 수정된 부분: weatherService.getRegionCodeMap() 사용
        val regionName = weatherService.getRegionCodeMap()[regionCode]?.first

        val response = WeeklyForecastResponseDto(
            regionCode = regionCode,
            regionName = regionName ?: "알 수 없는 지역",
            forecasts = forecastDtos
        )
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "조회 가능한 대표 도시(지역) 목록 조회",
        description = "일기 예보를 조회할 수 있는 대표 도시(지역)의 코드와 이름 목록을 반환합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "지역 목록 조회 성공",
        content = [Content(mediaType = "application/json", schema = Schema(type = "array", implementation = Map::class))]
    )
    @GetMapping("/regions")
    fun getAvailableRegions(): ResponseEntity<List<Map<String, String>>> {
        // ✍️ 수정된 부분: weatherService.getRegionCodeMap() 사용
        val regions = weatherService.getRegionCodeMap().map { entry ->
            mapOf("regionCode" to entry.key, "regionName" to entry.value.first)
        }
        return ResponseEntity.ok(regions)
    }


    @Operation(
        summary = "수동으로 특정 도시 예보 가져오기 (관리자/테스트용)",
        description = "특정 도시의 오늘자 06시 발표 기준 주간 예보를 즉시 가져와 DB에 저장/업데이트합니다. 날짜를 지정하면 해당 날짜 06시 기준.",
        responses = [
            ApiResponse(responseCode = "200", description = "예보 가져오기 작업이 성공적으로 요청됨."),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효하지 않은 지역 코드 또는 날짜 형식)"),
            ApiResponse(responseCode = "500", description = "예보 가져오기 작업 중 내부 서버 오류 발생")
        ]
    )
    @PostMapping("/admin/trigger-fetch-city")
    fun manuallyTriggerFetchForCity(
        @Parameter(description = "예보를 가져올 도시의 기온 예보 구역 코드", required = true, example = "11B10101")
        @RequestParam cityTempRegId: String,
        @Parameter(description = "기준 발표 날짜 (YYYY-MM-DD 형식). 제공하지 않으면 오늘 날짜의 06시 발표 기준.", required = false, example = "2023-10-28")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        targetDate: LocalDate?
    ): ResponseEntity<String> {
        if (!weatherService.representativeCityTempCodes.contains(cityTempRegId)) {
            return ResponseEntity.badRequest().body("지원하지 않거나 유효하지 않은 도시 지역 코드입니다: $cityTempRegId")
        }

        val kmaBaseDate = targetDate ?: LocalDate.now(KOREA_ZONE_ID)
        val baseDateTimeForKMA = kmaBaseDate.atTime(LocalTime.of(6,0))

        try {
            logger.info("Manual trigger request for city: {}, baseDateTime: {}", cityTempRegId, baseDateTimeForKMA)
            weatherService.fetchAndStoreWeeklyForecastsForCity(cityTempRegId, baseDateTimeForKMA)
            val message = "도시($cityTempRegId)에 대한 예보($baseDateTimeForKMA 발표 기준) 가져오기 작업이 요청되었습니다. 처리 결과는 서버 로그를 확인해주세요."
            logger.info(message)
            return ResponseEntity.ok(message)
        } catch (e: Exception) {
            logger.error("Error during manual trigger for fetching city {} forecast: {}", cityTempRegId, e.message, e)
            return ResponseEntity.internalServerError().body("예보 가져오기 작업 중 오류 발생: ${e.message}")
        }
    }
}