package com.company.rest.api.dto.kma

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 공통 응답 구조를 위한 기본 클래스
@JsonIgnoreProperties(ignoreUnknown = true)
open class KmaApiResponseBase {
    // lateinit var response: KmaResponse  <-- 이 부분을 아래와 같이 변경합니다.
    var response: KmaResponse? = null // lateinit이 아닌 nullable var로 변경하고 null로 초기화
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
    @JsonProperty("item") // JSON에서 item은 단일 객체 또는 리스트일 수 있음. 여기서는 리스트로 가정.
    val item: List<Map<String, Any>>? // 다양한 예보 항목들이 포함될 수 있으므로 Map 사용, item이 없을 수도 있음
)

// --- 중기육상예보 (getMidLandFcst) DTO ---
// KmaApiResponseBase를 상속하여 response 필드를 사용
class MidLandFcstResponseDto : KmaApiResponseBase()

// --- 중기기온조회 (getMidTa) DTO ---
// KmaApiResponseBase를 상속하여 response 필드를 사용
class MidTaResponseDto : KmaApiResponseBase()