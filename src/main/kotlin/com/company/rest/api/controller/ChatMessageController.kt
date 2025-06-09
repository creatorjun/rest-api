package com.company.rest.api.controller

import com.company.rest.api.dto.PaginatedChatMessagesResponseDto
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.service.ChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat Messages", description = "채팅 메시지 조회, 삭제 및 검색 API")
@SecurityRequirement(name = "bearerAuth")
class ChatMessageController(
    private val chatService: ChatService
) {
    private val logger = LoggerFactory.getLogger(ChatMessageController::class.java)

    @GetMapping("/with/{otherUserUid}/messages")
    @Operation(summary = "특정 사용자와의 채팅 메시지 조회 (페이징)", description = "이전 메시지를 로드하기 위한 커서 기반 페이징을 지원합니다.")
    fun getChatMessagesWithUser(
        @Parameter(hidden = true) @AuthenticationPrincipal currentUserUid: String?,
        @Parameter(description = "대화 상대방의 사용자 UID", required = true) @PathVariable otherUserUid: String,
        @Parameter(
            description = "이 타임스탬프(epoch milliseconds) 이전의 메시지를 조회합니다. 이전 페이지를 로드할 때 사용됩니다. (선택 사항, 첫 페이지 로드 시에는 생략)",
            required = false
        )
        @RequestParam(name = "before", required = false) beforeTimestamp: Long?,
        @Parameter(description = "한 번에 가져올 메시지 개수. 기본값 20, 최대 100.", required = false)
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PaginatedChatMessagesResponseDto> {
        if (currentUserUid == null) {
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        if (currentUserUid == otherUserUid) {
            logger.warn("User {} attempted to fetch chat messages with themselves.", currentUserUid)
            throw CustomException(ErrorCode.INVALID_INPUT_VALUE)
        }
        if (size <= 0 || size > 100) {
            logger.warn("Invalid message request size: {}", size)
            throw CustomException(ErrorCode.INVALID_INPUT_VALUE)
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
    @Operation(summary = "채팅 메시지 삭제", description = "자신이 보낸 특정 채팅 메시지를 논리적으로 삭제합니다.")
    fun deleteChatMessage(
        @Parameter(hidden = true) @AuthenticationPrincipal currentUserUid: String?,
        @Parameter(description = "삭제할 메시지의 ID", required = true) @PathVariable messageId: String
    ): ResponseEntity<Void> {
        if (currentUserUid == null) {
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        logger.info("User UID: {} attempting to delete message ID: {}", currentUserUid, messageId)
        chatService.deleteMessage(currentUserUid, messageId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/with/{otherUserUid}/messages/search")
    @Operation(
        summary = "특정 사용자와의 채팅 메시지 내용 검색 (페이징)",
        description = "키워드를 포함하는 메시지를 최신순으로 검색하여 반환합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "메시지 검색 성공",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = PaginatedChatMessagesResponseDto::class)
        )]
    )
    fun searchChatMessages(
        @Parameter(hidden = true) @AuthenticationPrincipal currentUserUid: String?,
        @Parameter(description = "대화 상대방의 사용자 UID", required = true) @PathVariable otherUserUid: String,
        @Parameter(description = "검색할 키워드", required = true) @RequestParam keyword: String,
        @PageableDefault(size = 20, sort = ["createdAt,desc"])
        @Parameter(description = "페이징 정보 (예: page=0&size=20).")
        pageable: Pageable
    ): ResponseEntity<PaginatedChatMessagesResponseDto> {
        if (currentUserUid == null) {
            logger.warn("searchChatMessages: User UID from @AuthenticationPrincipal is null.")
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        if (keyword.isBlank()) {
            logger.warn("searchChatMessages: Search keyword is blank for conversation with {}", otherUserUid)
        }
        if (pageable.pageSize <= 0 || pageable.pageSize > 100) {
            throw CustomException(ErrorCode.INVALID_INPUT_VALUE)
        }

        logger.info(
            "User UID: {} searching messages with otherUserUid: {} for keyword '{}'. Pageable: {}",
            currentUserUid, otherUserUid, keyword, pageable
        )

        val searchResults = chatService.searchMessages(
            currentUserUid,
            otherUserUid,
            keyword,
            pageable
        )

        return ResponseEntity.ok(searchResults)
    }
}