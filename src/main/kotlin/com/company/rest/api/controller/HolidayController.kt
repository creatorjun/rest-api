package com.company.rest.api.controller

import com.company.rest.api.dto.HolidayDto
import com.company.rest.api.service.HolidayService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/holidays")
@Tag(name = "Holidays", description = "공휴일 정보 조회 API")
class HolidayController(
    private val holidayService: HolidayService
) {

    @GetMapping("/{year}")
    @Operation(
        summary = "연도별 공휴일 정보 조회",
        description = "특정 연도의 공휴일 목록을 조회합니다. HTTP ETag를 이용한 캐싱을 지원합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "공휴일 정보 조회 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = HolidayDto::class))]
    )
    @ApiResponse(responseCode = "304", description = "데이터 변경 없음 (클라이언트 캐시 유효)")
    fun getHolidays(
        @Parameter(description = "조회할 연도", required = true, example = "2025")
        @PathVariable year: Int,
        @Parameter(
            description = "클라이언트가 가진 데이터의 ETag 값",
            `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER,
            name = "If-None-Match"
        )
        @RequestHeader(name = "If-None-Match", required = false) eTag: String?
    ): ResponseEntity<List<HolidayDto>> {

        val serverEtag = holidayService.getEtagForYear(year)

        if (eTag == serverEtag) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build()
        }

        val holidays = holidayService.getHolidaysForYear(year)
        val holidayDtos = holidays.map { HolidayDto.fromEntity(it) }

        return ResponseEntity.ok()
            .eTag(serverEtag)
            .body(holidayDtos)
    }
}