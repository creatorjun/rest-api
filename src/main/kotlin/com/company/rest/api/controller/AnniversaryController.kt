package com.company.rest.api.controller

import com.company.rest.api.dto.AnniversaryCreateRequestDto
import com.company.rest.api.dto.AnniversaryResponseDto
import com.company.rest.api.dto.AnniversaryUpdateRequestDto
import com.company.rest.api.security.UserId
import com.company.rest.api.service.AnniversaryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/anniversaries")
@Tag(name = "Anniversaries", description = "기념일 관리 API")
@SecurityRequirement(name = "bearerAuth")
class AnniversaryController(
    private val anniversaryService: AnniversaryService
) {

    @Operation(summary = "새 기념일 생성", description = "인증된 사용자의 새 기념일을 생성합니다.")
    @ApiResponse(
        responseCode = "201", description = "기념일 생성 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AnniversaryResponseDto::class))]
    )
    @PostMapping
    fun createAnniversary(
        @Parameter(hidden = true) @UserId userUid: String,
        @Valid @SwaggerRequestBody(description = "생성할 기념일 정보") @RequestBody requestDto: AnniversaryCreateRequestDto
    ): ResponseEntity<AnniversaryResponseDto> {
        val anniversaryResponse = anniversaryService.createAnniversary(userUid, requestDto)
        return ResponseEntity.status(HttpStatus.CREATED).body(anniversaryResponse)
    }

    @Operation(summary = "나와 파트너의 기념일 목록 조회", description = "인증된 사용자와 파트너가 생성한 모든 기념일을 날짜순으로 조회합니다.")
    @ApiResponse(
        responseCode = "200", description = "기념일 목록 조회 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AnniversaryResponseDto::class))]
    )
    @GetMapping
    fun getAnniversaries(
        @Parameter(hidden = true) @UserId userUid: String
    ): ResponseEntity<List<AnniversaryResponseDto>> {
        val anniversaries = anniversaryService.getAnniversariesForUser(userUid)
        return ResponseEntity.ok(anniversaries)
    }

    @Operation(summary = "기념일 수정", description = "특정 기념일의 이름 또는 날짜를 수정합니다. 본인 또는 파트너가 생성한 기념일만 수정할 수 있습니다.")
    @ApiResponse(
        responseCode = "200", description = "기념일 수정 성공",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AnniversaryResponseDto::class))]
    )
    @PutMapping("/{anniversaryId}")
    fun updateAnniversary(
        @Parameter(hidden = true) @UserId userUid: String,
        @Parameter(description = "수정할 기념일의 ID", required = true) @PathVariable anniversaryId: String,
        @Valid @SwaggerRequestBody(description = "수정할 기념일 정보") @RequestBody requestDto: AnniversaryUpdateRequestDto
    ): ResponseEntity<AnniversaryResponseDto> {
        val updatedAnniversary = anniversaryService.updateAnniversary(userUid, anniversaryId, requestDto)
        return ResponseEntity.ok(updatedAnniversary)
    }

    @Operation(summary = "기념일 삭제", description = "특정 기념일을 삭제합니다. 본인 또는 파트너가 생성한 기념일만 삭제할 수 있습니다.")
    @ApiResponse(responseCode = "204", description = "기념일 삭제 성공 (No Content)")
    @DeleteMapping("/{anniversaryId}")
    fun deleteAnniversary(
        @Parameter(hidden = true) @UserId userUid: String,
        @Parameter(description = "삭제할 기념일의 ID", required = true) @PathVariable anniversaryId: String
    ): ResponseEntity<Void> {
        anniversaryService.deleteAnniversary(userUid, anniversaryId)
        return ResponseEntity.noContent().build()
    }
}