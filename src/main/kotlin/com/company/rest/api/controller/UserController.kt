package com.company.rest.api.controller

import com.company.rest.api.dto.*
import com.company.rest.api.security.UserId
import com.company.rest.api.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "사용자 관련 API (FCM 토큰, 앱 비밀번호, 계정 정보, 파트너 관계, 계정 삭제 등)")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @PutMapping("/me/fcm-token")
    @Operation(
        summary = "FCM 토큰 등록/갱신",
        description = "현재 인증된 사용자의 FCM 토큰을 서버에 등록하거나 갱신합니다."
    )
    @ApiResponse(responseCode = "200", description = "FCM 토큰이 성공적으로 업데이트됨")
    fun updateMyFcmToken(
        @Parameter(hidden = true) @UserId userUid: String,
        @Valid @SwaggerRequestBody(description = "등록할 FCM 토큰 정보") @RequestBody
        fcmTokenRequestDto: FcmTokenRequestDto
    ): ResponseEntity<Void> {
        logger.info("User UID: {} attempting to update FCM token.", userUid)
        userService.updateFcmToken(userUid, fcmTokenRequestDto.fcmToken)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/me/verify-app-password")
    @Operation(
        summary = "앱 내 이중 인증 비밀번호 검증",
        description = "현재 인증된 사용자가 제공한 앱 비밀번호가 저장된 비밀번호와 일치하는지 검증합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "비밀번호 검증 성공",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = AppPasswordVerificationResponseDto::class)
        )]
    )
    fun verifyMyAppPassword(
        @Parameter(hidden = true) @UserId userUid: String,
        @Valid @SwaggerRequestBody @RequestBody requestDto: AppPasswordVerificationRequestDto
    ): ResponseEntity<AppPasswordVerificationResponseDto> {
        userService.verifyAppPassword(userUid, requestDto.appPassword)
        return ResponseEntity.ok(AppPasswordVerificationResponseDto(isVerified = true, message = "앱 비밀번호가 확인되었습니다."))
    }

    @PatchMapping("/me")
    @Operation(
        summary = "내 계정 정보 수정 (닉네임 및/또는 앱 비밀번호)",
        description = """
            현재 인증된 사용자의 닉네임 및/또는 앱 내 이중 인증 비밀번호를 수정/설정합니다.
            앱 비밀번호는 최소 4자 이상이어야 합니다.
            앱 비밀번호를 처음 설정하거나 변경할 때 `newAppPassword`를 제공해야 합니다.
            기존에 설정된 앱 비밀번호를 변경하려면 `currentAppPassword`도 함께 제공하여 현재 비밀번호를 검증해야 합니다.
            `newAppPassword` 필드를 비워두거나 `null`로 보내면 앱 비밀번호는 변경되지 않습니다. (비밀번호 해제는 별도 DELETE API 사용)
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "계정 정보 업데이트 성공",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = UserAccountUpdateResponseDto::class)
        )]
    )
    fun updateMyAccount(
        @Parameter(hidden = true) @UserId userUid: String,
        @Valid @SwaggerRequestBody(description = "수정할 계정 정보 (닉네임, 현재/새 앱 비밀번호)") @RequestBody
        requestDto: UserAccountUpdateRequestDto
    ): ResponseEntity<UserAccountUpdateResponseDto> {
        val updatedUser = userService.updateUserAccount(userUid, requestDto)
        return ResponseEntity.ok(UserAccountUpdateResponseDto.fromUser(updatedUser))
    }

    @DeleteMapping("/me/app-password")
    @Operation(
        summary = "앱 내 이중 인증 비밀번호 해제(삭제)",
        description = "현재 인증된 사용자의 앱 비밀번호를 해제(삭제)합니다. 해제를 위해 현재 설정된 앱 비밀번호를 제공해야 합니다."
    )
    @ApiResponse(responseCode = "204", description = "앱 비밀번호가 성공적으로 해제됨")
    fun removeMyAppPassword(
        @Parameter(hidden = true) @UserId userUid: String,
        @Valid @SwaggerRequestBody(description = "앱 비밀번호 해제를 위한 현재 비밀번호") @RequestBody
        requestDto: AppPasswordRemoveRequestDto
    ): ResponseEntity<Void> {
        userService.removeAppPassword(userUid, requestDto.currentAppPassword)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me/partner")
    @Operation(
        summary = "파트너 관계 해제 및 해당 파트너와의 모든 대화 내역 삭제",
        description = "현재 인증된 사용자의 파트너 관계를 해제하고, 해당 파트너와의 모든 채팅 메시지 기록을 삭제합니다."
    )
    @ApiResponse(responseCode = "204", description = "파트너 관계 및 대화 내역이 성공적으로 삭제됨")
    fun clearMyPartnerAndChatHistory(
        @Parameter(hidden = true) @UserId userUid: String
    ): ResponseEntity<Void> {
        userService.clearPartnerAndChatHistory(userUid)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me/withdrawal")
    @Operation(
        summary = "회원가입 철회",
        description = "최초 소셜 로그인 시 개인정보 동의를 거부한 사용자의 계정을 즉시 삭제합니다. 최초 로그인 시 발급된 토큰으로 인증해야 합니다."
    )
    @ApiResponse(responseCode = "204", description = "계정이 성공적으로 삭제됨")
    fun withdrawUserAccount(
        @Parameter(hidden = true) @UserId userUid: String
    ): ResponseEntity<Void> {
        logger.info("User UID: {} attempting to withdraw their account post-signup.", userUid)
        userService.deleteUserAccount(userUid)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me")
    @Operation(
        summary = "회원 탈퇴 (계정 영구 삭제)",
        description = "현재 인증된 사용자의 계정과 관련된 모든 정보(일정, 앱 비밀번호, 파트너 정보, 채팅 내역, 초대장, 토큰 등)를 영구적으로 삭제합니다. 이 작업은 되돌릴 수 없습니다."
    )
    @ApiResponse(responseCode = "204", description = "계정이 성공적으로 삭제됨")
    fun deleteMyAccount(
        @Parameter(hidden = true) @UserId userUid: String
    ): ResponseEntity<Void> {
        logger.info("User UID: {} attempting to permanently delete their account.", userUid)
        userService.deleteUserAccount(userUid)
        return ResponseEntity.noContent().build()
    }
}