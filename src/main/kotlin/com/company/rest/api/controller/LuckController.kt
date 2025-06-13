package com.company.rest.api.controller

import com.company.rest.api.dto.ZodiacLuckDataDto
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.service.GeminiService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
        summary = "오늘의 특정 띠별 운세 조회",
        description = "URL 경로에 포함된 띠 이름을 기준으로, 오늘 날짜의 특정 띠 운세 정보를 반환합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "운세 정보 조회 성공",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = ZodiacLuckDataDto::class)
        )]
    )
    @ApiResponse(responseCode = "404", description = "해당 띠의 운세 정보를 찾을 수 없음")
    @GetMapping("/{zodiacName}")
    fun getLuckForZodiac(
        @Parameter(description = "조회할 띠의 이름 (예: '쥐띠')", required = true)
        @PathVariable zodiacName: String
    ): ResponseEntity<ZodiacLuckDataDto> {
        val today = LocalDate.now(KOREA_ZONE_ID)
        logger.info("Request received for today's ({}) luck for zodiac: {}", today, zodiacName)

        val specificLuck = geminiService.getLuckForZodiacSign(today, zodiacName)
            ?: throw CustomException(ErrorCode.LUCK_DATA_NOT_FOUND)

        return ResponseEntity.ok(specificLuck)
    }
}