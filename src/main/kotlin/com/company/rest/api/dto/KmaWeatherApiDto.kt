package com.company.rest.api.dto.kma

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 공통 응답 구조를 위한 기본 클래스
@JsonIgnoreProperties(ignoreUnknown = true)
open class KmaApiResponseBase {
    var response: KmaResponse? = null
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaResponse(
    val header: KmaHeader,
    val body: KmaBody? // body는 없을 수도 있으므로 nullable
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaHeader(
    val resultCode: String,
    val resultMsg: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaBody(
    val dataType: String?, // XML 또는 JSON
    val items: KmaItemsContainer?, // items가 없을 수도 있으므로 nullable
    val pageNo: Int?,
    val numOfRows: Int?,
    val totalCount: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaItemsContainer(
    // 각 item은 다양한 필드를 가질 수 있으므로 Map<String, Any>로 유지.
    // 서비스 레이어에서 각 필드를 적절한 타입으로 캐스팅하여 사용.
    @JsonProperty("item")
    val item: List<Map<String, Any>>? // item이 없을 수도 있음
)

// --- 중기육상예보 (getMidLandFcst) DTO ---
class MidLandFcstResponseDto : KmaApiResponseBase()

// --- 중기기온조회 (getMidTa) DTO ---
class MidTaResponseDto : KmaApiResponseBase()

// --- 단기예보조회 (getVilageFcst) DTO (새로 추가) ---
// VilageFcstInfoService_2.0/getVilageFcst 응답을 위한 DTO
// 내부 구조는 KmaApiResponseBase를 따르며, item은 List<Map<String, Any>>로 처리됩니다.
class KmaVilageFcstResponseDto : KmaApiResponseBase()

// 만약 단기예보의 item 내부 필드가 고정적이고 명확하다면,
// 아래와 같이 KmaVilageFcstItemDto를 정의하고 KmaItemsContainer를 수정할 수도 있습니다.
// (현재는 WeatherService의 기존 파싱 방식을 고려하여 Map<String, Any>를 유지)
/*
@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaVilageFcstItemDto(
    val baseDate: String?,
    val baseTime: String?,
    val category: String?,
    val fcstDate: String?,
    val fcstTime: String?,
    val fcstValue: String?,
    val nx: Int?,
    val ny: Int?
)

// KmaItemsContainer를 아래와 같이 수정하거나, VilageFcst용 별도 ItemsContainer를 만들 경우:
// @JsonIgnoreProperties(ignoreUnknown = true)
// data class KmaVilageItemsContainer(
//    @JsonProperty("item")
//    val item: List<KmaVilageFcstItemDto>?
// )
//
// // 그리고 KmaBody에서 items 필드 타입을 KmaVilageItemsContainer? 로 변경해야 함.
// // 또는 KmaVilageFcstResponseDto가 KmaResponse를 커스터마이징하도록 변경.
*/