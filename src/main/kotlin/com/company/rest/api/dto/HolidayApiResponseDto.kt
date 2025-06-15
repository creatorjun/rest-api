package com.company.rest.api.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class HolidayApiResponseWrapper(
    @JsonProperty("response")
    val response: HolidayApiResponseBody?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HolidayApiResponseBody(
    @JsonProperty("header")
    val header: HolidayApiResponseHeader?,
    @JsonProperty("body")
    val body: HolidayApiBody?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HolidayApiResponseHeader(
    @JsonProperty("resultCode")
    val resultCode: String?,
    @JsonProperty("resultMsg")
    val resultMsg: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HolidayApiBody(
    @JsonProperty("items")
    val items: HolidayApiItems?,
    @JsonProperty("totalCount")
    val totalCount: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HolidayApiItems(
    @JsonProperty("item")
    val item: List<HolidayApiItemDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HolidayApiItemDto(
    @JsonProperty("locdate")
    val locdate: Int,

    @JsonProperty("dateName")
    val dateName: String,

    @JsonProperty("isHoliday")
    val isHoliday: String
)