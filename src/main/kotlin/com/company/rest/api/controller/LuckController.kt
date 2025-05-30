package com.company.rest.api.controller

import com.company.rest.api.dto.ZodiacLuckDataDto
import com.company.rest.api.service.GeminiService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag // Tag 임포트 확인
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/luck") // 엔드포인트 경로 변경
@Tag(name = "Luck", description = "띠별 운세 조회 및 수동 가져오기 API") // Tag 이름 및 설명 변경
class LuckController(
    private val geminiService: GeminiService
) {
    private val logger = LoggerFactory.getLogger(LuckController::class.java)
    private val KOREA_ZONE_ID = ZoneId.of("Asia/Seoul")

    @Operation(
        summary = "오늘의 특정 띠별 운세 조회",
        description = "지정된 띠에 대한 오늘의 운세 정보를 반환합니다. 데이터는 매일 오전 9시(한국 시간)에 갱신되거나 수동으로 가져올 수 있습니다.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "운세 정보 조회 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ZodiacLuckDataDto::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 띠 이름"),
            ApiResponse(responseCode = "404", description = "해당 띠 또는 해당 날짜의 운세 정보를 찾을 수 없거나 아직 준비되지 않음")
        ]
    )
    @GetMapping("/today/{zodiacName}")
    fun getTodaysLuckForZodiac(
        @Parameter(description = "조회할 띠 이름 (예: 쥐띠, 소띠, 호랑이띠 등)", required = true, example = "쥐띠")
        @PathVariable zodiacName: String
    ): ResponseEntity<ZodiacLuckDataDto> {
        val today = LocalDate.now(KOREA_ZONE_ID)
        logger.info("Request received for today's ({}) Luck for zodiac: {}", today, zodiacName)

        if (zodiacName.isBlank()) {
            logger.warn("Zodiac name is blank.")
            return ResponseEntity.badRequest().build()
        }

        val LuckData = geminiService.getLuckForZodiacSign(today, zodiacName)

        return if (LuckData != null) {
            ResponseEntity.ok(LuckData)
        } else {
            logger.warn("Luck data not found for today ({}) and zodiac: {}", today, zodiacName)
            ResponseEntity.notFound().build()
        }
    }

    @Operation(
        summary = "특정 날짜의 특정 띠별 운세 조회",
        description = "지정된 날짜와 띠에 대한 운세 정보를 반환합니다. 날짜는 YYYY-MM-DD 형식으로 제공해야 합니다.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "운세 정보 조회 성공",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ZodiacLuckDataDto::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 띠 이름 또는 날짜 형식"),
            ApiResponse(responseCode = "404", description = "해당 띠 또는 해당 날짜의 운세 정보를 찾을 수 없거나 아직 준비되지 않음")
        ]
    )
    @GetMapping("/{date}/{zodiacName}")
    fun getLuckForZodiacAndDate(
        @Parameter(description = "조회할 날짜 (YYYY-MM-DD 형식)", required = true, example = "2023-10-27")
        @PathVariable date: String,
        @Parameter(description = "조회할 띠 이름 (예: 쥐띠, 소띠, 호랑이띠 등)", required = true, example = "쥐띠")
        @PathVariable zodiacName: String
    ): ResponseEntity<ZodiacLuckDataDto> {
        val requestDate: LocalDate = try {
            LocalDate.parse(date)
        } catch (e: DateTimeParseException) {
            logger.warn("Invalid date format for date string: {}", date)
            return ResponseEntity.badRequest().build()
        }

        logger.info("Request received for date {} Luck for zodiac: {}", requestDate, zodiacName)

        if (zodiacName.isBlank()) {
            logger.warn("Zodiac name is blank for date: {}", requestDate)
            return ResponseEntity.badRequest().build()
        }

        val LuckData = geminiService.getLuckForZodiacSign(requestDate, zodiacName)

        return if (LuckData != null) {
            ResponseEntity.ok(LuckData)
        } else {
            logger.warn("Luck data not found for date {} and zodiac: {}", requestDate, zodiacName)
            ResponseEntity.notFound().build()
        }
    }

    @Operation(
        summary = "오늘의 운세 정보 수동으로 가져오기 및 저장 (관리자/테스트용)",
        description = "오늘 날짜의 운세 정보를 제미나이로부터 즉시 가져와 데이터베이스에 저장/업데이트합니다. 주로 테스트 또는 긴급 데이터 동기화에 사용됩니다.",
        responses = [
            ApiResponse(responseCode = "200", description = "운세 정보 가져오기 작업이 성공적으로 시작됨. (실제 저장 결과는 비동기적일 수 있으므로 로그 및 DB 확인 필요)"),
            ApiResponse(responseCode = "500", description = "운세 정보 가져오기 작업 시작 중 내부 서버 오류 발생")
        ]
    )
    @PostMapping("/admin/trigger-fetch-today")
    fun manuallyTriggerFetchTodaysLuck(): ResponseEntity<String> {
        val today = LocalDate.now(KOREA_ZONE_ID)
        logger.info("Manual trigger request received to fetch and store Lucks for today: {}", today)
        try {
            geminiService.fetchAndStoreDailyLuck(today)
            val message = "오늘 ($today) 운세 정보 가져오기 및 저장 작업이 시작되었습니다. 처리 결과는 서버 로그와 데이터베이스를 확인해주세요."
            logger.info(message)
            return ResponseEntity.ok(message)
        } catch (e: Exception) {
            logger.error("Error during manual trigger for fetching today's Luck (date: {}): {}", today, e.message, e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("오늘 ($today) 운세 정보 가져오기 작업 시작 중 오류가 발생했습니다: ${e.message}")
        }
    }
}