package com.company.rest.api.controller

import com.company.rest.api.dto.EventCreateRequestDto // DTO 임포트 변경
import com.company.rest.api.dto.EventIdRequestDto // DTO 임포트 변경
import com.company.rest.api.dto.EventResponseDto // DTO 임포트 변경
import com.company.rest.api.dto.EventUpdateRequestDto // DTO 임포트 변경
import com.company.rest.api.service.EventService // 서비스 임포트 변경
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/events") // 엔드포인트 변경: /memos -> /events
@Tag(name = "Events", description = "이벤트 관리 API") // 태그 이름 및 설명 변경: Memos -> Events, 메모 -> 이벤트
@SecurityRequirement(name = "bearerAuth")
class EventController( // 클래스 이름 변경: MemoController -> EventController
    private val eventService: EventService // 서비스 타입 및 이름 변경
) {
    private val logger = LoggerFactory.getLogger(EventController::class.java)

    @Operation(
        summary = "새 이벤트 생성", // Summary 변경: 메모 -> 이벤트
        description = "인증된 사용자의 새 이벤트를 생성합니다.", // Description 변경: 메모 -> 이벤트
        parameters = [
            Parameter(name = "Authorization", `in` = ParameterIn.HEADER, description = "Bearer {Access Token}", required = true, schema = Schema(type = "string"))
        ]
    )
    @ApiResponse(
        responseCode = "201", description = "이벤트 생성 성공", // Description 변경: 메모 -> 이벤트
        content = [Content(mediaType = "application/json", schema = Schema(implementation = EventResponseDto::class))] // Schema 변경
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 형식")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @PostMapping
    fun createEvent( // 메소드 이름 변경: createMemo -> createEvent
        @Valid @SwaggerRequestBody(description = "생성할 이벤트 정보") @RequestBody // Description 변경: 메모 -> 이벤트
        eventCreateRequestDto: EventCreateRequestDto, // DTO 타입 및 이름 변경
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?
    ): ResponseEntity<EventResponseDto> { // 반환 DTO 타입 변경
        if (userUid == null) {
            logger.warn("Event creation attempt: User UID from @AuthenticationPrincipal is null.") // 로그 메시지 변경
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 인증 정보를 찾을 수 없습니다.")
        }
        logger.info("Event creation authorized for user UID: $userUid (from @AuthenticationPrincipal)") // 로그 메시지 변경
        val createdEvent = eventService.createEvent(eventCreateRequestDto, userUid) // 서비스 호출 및 변수 이름 변경
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent)
    }

    @Operation(
        summary = "내 이벤트 목록 조회", // Summary 변경: 메모 -> 이벤트
        description = "인증된 사용자의 모든 이벤트 목록을 조회합니다. 생성일자 기준 내림차순으로 정렬됩니다.", // Description 변경: 메모 -> 이벤트
        parameters = [
            Parameter(name = "Authorization", `in` = ParameterIn.HEADER, description = "Bearer {Access Token}", required = true, schema = Schema(type = "string"))
        ]
    )
    @ApiResponse(
        responseCode = "200", description = "이벤트 목록 조회 성공", // Description 변경: 메모 -> 이벤트
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = EventResponseDto::class)))] // Schema 변경
    )
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @GetMapping
    fun getMyEvents( // 메소드 이름 변경: getMyMemos -> getMyEvents
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?
    ): ResponseEntity<List<EventResponseDto>> { // 반환 DTO 타입 변경
        if (userUid == null) {
            logger.warn("Get my events attempt: User UID from @AuthenticationPrincipal is null.") // 로그 메시지 변경
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 인증 정보를 찾을 수 없습니다.")
        }
        val events = eventService.getEventsForUser(userUid) // 서비스 호출 및 변수 이름 변경
        return ResponseEntity.ok(events)
    }

    @Operation(
        summary = "이벤트 수정", // Summary 변경: 메모 -> 이벤트
        description = "인증된 사용자의 특정 이벤트를 수정합니다. 요청 본문에 eventId와 변경할 필드들을 포함해야 합니다.", // Description 변경: 메모 -> 이벤트, memoId -> eventId
        parameters = [
            Parameter(name = "Authorization", `in` = ParameterIn.HEADER, description = "Bearer {Access Token}", required = true, schema = Schema(type = "string"))
        ]
    )
    @ApiResponse(
        responseCode = "200", description = "이벤트 수정 성공", // Description 변경: 메모 -> 이벤트
        content = [Content(mediaType = "application/json", schema = Schema(implementation = EventResponseDto::class))] // Schema 변경
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 형식 (예: eventId 누락)") // Description 변경: memoId -> eventId
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "403", description = "권한 없음 (자신의 이벤트가 아님)") // Description 변경: 메모 -> 이벤트
    @ApiResponse(responseCode = "404", description = "수정할 이벤트를 찾을 수 없음") // Description 변경: 메모 -> 이벤트
    @PutMapping
    fun updateMyEvent( // 메소드 이름 변경: updateMyMemo -> updateMyEvent
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?,
        @Valid @SwaggerRequestBody(description = "수정할 이벤트 정보 (eventId 포함)") @RequestBody // Description 변경: 메모 -> 이벤트, memoId -> eventId
        eventUpdateRequestDto: EventUpdateRequestDto // DTO 타입 및 이름 변경
    ): ResponseEntity<EventResponseDto> { // 반환 DTO 타입 변경
        if (userUid == null) {
            logger.warn("Update event attempt: User UID from @AuthenticationPrincipal is null.") // 로그 메시지 변경
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 인증 정보를 찾을 수 없습니다.")
        }
        val updatedEvent = eventService.updateEvent(userUid, eventUpdateRequestDto) // 서비스 호출 및 변수 이름 변경
        return ResponseEntity.ok(updatedEvent)
    }

    @Operation(
        summary = "이벤트 삭제", // Summary 변경: 메모 -> 이벤트
        description = "인증된 사용자의 특정 이벤트를 삭제합니다. 요청 본문에 eventId를 포함해야 합니다.", // Description 변경: 메모 -> 이벤트, memoId -> eventId
        parameters = [
            Parameter(name = "Authorization", `in` = ParameterIn.HEADER, description = "Bearer {Access Token}", required = true, schema = Schema(type = "string"))
        ]
    )
    @ApiResponse(responseCode = "204", description = "이벤트 삭제 성공 (No Content)") // Description 변경: 메모 -> 이벤트
    @ApiResponse(responseCode = "400", description = "잘못된 요청 형식 (예: eventId 누락)") // Description 변경: memoId -> eventId
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "403", description = "권한 없음 (자신의 이벤트가 아님)") // Description 변경: 메모 -> 이벤트
    @ApiResponse(responseCode = "404", description = "삭제할 이벤트를 찾을 수 없음") // Description 변경: 메모 -> 이벤트
    @DeleteMapping
    fun deleteMyEvent( // 메소드 이름 변경: deleteMyMemo -> deleteMyEvent
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?,
        @Valid @SwaggerRequestBody(description = "삭제할 이벤트의 ID") @RequestBody // Description 변경: 메모 -> 이벤트
        eventIdRequestDto: EventIdRequestDto // DTO 타입 및 이름 변경
    ): ResponseEntity<Void> {
        if (userUid == null) {
            logger.warn("Delete event attempt: User UID from @AuthenticationPrincipal is null.") // 로그 메시지 변경
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 인증 정보를 찾을 수 없습니다.")
        }
        eventService.deleteEvent(userUid, eventIdRequestDto.eventId) // 서비스 호출 변경 (두 번째 파라미터는 eventId)
        return ResponseEntity.noContent().build()
    }
}