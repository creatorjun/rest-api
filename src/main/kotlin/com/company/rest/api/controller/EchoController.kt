package com.company.rest.api.controller // 패키지 경로 확인

import com.fasterxml.jackson.databind.JsonNode // JsonNode 사용 시 필요
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody // 이름 충돌 방지
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType // MediaType 사용 시 필요
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/echo") // API 경로 설정 (예: /api/v1/echo)
@Tag(name = "Echo Controller", description = "받은 데이터를 그대로 반환하는 API")
class EchoController {

    /**
     * 방법 1: JSON 객체를 Map으로 받아 그대로 반환
     * - 가장 일반적인 JSON 객체 형태를 받을 때 유용합니다.
     * - 입력이 유효한 JSON 객체가 아니면 에러가 발생할 수 있습니다.
     */
    @Operation(
        summary = "JSON Echo (Map)",
        description = "POST 요청으로 받은 JSON 객체(Map)를 그대로 응답으로 반환합니다."
    )
    @SwaggerRequestBody(
        description = "서버로 보낼 임의의 JSON 객체",
        required = true,
        content = [Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = Schema(type = "object"), // 임의의 객체 스키마
            examples = [ExampleObject(
                name = "샘플 요청",
                summary = "예시 JSON 객체",
                value = """{"key1": "value1", "key2": 123, "nested": {"subKey": true}}"""
            )]
        )]
    )
    @ApiResponse(
        responseCode = "200", description = "성공적으로 데이터를 받아 반환함",
        content = [Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = Schema(type = "object") // 반환 스키마도 임의의 객체
        )]
    )
    @PostMapping("/map") // 경로: /api/v1/echo/map
    fun echoJsonMap(@RequestBody body: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        // 받은 Map 데이터를 그대로 ResponseEntity에 담아 반환
        // 상태 코드 200 (OK)와 함께 반환됨
        return ResponseEntity.ok(body)
    }

    /**
     * 방법 2: 요청 본문을 문자열(String)로 받아 그대로 반환
     * - JSON이 아닌 데이터나, 받은 문자열 그대로 처리하고 싶을 때 사용합니다.
     * - Content-Type이 application/json이 아니어도 받을 수 있습니다 (text/plain 등).
     */
    @Operation(
        summary = "Raw Echo (String)",
        description = "POST 요청으로 받은 본문(Raw String)을 그대로 응답으로 반환합니다."
    )
    @SwaggerRequestBody(
        description = "서버로 보낼 임의의 텍스트 또는 JSON 문자열",
        required = true,
        content = [
            Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(type = "string"), examples = [ExampleObject(value = """{"message": "hello"}""")]),
            Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = Schema(type = "string"), examples = [ExampleObject(value = "Just a plain text")])
        ]
    )
    @ApiResponse(
        responseCode = "200", description = "성공적으로 데이터를 받아 반환함",
        content = [
            Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(type = "string")),
            Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = Schema(type = "string"))
        ]
    )
    @PostMapping("/string", consumes = ["*/*"], produces = [MediaType.TEXT_PLAIN_VALUE]) // 모든 타입을 받고, 평문 텍스트로 반환 명시
    fun echoRawString(@RequestBody body: String): ResponseEntity<String> {
        // 받은 문자열 데이터를 그대로 ResponseEntity에 담아 반환
        return ResponseEntity.ok(body)
    }

    /**
     * 방법 3: JSON을 JsonNode로 받아 그대로 반환 (더 유연한 처리 가능)
     * - Jackson 라이브러리의 JsonNode를 사용하여 트리 형태로 JSON 데이터에 접근할 수 있습니다.
     * - 받은 데이터가 유효한 JSON이어야 합니다.
     */
    @Operation(
        summary = "JSON Echo (JsonNode)",
        description = "POST 요청으로 받은 JSON 객체(JsonNode)를 그대로 응답으로 반환합니다."
    )
    @SwaggerRequestBody(
        description = "서버로 보낼 임의의 JSON 객체",
        required = true,
        content = [Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = Schema(implementation = Any::class), // JsonNode는 직접 참조 어려우므로 Any 또는 Object 사용
            examples = [ExampleObject(
                name = "샘플 JsonNode 요청",
                summary = "예시 JSON 객체",
                value = """{"user": {"name": "홍길동", "age": 30}, "active": true}"""
            )]
        )]
    )
    @ApiResponse(
        responseCode = "200", description = "성공적으로 데이터를 받아 반환함",
        content = [Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = Schema(implementation = Any::class)
        )]
    )
    @PostMapping("/jsonnode") // 경로: /api/v1/echo/jsonnode
    fun echoJsonNode(@RequestBody body: JsonNode): ResponseEntity<JsonNode> {
        // 받은 JsonNode 데이터를 그대로 ResponseEntity에 담아 반환
        return ResponseEntity.ok(body)
    }
}