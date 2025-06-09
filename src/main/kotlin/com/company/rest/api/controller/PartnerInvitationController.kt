package com.company.rest.api.controller

import com.company.rest.api.dto.PartnerInvitationAcceptRequestDto
import com.company.rest.api.dto.PartnerInvitationResponseDto
import com.company.rest.api.dto.PartnerRelationResponseDto
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.service.PartnerInvitationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/partner/invitation")
@Tag(name = "Partner Invitation", description = "파트너 초대 관련 API")
@SecurityRequirement(name = "bearerAuth")
class PartnerInvitationController(
    private val partnerInvitationService: PartnerInvitationService
) {
    private val logger = LoggerFactory.getLogger(PartnerInvitationController::class.java)

    @Operation(
        summary = "파트너 초대 코드 생성",
        description = "인증된 사용자를 위한 새 파트너 초대 코드를 생성합니다. 기존에 유효한 코드가 있다면 해당 코드를 반환합니다.",
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
        responseCode = "201", description = "새로운 초대 코드 생성 성공 또는 기존 유효 코드 반환",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = PartnerInvitationResponseDto::class)
        )]
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 이미 파트너가 있는 경우)")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    @PostMapping
    fun createPartnerInvitation(
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?
    ): ResponseEntity<PartnerInvitationResponseDto> {
        if (userUid == null) {
            logger.warn("Create partner invitation attempt: User UID from @AuthenticationPrincipal is null.")
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }

        val invitationResponse = partnerInvitationService.createInvitation(userUid)
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationResponse)
    }

    @Operation(
        summary = "파트너 초대 수락",
        description = "제공된 초대 ID를 사용하여 파트너 관계를 맺습니다. 인증된 사용자가 초대를 수락하는 주체가 됩니다.",
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
        responseCode = "200", description = "파트너 관계 수락 성공",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = PartnerRelationResponseDto::class)
        )]
    )
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효하지 않은 초대 코드, 이미 파트너가 있는 경우, 자신의 초대 수락 시도)")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자 또는 초대 정보를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "충돌 발생 (예: 초대자가 이미 다른 파트너와 연결된 경우)")
    @PostMapping("/accept")
    fun acceptPartnerInvitation(
        @Parameter(hidden = true) @AuthenticationPrincipal accepterUserUid: String?,
        @Valid @SwaggerRequestBody(description = "수락할 초대 ID") @RequestBody
        requestDto: PartnerInvitationAcceptRequestDto
    ): ResponseEntity<PartnerRelationResponseDto> {
        if (accepterUserUid == null) {
            logger.warn("Accept partner invitation attempt: User UID from @AuthenticationPrincipal is null.")
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }

        val partnerRelationResponse = partnerInvitationService.acceptInvitation(
            accepterUserUid = accepterUserUid,
            invitationId = requestDto.invitationId
        )
        return ResponseEntity.ok(partnerRelationResponse)
    }
}