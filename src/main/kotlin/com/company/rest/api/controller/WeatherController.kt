package com.company.rest.api.controller

import com.company.rest.api.dto.WeatherResponseDto
import com.company.rest.api.service.WeatherService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "Weather", description = "날씨 정보 조회 API")
class WeatherController(
    private val weatherService: WeatherService
) {
    @Operation(
        summary = "특정 위치의 종합 날씨 정보 조회",
        description = "쿼리 파라미터로 받은 위도와 경도를 기준으로, 서버에 저장된 현재/1시간/일일 예보 정보를 종합하여 반환합니다."
    )
    @ApiResponse(
        responseCode = "200", description = "날씨 정보 조회 성공",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = WeatherResponseDto::class)
        )]
    )
    @GetMapping
    fun getWeather(
        @Parameter(description = "조회할 위치의 위도", required = true, example = "37.56")
        @RequestParam("lat") latitude: Double,
        @Parameter(description = "조회할 위치의 경도", required = true, example = "127.0")
        @RequestParam("lon") longitude: Double
    ): ResponseEntity<WeatherResponseDto> {
        val weatherData = weatherService.getWeatherForLocation(latitude, longitude)
        return ResponseEntity.ok(weatherData)
    }
}