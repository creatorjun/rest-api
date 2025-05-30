package com.company.rest.api.controller

import com.company.rest.api.dto.PaginatedChatMessagesResponseDto //
import com.company.rest.api.service.ChatService //
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable // Pageable 직접 주입을 위해 임포트
import org.springframework.data.web.PageableDefault // Pageable 기본값 설정을 위해 임포트
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat Messages", description = "채팅 메시지 조회, 삭제 및 검색 API") // Tag 설명 업데이트
@SecurityRequirement(name = "bearerAuth")
class ChatMessageController(
    private val chatService: ChatService
) {
    private val logger = LoggerFactory.getLogger(ChatMessageController::class.java)

    @GetMapping("/with/{otherUserUid}/messages")
    @Operation( /* ... 기존 OpenAPI 어노테이션 ... */ )
    fun getChatMessagesWithUser(
        @Parameter(hidden = true) @AuthenticationPrincipal currentUserUid: String?,
        @Parameter(description = "대화 상대방의 사용자 UID", required = true) @PathVariable otherUserUid: String,
        @Parameter(description = "이 타임스탬프(epoch milliseconds) 이전의 메시지를 조회합니다. 이전 페이지를 로드할 때 사용됩니다. (선택 사항, 첫 페이지 로드 시에는 생략)", required = false)
        @RequestParam(name = "before", required = false) beforeTimestamp: Long?,
        @Parameter(description = "한 번에 가져올 메시지 개수. 기본값 20, 최대 100.", required = false)
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PaginatedChatMessagesResponseDto> { //
        if (currentUserUid == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 인증 정보를 찾을 수 없습니다.")
        }
        if (currentUserUid == otherUserUid) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신과의 채팅 내역은 조회할 수 없습니다.")
        }
        if (size <= 0 || size > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지 요청 개수는 1에서 100 사이여야 합니다.")
        }
        logger.info(
            "User UID: {} requesting messages with otherUserUid: {}, beforeTimestamp: {}, size: {}",
            currentUserUid, otherUserUid, beforeTimestamp, size
        )
        val paginatedMessages = chatService.getPaginatedMessages(
            currentUserUid,
            otherUserUid,
            beforeTimestamp,
            size
        )
        return ResponseEntity.ok(paginatedMessages)
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation( /* ... 기존 OpenAPI 어노테이션 ... */ )
    fun deleteChatMessage(
        @Parameter(hidden = true) @AuthenticationPrincipal currentUserUid: String?,
        @Parameter(description = "삭제할 메시지의 ID", required = true) @PathVariable messageId: String
    ): ResponseEntity<Void> { //
        if (currentUserUid == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 인증 정보를 찾을 수 없습니다.")
        }
        logger.info("User UID: {} attempting to delete message ID: {}", currentUserUid, messageId)
        chatService.deleteMessage(currentUserUid, messageId)
        return ResponseEntity.noContent().build()
    }

    // --- 여기부터 새로운 검색 엔드포인트 추가 ---
    @GetMapping("/with/{otherUserUid}/messages/search")
    @Operation(
        summary = "특정 사용자와의 채팅 메시지 내용 검색 (페이징)",
        description = """
            현재 인증된 사용자와 지정된 다른 사용자({otherUserUid}) 간의 채팅 메시지 내용에서
            특정 키워드를 포함하는 메시지를 시간 역순(최신순)으로 검색하여 반환합니다.
            페이징을 지원하며, 'page'와 'size' 파라미터를 사용합니다.
        """,
        parameters = [
            Parameter(name = "Authorization", `in` = ParameterIn.HEADER, description = "Bearer {Access Token}", required = true, schema = Schema(type = "string"))
        ]
    )
    @ApiResponse(
        responseCode = "200", description = "메시지 검색 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = PaginatedChatMessagesResponseDto::class))]
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터 (예: keyword 누락)")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자 또는 대화 상대를 찾을 수 없음")
    fun searchChatMessages(
        @Parameter(hidden = true) @AuthenticationPrincipal currentUserUid: String?,
        @Parameter(description = "대화 상대방의 사용자 UID", required = true) @PathVariable otherUserUid: String,
        @Parameter(description = "검색할 키워드", required = true) @RequestParam keyword: String,
        @PageableDefault(size = 20, sort = ["createdAt,desc"]) // page, size, sort 파라미터를 받아 Pageable 객체 자동 생성
        @Parameter(description = "페이징 정보 (예: page=0&size=20&sort=createdAt,desc). sort는 현재 고정값으로 동작.")
        pageable: Pageable // Spring MVC가 page, size, sort 파라미터를 보고 Pageable 객체를 만들어줌
    ): ResponseEntity<PaginatedChatMessagesResponseDto> {
        if (currentUserUid == null) {
            logger.warn("searchChatMessages: User UID from @AuthenticationPrincipal is null.")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 인증 정보를 찾을 수 없습니다.")
        }
        if (keyword.isBlank()) {
            logger.warn("searchChatMessages: Search keyword is blank for conversation with {}", otherUserUid)
            // 빈 키워드에 대해서는 빈 결과를 반환하거나 에러 처리 가능. 여기서는 서비스에서 빈 결과를 주도록 함.
        }
        // pageable.size에 대한 추가적인 유효성 검사 (예: 최대 100개)를 여기서도 할 수 있음
        if (pageable.pageSize <= 0 || pageable.pageSize > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 개수는 1에서 100 사이여야 합니다.")
        }


        logger.info(
            "User UID: {} searching messages with otherUserUid: {} for keyword '{}'. Pageable: {}",
            currentUserUid, otherUserUid, keyword, pageable
        )

        val searchResults = chatService.searchMessages(
            currentUserUid,
            otherUserUid,
            keyword,
            pageable // 서비스로 Pageable 객체 직접 전달
        ) //

        return ResponseEntity.ok(searchResults)
    }
    // --- 새로운 검색 엔드포인트 추가 끝 ---
}