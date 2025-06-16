package com.company.rest.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaForecastResponseWrapper(
    @JsonProperty("response")
    val response: AirKoreaResponseBody?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaResponseBody(
    @JsonProperty("header")
    val header: AirKoreaResponseHeader?,
    @JsonProperty("body")
    val body: AirKoreaResponseItems?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaResponseHeader(
    @JsonProperty("resultCode")
    val resultCode: String?,
    @JsonProperty("resultMsg")
    val resultMsg: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaResponseItems(
    @JsonProperty("totalCount")
    val totalCount: Int?,
    @JsonProperty("items")
    val items: List<AirKoreaForecastItemDto>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaForecastItemDto(
    @JsonProperty("informGrade")
    val informGrade: String?,

    @JsonProperty("informOverall")
    val informOverall: String?,

    @JsonProperty("informCause")
    val informCause: String?,

    @JsonProperty("informCode")
    val informCode: String?,

    @JsonProperty("dataTime")
    val dataTime: String?,

    @JsonProperty("informData")
    val informData: String?
)