package com.company.rest.api.controller

import com.company.rest.api.dto.*
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (예: FCM 토큰 누락)")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    fun updateMyFcmToken(
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?,
        @Valid @SwaggerRequestBody(description = "등록할 FCM 토큰 정보") @RequestBody
        fcmTokenRequestDto: FcmTokenRequestDto
    ): ResponseEntity<Void> {
        if (userUid == null) {
            logger.warn("FCM token update attempt: User UID from @AuthenticationPrincipal is null.")
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
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
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 비밀번호 누락, 혹은 비밀번호 미설정 상태)")
    @ApiResponse(responseCode = "401", description = "비밀번호 불일치 또는 인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    fun verifyMyAppPassword(
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?,
        @Valid @SwaggerRequestBody @RequestBody requestDto: AppPasswordVerificationRequestDto
    ): ResponseEntity<AppPasswordVerificationResponseDto> {
        if (userUid == null) {
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        // 이제 service에서 성공 시 true를, 실패 시 예외를 던지므로, 반환값을 확인할 필요가 없음
        userService.verifyAppPassword(userUid, requestDto.appPassword)
        // 예외가 발생하지 않았다면 성공한 것임
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
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 유효성 검사 실패 - 비밀번호 4자 미만 등)")
    @ApiResponse(responseCode = "401", description = "인증 실패 (예: 현재 앱 비밀번호 불일치)")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    fun updateMyAccount(
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?,
        @Valid @SwaggerRequestBody(description = "수정할 계정 정보 (닉네임, 현재/새 앱 비밀번호)") @RequestBody
        requestDto: UserAccountUpdateRequestDto
    ): ResponseEntity<UserAccountUpdateResponseDto> {
        if (userUid == null) {
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        val updatedUser = userService.updateUserAccount(userUid, requestDto)
        return ResponseEntity.ok(UserAccountUpdateResponseDto.fromUser(updatedUser))
    }

    @DeleteMapping("/me/app-password")
    @Operation(
        summary = "앱 내 이중 인증 비밀번호 해제(삭제)",
        description = "현재 인증된 사용자의 앱 비밀번호를 해제(삭제)합니다. 해제를 위해 현재 설정된 앱 비밀번호를 제공해야 합니다."
    )
    @ApiResponse(responseCode = "204", description = "앱 비밀번호가 성공적으로 해제됨")
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (예: 현재 비밀번호 누락, 혹은 비밀번호 미설정 상태)")
    @ApiResponse(responseCode = "401", description = "현재 앱 비밀번호 불일치 또는 인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    fun removeMyAppPassword(
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?,
        @Valid @SwaggerRequestBody(description = "앱 비밀번호 해제를 위한 현재 비밀번호") @RequestBody
        requestDto: AppPasswordRemoveRequestDto
    ): ResponseEntity<Void> {
        if (userUid == null) {
            logger.warn("App password removal attempt: User UID from @AuthenticationPrincipal is null.")
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        userService.removeAppPassword(userUid, requestDto.currentAppPassword)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me/partner")
    @Operation(
        summary = "파트너 관계 해제 및 해당 파트너와의 모든 대화 내역 삭제",
        description = "현재 인증된 사용자의 파트너 관계를 해제하고, 해당 파트너와의 모든 채팅 메시지 기록을 삭제합니다."
    )
    @ApiResponse(responseCode = "204", description = "파트너 관계 및 대화 내역이 성공적으로 삭제됨")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    fun clearMyPartnerAndChatHistory(
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?
    ): ResponseEntity<Void> {
        if (userUid == null) {
            logger.warn("Clear partner and chat history attempt: User UID from @AuthenticationPrincipal is null.")
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        userService.clearPartnerAndChatHistory(userUid)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me")
    @Operation(
        summary = "내 계정 전체 삭제 (회원 탈퇴)",
        description = "현재 인증된 사용자의 계정과 관련된 모든 정보(일정, 앱 비밀번호, 파트너 정보, 채팅 내역, 초대장, 토큰 등)를 영구적으로 삭제합니다. 이 작업은 되돌릴 수 없습니다."
    )
    @ApiResponse(responseCode = "204", description = "계정이 성공적으로 삭제됨 (No Content)")
    @ApiResponse(responseCode = "401", description = "인증 실패")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    fun deleteMyAccount(
        @Parameter(hidden = true) @AuthenticationPrincipal userUid: String?
    ): ResponseEntity<Void> {
        if (userUid == null) {
            logger.warn("Account deletion attempt: User UID from @AuthenticationPrincipal is null.")
            throw CustomException(ErrorCode.TOKEN_NOT_FOUND)
        }
        logger.info("User UID: {} attempting to delete their account.", userUid)
        userService.deleteUserAccount(userUid)
        return ResponseEntity.noContent().build()
    }
}