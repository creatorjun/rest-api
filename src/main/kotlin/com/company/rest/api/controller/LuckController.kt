package com.company.rest.api.controller

// import org.springframework.web.bind.annotation.RequestParam // getLuckForZodiacAndDate에서만 사용하던 것이므로 삭제 가능
import com.company.rest.api.dto.ZodiacLuckDataDto
import com.company.rest.api.service.GeminiService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.ZoneId

// import java.time.format.DateTimeParseException // getLuckForZodiacAndDate에서만 사용하던 것이므로 삭제

@RestController
@RequestMapping("/api/v1/luck")
@Tag(name = "Luck", description = "띠별 운세 조회 및 수동 가져오기 API")
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
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ZodiacLuckDataDto::class)
                )]
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

        val luckData = geminiService.getLuckForZodiacSign(today, zodiacName)

        return if (luckData != null) {
            ResponseEntity.ok(luckData)
        } else {
            logger.warn("Luck data not found for today ({}) and zodiac: {}", today, zodiacName)
            ResponseEntity.notFound().build()
        }
    }

    // `/api/v1/luck/{date}/{zodiacName}` 엔드포인트 및 getLuckForZodiacAndDate() 메서드 삭제됨

    @Operation(
        summary = "오늘의 운세 정보 수동으로 가져오기 및 저장 (관리자/테스트용)",
        description = "오늘 날짜의 운세 정보를 제미나이로부터 즉시 가져와 데이터베이스에 저장/업데이트합니다. 주로 테스트 또는 긴급 데이터 동기화에 사용됩니다.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "운세 정보 가져오기 작업이 성공적으로 시작됨. (실제 저장 결과는 비동기적일 수 있으므로 로그 및 DB 확인 필요)"
            ),
            ApiResponse(responseCode = "500", description = "운세 정보 가져오기 작업 시작 중 내부 서버 오류 발생")
        ]
    )
    @PostMapping("/admin/fetch")
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