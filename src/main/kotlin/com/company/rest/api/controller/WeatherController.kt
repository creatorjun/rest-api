package com.company.rest.api.controller

import com.company.rest.api.dto.WeatherForecastResponseDto // WeatherForecastResponseDto는 DailyWeatherForecast를 변환하는데 사용됨
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
        summary = "특정 도시 이름으로 주간 일기 예보 조회", // summary 변경
        description = """
            지정된 도시 이름(예: "서울", "부산")에 대해 특정 시작일로부터 일주일간의 일기 예보를 반환합니다.
            도시 이름은 '/api/v1/weather/regions' 엔드포인트에서 제공하는 이름을 사용해야 합니다.
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
    @GetMapping("/weekly/by-city-name/{cityName}") // 경로 변경
    fun getWeeklyForecastByCityName( // 메소드명 및 파라미터 변경
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

    @Operation(
        summary = "조회 가능한 대표 도시(지역) 목록 조회",
        description = "일기 예보를 조회할 수 있는 대표 도시(지역)의 코드와 이름 목록을 반환합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "지역 목록 조회 성공",
        content = [Content(mediaType = "application/json", schema = Schema(type = "array", implementation = Map::class))]
        // 스키마를 좀 더 구체적으로 표현하고 싶다면, RegionDetail과 유사한 DTO를 정의하고 그것을 implementation으로 사용할 수 있습니다.
        // 예: schema = Schema(type = "array", implementation = RegionInfoResponseDto::class)
    )
    @GetMapping("/regions")
    fun getAvailableRegions(): ResponseEntity<List<Map<String, Any>>> { // 반환 타입의 Map Value를 Any로 변경 (nx, ny 포함 가능성)
        val regions = weatherService.getRegionCodeMap().map { entry ->
            mapOf(
                "regionCode" to entry.key, // 내부 관리용 코드
                "cityName" to entry.value.cityName,
                "nx" to entry.value.nx, // 클라이언트에게 참고용으로 제공 가능
                "ny" to entry.value.ny  // 클라이언트에게 참고용으로 제공 가능
            )
        }
        return ResponseEntity.ok(regions)
    }


    @Operation(
        summary = "수동으로 특정 도시 예보 가져오기 (관리자/테스트용)",
        description = "특정 도시의 오늘자 06시 발표 기준 주간 예보를 즉시 가져와 DB에 저장/업데이트합니다. 날짜를 지정하면 해당 날짜 06시 기준. 도시 식별자로 '기온 예보 구역 코드'를 사용합니다.",
        responses = [
            ApiResponse(responseCode = "200", description = "예보 가져오기 작업이 성공적으로 요청됨."),
            ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효하지 않은 지역 코드 또는 날짜 형식)"),
            ApiResponse(responseCode = "500", description = "예보 가져오기 작업 중 내부 서버 오류 발생")
        ]
    )
    @PostMapping("/admin/trigger-fetch-city")
    fun manuallyTriggerFetchForCity(
        @Parameter(description = "예보를 가져올 도시의 기온 예보 구역 코드 (RegionCodeConfig의 키 값)", required = true, example = "11B10101")
        @RequestParam cityTempRegId: String, // 이 엔드포인트는 내부 관리/테스트용으로 기존 regionCode(cityTempRegId)를 유지
        @Parameter(description = "기준 발표 날짜 (YYYY-MM-DD 형식). 제공하지 않으면 오늘 날짜의 06시 발표 기준.", required = false, example = "2025-06-03")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        targetDate: LocalDate?
    ): ResponseEntity<String> {
        // cityTempRegId (regionCode)의 유효성 검사는 WeatherService의 regionCodeMap을 통해 이루어짐
        val regionDetails = weatherService.getRegionCodeMap()[cityTempRegId]
        if (regionDetails == null) {
            return ResponseEntity.badRequest().body("지원하지 않거나 유효하지 않은 도시 지역 코드입니다: $cityTempRegId")
        }

        val kmaBaseDate = targetDate ?: LocalDate.now(KOREA_ZONE_ID)
        val baseDateTimeForKMA = kmaBaseDate.atTime(LocalTime.of(6,0)) // 중기예보 기준 시간

        // 단기 예보와 중기 예보 모두 처리하도록 WeatherService의 메서드가 수정될 것을 예상
        // 여기서는 우선 cityTempRegId(regionCode)와 cityName, nx, ny 등을 모두 가진 regionDetails를 전달하거나,
        // 서비스 내부에서 cityTempRegId를 기반으로 모든 작업을 처리하도록 할 수 있습니다.
        // 현재 fetchAndStoreWeeklyForecastsForCity는 cityTempRegId를 받으므로, 이를 활용.
        // 만약 단기예보도 이 트리거로 함께 갱신하려면 서비스 로직 수정 필요.
        try {
            logger.info("Manual trigger request for city (using regionCode): {}, baseDateTime for mid-term: {}", cityTempRegId, baseDateTimeForKMA)
            // WeatherService의 fetchAndStoreWeeklyForecastsForCity는 현재 중기예보(getMidLandFcst, getMidTa)만 처리.
            // 이 메서드가 D+0~3 단기예보도 함께 가져오도록 확장되거나, 별도의 단기예보 트리거 메서드가 필요할 수 있음.
            // 현재는 기존 중기예보 로직만 트리거하는 것으로 가정.
            weatherService.fetchAndStoreWeeklyForecastsForCity(cityTempRegId, baseDateTimeForKMA)

            // 만약 단기예보(getVilageFcst)도 이 트리거로 가져오고 싶다면,
            // WeatherService에 새로운 메서드(예: fetchAndStoreShortTermForecastForRegion)를 만들고 여기서 호출하거나,
            // fetchAndStoreWeeklyForecastsForCity 내부 로직을 확장해야 합니다.
            // 여기서는 우선 메시지를 좀 더 포괄적으로 수정.
            val message = "도시(${regionDetails.cityName}, 코드: $cityTempRegId)에 대한 예보($baseDateTimeForKMA 발표 기준) 가져오기 작업이 요청되었습니다. 처리 결과는 서버 로그를 확인해주세요. (현재 중기예보 위주, 단기예보 연동 필요시 서비스 로직 확인)"
            logger.info(message)
            return ResponseEntity.ok(message)
        } catch (e: Exception) {
            logger.error("Error during manual trigger for fetching city {} forecast: {}", cityTempRegId, e.message, e)
            return ResponseEntity.internalServerError().body("예보 가져오기 작업 중 오류 발생: ${e.message}")
        }
    }
}