package com.company.rest.api.controller

import com.company.rest.api.dto.EventCreateRequestDto
import com.company.rest.api.dto.EventIdRequestDto
import com.company.rest.api.dto.EventResponseDto
import com.company.rest.api.dto.EventUpdateRequestDto
import com.company.rest.api.security.UserId
import com.company.rest.api.service.EventService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "이벤트 관리 API")
@SecurityRequirement(name = "bearerAuth")
class EventController(
    private val eventService: EventService
) {
    private val logger = LoggerFactory.getLogger(EventController::class.java)

    @Operation(
        summary = "새 이벤트 생성",
        description = "인증된 사용자의 새 이벤트를 생성합니다.",
        parameters = [
            Parameter(
                name = "Authorization",
                `in` = ParameterIn.HEADER,
                description = "Bearer {Access Token}",
                required = true,
                schema = Schema(type = "string")
            )
        ]
    )
    @ApiResponse(
        responseCode = "201", description = "이벤트 생성 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = EventResponseDto::class))]
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 형식")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @PostMapping
    fun createEvent(
        @Valid @SwaggerRequestBody(description = "생성할 이벤트 정보") @RequestBody
        eventCreateRequestDto: EventCreateRequestDto,
        @Parameter(hidden = true) @UserId userUid: String
    ): ResponseEntity<EventResponseDto> {
        logger.info("Event creation authorized for user UID: $userUid (from @UserId annotation)")
        val createdEvent = eventService.createEvent(eventCreateRequestDto, userUid)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent)
    }

    @Operation(
        summary = "초기 이벤트 목록 조회 (오늘 기준 전후 1개월)",
        description = "앱의 초기 로딩 시 사용됩니다. 오늘 날짜를 기준으로 지난달 1일부터 다음 달 말일까지의 이벤트 목록을 조회합니다.",
        parameters = [
            Parameter(
                name = "Authorization",
                `in` = ParameterIn.HEADER,
                description = "Bearer {Access Token}",
                required = true,
                schema = Schema(type = "string")
            )
        ]
    )
    @ApiResponse(
        responseCode = "200", description = "이벤트 목록 조회 성공",
        content = [Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = EventResponseDto::class))
        )]
    )
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @GetMapping
    fun getInitialEvents(
        @Parameter(hidden = true) @UserId userUid: String
    ): ResponseEntity<List<EventResponseDto>> {
        val events = eventService.getInitialEvents(userUid)
        return ResponseEntity.ok(events)
    }

    @Operation(
        summary = "월별 이벤트 목록 조회",
        description = "사용자가 캘린더를 스와이프할 때 특정 연도와 월에 해당하는 이벤트 목록을 조회합니다.",
        parameters = [
            Parameter(
                name = "Authorization",
                `in` = ParameterIn.HEADER,
                description = "Bearer {Access Token}",
                required = true,
                schema = Schema(type = "string")
            )
        ]
    )
    @ApiResponse(
        responseCode = "200", description = "월별 이벤트 목록 조회 성공",
        content = [Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = EventResponseDto::class))
        )]
    )
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @GetMapping("/by-month")
    fun getEventsByMonth(
        @Parameter(hidden = true) @UserId userUid: String,
        @Parameter(description = "조회할 연도", required = true, example = "2025") @RequestParam("year") year: Int,
        @Parameter(description = "조회할 월", required = true, example = "6") @RequestParam("month") month: Int
    ): ResponseEntity<List<EventResponseDto>> {
        val events = eventService.getEventsByMonth(userUid, year, month)
        return ResponseEntity.ok(events)
    }

    @Operation(
        summary = "이벤트 수정",
        description = "인증된 사용자의 특정 이벤트를 수정합니다. 요청 본문에 eventId와 변경할 필드들을 포함해야 합니다.",
        parameters = [
            Parameter(
                name = "Authorization",
                `in` = ParameterIn.HEADER,
                description = "Bearer {Access Token}",
                required = true,
                schema = Schema(type = "string")
            )
        ]
    )
    @ApiResponse(
        responseCode = "200", description = "이벤트 수정 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = EventResponseDto::class))]
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 형식 (예: eventId 누락)")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "403", description = "권한 없음 (자신의 이벤트가 아님)")
    @ApiResponse(responseCode = "404", description = "수정할 이벤트를 찾을 수 없음")
    @PutMapping
    fun updateMyEvent(
        @Parameter(hidden = true) @UserId userUid: String,
        @Valid @SwaggerRequestBody(description = "수정할 이벤트 정보 (eventId 포함)") @RequestBody
        eventUpdateRequestDto: EventUpdateRequestDto
    ): ResponseEntity<EventResponseDto> {
        val updatedEvent = eventService.updateEvent(userUid, eventUpdateRequestDto)
        return ResponseEntity.ok(updatedEvent)
    }

    @Operation(
        summary = "이벤트 삭제",
        description = "인증된 사용자의 특정 이벤트를 삭제합니다. 요청 본문에 eventId를 포함해야 합니다.",
        parameters = [
            Parameter(
                name = "Authorization",
                `in` = ParameterIn.HEADER,
                description = "Bearer {Access Token}",
                required = true,
                schema = Schema(type = "string")
            )
        ]
    )
    @ApiResponse(responseCode = "204", description = "이벤트 삭제 성공 (No Content)")
    @ApiResponse(responseCode = "400", description = "잘못된 요청 형식 (예: eventId 누락)")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "403", description = "권한 없음 (자신의 이벤트가 아님)")
    @ApiResponse(responseCode = "404", description = "삭제할 이벤트를 찾을 수 없음")
    @DeleteMapping
    fun deleteMyEvent(
        @Parameter(hidden = true) @UserId userUid: String,
        @Valid @SwaggerRequestBody(description = "삭제할 이벤트의 ID") @RequestBody
        eventIdRequestDto: EventIdRequestDto
    ): ResponseEntity<Void> {
        eventService.deleteEvent(userUid, eventIdRequestDto.eventId)
        return ResponseEntity.noContent().build()
    }
}