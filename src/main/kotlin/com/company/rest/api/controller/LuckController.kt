package com.company.rest.api.controller

import com.company.rest.api.dto.ZodiacLuckDataDto
import com.company.rest.api.service.GeminiService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/luck")
@Tag(name = "Luck", description = "띠별 운세 조회 API")
class LuckController(
    private val geminiService: GeminiService
) {
    private val logger = LoggerFactory.getLogger(LuckController::class.java)
    private val KOREA_ZONE_ID = ZoneId.of("Asia/Seoul")

    @Operation(
        summary = "오늘의 모든 띠별 운세 조회",
        description = "오늘 날짜를 기준으로 12개 띠의 모든 운세 정보를 리스트 형태로 반환합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "운세 정보 조회 성공",
        content = [Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = ZodiacLuckDataDto::class))
        )]
    )
    @GetMapping
    fun getTodaysAllLucks(): ResponseEntity<List<ZodiacLuckDataDto>> {
        val today = LocalDate.now(KOREA_ZONE_ID)
        logger.info("Request received for today's ({}) all zodiac lucks.", today)

        val allLucks = geminiService.getAllLucksForDate(today)

        return ResponseEntity.ok(allLucks)
    }
}