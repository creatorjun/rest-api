package com.company.rest.api.service

import com.company.rest.api.config.RegionDetails // RegionDetails 임포트
import com.company.rest.api.dto.WeatherForecastResponseDto // 추가
import com.company.rest.api.dto.WeeklyForecastResponseDto // 추가
import com.company.rest.api.dto.kma.KmaApiResponseBase
import com.company.rest.api.dto.kma.MidLandFcstResponseDto
import com.company.rest.api.dto.kma.MidTaResponseDto
// 단기예보 DTO (새로 추가 필요 - 예시)
// import com.company.rest.api.dto.kma.VilageFcstResponseDto
import com.company.rest.api.entity.DailyWeatherForecast
import com.company.rest.api.entity.WeatherApiCallStatus
import com.company.rest.api.entity.WeatherApiLog
import com.company.rest.api.repository.DailyWeatherForecastRepository
import com.company.rest.api.repository.WeatherApiLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class WeatherService(
    @Qualifier("kmaWeatherWebClient") private val webClient: WebClient,
    private val weatherApiLogRepository: WeatherApiLogRepository,
    private val dailyWeatherForecastRepository: DailyWeatherForecastRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${weather.api.url}") private val kmaApiBaseUrl: String,
    private val regionCodeMap: Map<String, RegionDetails> // 타입 변경: Map<String, Pair<String, String>> -> Map<String, RegionDetails>
) {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)

    @Value("\${weather.api.key}")
    private lateinit var kmaApiKey: String

    // 기존 중기예보 API 경로
    private val midLandFcstUriPath = "/getMidLandFcst"
    private val midTaUriPath = "/getMidTa"

    // 새로운 단기예보 API 경로 (기상청 가이드 문서 기반)
    private val vilageFcstUriPath = "/getVilageFcst" // 단기예보조회 (Vil(l)ageFcstInfoService_2.0/getVilageFcst)

    // 대표 도시 목록은 regionCodeMap의 키(기온 예보 구역 코드)들을 사용
    val representativeCityTempCodes: List<String> = regionCodeMap.keys.toList()

    // regionCodeMap에 대한 public getter 변경
    fun getRegionCodeMap(): Map<String, RegionDetails> {
        return this.regionCodeMap
    }

    // 도시 이름으로 RegionDetails와 해당 regionCode(맵의 키)를 찾는 내부 메서드
    private fun findRegionInfoByCityName(cityName: String): Pair<String, RegionDetails>? {
        return regionCodeMap.entries.find { it.value.cityName.equals(cityName, ignoreCase = true) }
            ?.toPair() // Map.Entry<String, RegionDetails> to Pair<String, RegionDetails>
    }

    /**
     * 도시 이름으로 주간 일기 예보를 조회합니다. (D+0 ~ D+7)
     * 단기예보(D+0~D+2/3)와 중기예보(D+3/4~D+7) 데이터를 조합해야 합니다.
     * 현재는 DB에 저장된 DailyWeatherForecast 기준으로만 조회합니다.
     * 실제 데이터 fetching 및 통합은 스케줄러 또는 fetchAndStore... 메서드에서 담당해야 합니다.
     */
    @Transactional(readOnly = true)
    fun getWeeklyForecastsByCityName(cityName: String, startDate: LocalDate): WeeklyForecastResponseDto? {
        logger.info("Attempting to get weekly forecast for city: {}, start date: {}", cityName, startDate)
        val regionInfoPair = findRegionInfoByCityName(cityName)

        if (regionInfoPair == null) {
            logger.warn("Region details not found for city name: {}", cityName)
            return null
        }
        val regionCode = regionInfoPair.first // Map의 Key (기온 예보 구역 코드)
        val regionDetails = regionInfoPair.second

        // DB에서 D+0 ~ D+7까지의 예보를 조회 (이미 단기+중기 정보가 통합 저장되어 있다고 가정)
        val endDate = startDate.plusDays(6) // D+0 부터 D+6 (총 7일)
        val dailyForecastEntities =
            dailyWeatherForecastRepository.findByRegionCodeAndForecastDateBetweenOrderByForecastDateAsc(
                regionCode,
                startDate,
                endDate
            )

        if (dailyForecastEntities.isEmpty()) {
            logger.warn(
                "No forecast data found in DB for regionCode: {} (City: {}), from {} to {}",
                regionCode,
                cityName,
                startDate,
                endDate
            )
            // 여기서 바로 null을 반환하기보다, 스케줄러가 돌기 전이거나 데이터가 없을 수 있으므로,
            // 일단 빈 forecasts를 가진 DTO를 반환하거나, 컨트롤러에서 404를 결정하도록 null을 반환할 수 있습니다.
            // 현재 컨트롤러는 null이면 404를 반환하므로, 이대로 유지.
            return null
        }

        val forecastDtos = dailyForecastEntities.map { WeatherForecastResponseDto.fromEntity(it) }

        return WeeklyForecastResponseDto(
            regionCode = regionCode,
            regionName = regionDetails.cityName,
            forecasts = forecastDtos
        )
    }


    /**
     * (기존) 특정 지역 코드(기온 예보 코드)와 시작 날짜를 기준으로 DB에서 주간 예보를 가져옵니다.
     * 이 메서드는 주로 기존 로직(예: 스케줄러에서 특정 regionCode로 조회)과의 호환성을 위해 유지될 수 있습니다.
     */
    @Transactional(readOnly = true)
    fun getWeeklyForecasts(regionCode: String, startDate: LocalDate): List<DailyWeatherForecast> {
        val endDate = startDate.plusDays(6) // D+0 부터 D+6 (총 7일)
        return dailyWeatherForecastRepository.findByRegionCodeAndForecastDateBetweenOrderByForecastDateAsc(
            regionCode,
            startDate,
            endDate
        )
    }


    /**
     * (기존) 중기예보 (육상 + 기온)를 가져와 DB에 저장합니다. (D+3 ~ D+10 정도)
     * cityTempRegId는 RegionCodeConfig의 키 값 (기온 예보 구역 코드)입니다.
     */
    @Transactional
    fun fetchAndStoreWeeklyForecastsForCity(cityTempRegId: String, baseDateTime: LocalDateTime) {
        val regionDetails = regionCodeMap[cityTempRegId]
        if (regionDetails == null) {
            logger.warn("Unsupported city temperature regId: {}. Skipping mid-term forecast fetch.", cityTempRegId)
            return
        }
        val cityName = regionDetails.cityName
        val landFcstRegId = regionDetails.landFcstRegId // 중기 육상예보용 코드
        val tmFc = baseDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

        logger.info(
            "Attempting to fetch MID-TERM forecasts for {} (temp_reg_id={}), land_reg_id={}, baseTmFc={}",
            cityName,
            cityTempRegId,
            landFcstRegId,
            tmFc
        )

        var logEntry = weatherApiLogRepository.findByBaseDateTimeAndRegionCode(baseDateTime, cityTempRegId)
            .orElseGet {
                WeatherApiLog(
                    baseDateTime = baseDateTime,
                    regionCode = cityTempRegId, // cityTempRegId (기온 코드가 대표 코드)
                    apiCallStatus = WeatherApiCallStatus.PENDING
                )
            }

        // 중기예보의 경우, D+3 부터의 데이터이므로, baseDateTime이 오늘이면, 예보는 3일 뒤부터 시작.
        // 만약 성공한 로그가 있고, 그 로그의 예보가 너무 오래되지 않았다면 (예: 하루 이내) 스킵할 수도 있음 (현재는 baseDateTime 기준으로만 판단)
        if (logEntry.apiCallStatus == WeatherApiCallStatus.SUCCESS && logEntry.id != null) {
            logger.info(
                "Mid-term forecast for {} ({}) with baseTmFc={} already successfully processed. Skipping.",
                cityName,
                cityTempRegId,
                tmFc
            )
            return
        }

        // 기존 예보 데이터 삭제 (해당 로그에 귀속된 것들)
        if (logEntry.id != null) { // 이미 존재하는 로그라면
            dailyWeatherForecastRepository.deleteAllByLogEntry(logEntry)
            logEntry.dailyForecasts.clear() // 컬렉션도 비워줌
        }

        logEntry.apiCallStatus = WeatherApiCallStatus.PENDING
        logEntry.updatedAt = LocalDateTime.now()
        logEntry = weatherApiLogRepository.save(logEntry) // 먼저 PENDING으로 저장

        // 중기 육상 예보 (wfMn, wfMx 등 날씨 정보)
        val landFcstMono =
            fetchKmaApi(midLandFcstUriPath, landFcstRegId, tmFc, MidLandFcstResponseDto::class.java, "MidLandFcst")
                .doOnSubscribe {
                    logger.debug(
                        "Fetching MidLandFcst for land_reg_id: {}, tmFc: {}",
                        landFcstRegId,
                        tmFc
                    )
                }
                .onErrorResume { e ->
                    logger.error(
                        "Error fetching MidLandFcst for land_reg_id: {}, tmFc: {}: {}",
                        landFcstRegId,
                        tmFc,
                        e.message
                    )
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_LAND
                    Mono.empty<MidLandFcstResponseDto>()
                }

        // 중기 기온 예보 (taMin, taMax 등 기온 정보)
        val midTaMono = fetchKmaApi(midTaUriPath, cityTempRegId, tmFc, MidTaResponseDto::class.java, "MidTa")
            .doOnSubscribe { logger.debug("Fetching MidTa for temp_reg_id: {}, tmFc: {}", cityTempRegId, tmFc) }
            .onErrorResume { e ->
                logger.error("Error fetching MidTa for temp_reg_id: {}, tmFc: {}: {}", cityTempRegId, tmFc, e.message)
                if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING) { // 아직 다른 에러가 없었다면
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_TEMP
                } else if (logEntry.apiCallStatus == WeatherApiCallStatus.API_ERROR_LAND) { // 육상예보도 에러였다면
                    logEntry.apiCallStatus =
                        WeatherApiCallStatus.API_ERROR_BOTH // (새로운 상태 정의 또는 기존 API_ERROR_UNKNOWN 등 활용)
                    // 혹은 둘 다 에러면 그냥 최종적으로 NO_DATA_FOUND나 API_ERROR_UNKNOWN
                }
                Mono.empty<MidTaResponseDto>()
            }

        Mono.zip(landFcstMono.defaultIfEmpty(MidLandFcstResponseDto()), midTaMono.defaultIfEmpty(MidTaResponseDto()))
            .flatMap { tuple ->
                val landResponse = tuple.t1
                val tempResponse = tuple.t2

                logEntry.rawResponseLand =
                    if (landResponse.response?.body?.items?.item != null) objectMapper.writeValueAsString(landResponse) else null
                logEntry.rawResponseTemp =
                    if (tempResponse.response?.body?.items?.item != null) objectMapper.writeValueAsString(tempResponse) else null

                val landDataAvailable =
                    isKmaResponseSuccess(landResponse) && landResponse.response?.body?.items?.item?.isNotEmpty() == true
                val tempDataAvailable =
                    isKmaResponseSuccess(tempResponse) && tempResponse.response?.body?.items?.item?.isNotEmpty() == true

                if (landDataAvailable && tempDataAvailable) {
                    try {
                        // 파싱 및 저장 (중기예보용)
                        parseAndSaveMidTermForecasts(
                            logEntry,
                            landResponse,
                            tempResponse,
                            baseDateTime.toLocalDate(),
                            cityTempRegId,
                            cityName
                        )
                        logEntry.apiCallStatus = WeatherApiCallStatus.SUCCESS
                        logger.info(
                            "Successfully processed MID-TERM forecasts for {} ({}), tmFc={}",
                            cityName,
                            cityTempRegId,
                            tmFc
                        )
                    } catch (e: Exception) {
                        logger.error(
                            "Error parsing MID-TERM forecasts for {} ({}), tmFc={}: {}",
                            cityName,
                            cityTempRegId,
                            tmFc,
                            e.message,
                            e
                        )
                        logEntry.apiCallStatus = WeatherApiCallStatus.PARSING_FAILED
                    }
                } else if (landDataAvailable || tempDataAvailable) {
                    // 일부 데이터만 성공한 경우도 일단 PARTIAL_SUCCESS로 하고, 파싱 시도
                    logEntry.apiCallStatus = WeatherApiCallStatus.PARTIAL_SUCCESS
                    logger.warn(
                        "Partially successful or only one type of MID-TERM data for {} ({}). Land: {}, Temp: {}. tmFc={}",
                        cityName, cityTempRegId, landDataAvailable, tempDataAvailable, tmFc
                    )
                    try {
                        parseAndSaveMidTermForecasts(
                            logEntry,
                            landResponse,
                            tempResponse,
                            baseDateTime.toLocalDate(),
                            cityTempRegId,
                            cityName
                        )
                    } catch (e: Exception) {
                        logger.error(
                            "Error parsing partially successful MID-TERM forecasts for {} ({}), tmFc={}: {}",
                            cityName,
                            cityTempRegId,
                            tmFc,
                            e.message,
                            e
                        )
                        // PARTIAL_SUCCESS 상태에서 파싱 실패하면 PARSING_FAILED로 덮어쓸 수 있음.
                        logEntry.apiCallStatus = WeatherApiCallStatus.PARSING_FAILED
                    }
                } else { // 두 API 모두 유효한 데이터가 없는 경우
                    // logEntry.apiCallStatus는 API 호출 시 에러 상태로 이미 변경되었을 수 있음 (API_ERROR_LAND, API_ERROR_TEMP 등)
                    // 그것도 아니라면 NO_DATA_FOUND
                    if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING) { // API 호출은 성공했으나 데이터가 없는 경우
                        logEntry.apiCallStatus = WeatherApiCallStatus.NO_DATA_FOUND
                    }
                    logger.error(
                        "Failed to fetch any valid MID-TERM forecast data for {} ({}), tmFc={}. Final status: {}",
                        cityName,
                        cityTempRegId,
                        tmFc,
                        logEntry.apiCallStatus
                    )
                }
                Mono.just(logEntry)
            }
            .switchIfEmpty(Mono.fromRunnable { // landFcstMono와 midTaMono가 모두 empty()를 반환했을 때 (API 호출부터 실패한 경우)
                if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING) { // 위에서 상태 변경이 안 되었다면 (예: 네트워크 오류 등으로 onErrorResume으로 바로 빠짐)
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_UNKNOWN // 포괄적인 API 에러 상태
                }
                logger.error(
                    "Both MID-TERM API calls resulted in empty Mono for {} ({}), tmFc={}. Final status: {}",
                    cityName,
                    cityTempRegId,
                    tmFc,
                    logEntry.apiCallStatus
                )
            })
            .doFinally { _ -> // Reactive stream 완료 후 항상 실행 (성공/실패/취소 무관)
                logEntry.updatedAt = LocalDateTime.now()
                weatherApiLogRepository.save(logEntry) // 최종 상태 저장
            }
            .subscribe(
                { updatedLog ->
                    logger.info(
                        "Mid-term weather log updated for {}({}). Status: {}",
                        cityName,
                        cityTempRegId,
                        updatedLog.apiCallStatus
                    )
                },
                { e ->
                    logger.error(
                        "Unexpected error in reactive chain for MID-TERM weather fetch {}({}): {}",
                        cityName,
                        cityTempRegId,
                        e.message,
                        e
                    )
                }
            )
    }

    /**
     * 새로운 메서드: 단기 예보 (getVilageFcst)를 가져와 DB에 저장합니다. (D+0 ~ D+2/3)
     * regionCode는 RegionCodeConfig의 키 값 (기온 예보 구역 코드)입니다.
     * baseDateTime은 API 호출의 기준이 되는 발표 시각입니다 (예: 0500 KST).
     */
    @Transactional
    fun fetchAndStoreShortTermForecastsForRegion(regionCode: String, baseDateTime: LocalDateTime) {
        val regionDetails = regionCodeMap[regionCode]
        if (regionDetails == null) {
            logger.warn("Region details not found for regionCode: {}. Skipping short-term forecast fetch.", regionCode)
            return
        }

        val cityName = regionDetails.cityName
        val nx = regionDetails.nx
        val ny = regionDetails.ny
        val tmFc = baseDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) // base_date, base_time에 사용될 포맷

        logger.info(
            "Attempting to fetch SHORT-TERM forecasts (getVilageFcst) for {} (regionCode={}), nx={}, ny={}, baseDateTime={}",
            cityName, regionCode, nx, ny, tmFc
        )

        // WeatherApiLog를 사용한다면, 단기예보/중기예보를 구분할 방법이 필요.
        // 혹은 별도의 Log 엔티티를 만들거나, WeatherApiLog에 type 필드 추가.
        // 여기서는 일단 기존 WeatherApiLog를 재활용하되, 요청 파라미터나 응답 저장을 구분해야 함.
        // 또는 단기예보는 너무 자주 바뀌므로 로그를 간소화하거나, 별도 관리.

        // WeatherApiLog는 baseDateTime + regionCode가 UK.
        // 단기 예보는 baseTime이 더 다양함 (0200, 0500 등). 중기 예보는 0600, 1800.
        // 로그를 통합 관리하려면, 로그 엔티티에 'forecastType' (SHORT_TERM, MID_TERM) 같은 필드 추가 고려.
        // 여기서는 일단 단순화를 위해 로그 기록은 생략하고 API 호출 및 파싱에 집중. (추후 로그 전략 수립 필요)

        val requestParams = mapOf(
            "nx" to nx.toString(),
            "ny" to ny.toString(),
            "base_date" to baseDateTime.format(DateTimeFormatter.BASIC_ISO_DATE), // yyyyMMdd
            "base_time" to baseDateTime.format(DateTimeFormatter.ofPattern("HHmm")), // HHmm
            "numOfRows" to "1000" // 충분히 큰 값으로 설정하여 페이징 회피 시도 (API 최대치 확인 필요)
        )

        // 단기예보 API 호출
        // VilageFcstResponseDto는 KmaApiResponseBase를 상속하며, item 구조가 유사하다고 가정.
        // 실제로는 getVilageFcst 응답 구조에 맞는 DTO가 필요할 수 있음. KmaWeatherApiDto.kt에 VilageFcstItemDto 등을 정의해야함.
        // 여기서는 가상의 VilageFcstResponseDto를 사용한다고 가정.
        fetchKmaApi(
            vilageFcstUriPath,
            requestParams,
            KmaApiResponseBase::class.java,
            "VilageFcst"
        ) // KmaApiResponseBase로 임시 사용
            .flatMap { response ->
                if (!isKmaResponseSuccess(response)) {
                    logger.error(
                        "Short-term forecast API call failed for {}({}) with baseDateTime {}. Response: {}",
                        cityName, regionCode, tmFc, objectMapper.writeValueAsString(response.response?.header)
                    )
                    return@flatMap Mono.error<Unit>(ResponseStatusException(HttpStatus.BAD_GATEWAY, "단기예보 API 응답 오류"))
                }

                val items = response.response?.body?.items?.item
                if (items == null || items.isEmpty()) {
                    logger.warn(
                        "No items found in short-term forecast response for {}({}) with baseDateTime {}",
                        cityName,
                        regionCode,
                        tmFc
                    )
                    return@flatMap Mono.empty<Unit>()
                }

                // 여기서 items (List<Map<String, Any>>)를 파싱하여 DailyWeatherForecast 엔티티 리스트로 변환
                // D+0, D+1, D+2(D+3까지)에 대한 데이터를 추출하고, 시간별 데이터를 일별로 통합.
                // parseAndSaveShortTermForecasts(...) 호출
                try {
                    parseAndSaveShortTermForecasts(items, regionCode, cityName, baseDateTime.toLocalDate())
                    logger.info(
                        "Successfully parsed and initiated save for short-term forecasts for {}({})",
                        cityName,
                        regionCode
                    )
                } catch (e: Exception) {
                    logger.error(
                        "Error parsing or saving short-term forecasts for {}({}): {}",
                        cityName,
                        regionCode,
                        e.message,
                        e
                    )
                    return@flatMap Mono.error<Unit>(e) // 파싱 에러 전파
                }
                Mono.just(Unit)
            }
            .doOnError { e ->
                logger.error(
                    "Failed to fetch or process short-term forecasts for {}({}) with baseDateTime {}: {}",
                    cityName, regionCode, tmFc, e.message
                )
                // 실패 시 WeatherApiLog 상태 업데이트 (만약 로그를 사용한다면)
            }
            .subscribe(
                { logger.info("Short-term forecast processing completed for {}({}).", cityName, regionCode) },
                { e ->
                    logger.error(
                        "Unhandled error in short-term forecast reactive chain for {}({}): {}",
                        cityName,
                        regionCode,
                        e.message,
                        e
                    )
                }
            )
    }


    /**
     * 공통 KMA API 호출 로직 (중기예보용 - regId 파라미터 사용)
     */
    private fun <T : KmaApiResponseBase> fetchKmaApi(
        uriPath: String,
        regId: String, // 중기예보용 지역 ID
        tmFc: String,  // 발표시각
        responseType: Class<T>,
        apiNameForLog: String
    ): Mono<T> {
        val queryParams = mutableMapOf<String, String>()
        queryParams["regId"] = regId
        queryParams["tmFc"] = tmFc
        // 공통 파라미터 추가 (pageNo, numOfRows 등은 여기서 기본값 또는 고정값 사용)
        queryParams["pageNo"] = "1"
        queryParams["numOfRows"] = "100" // 중기예보는 보통 데이터가 많지 않으므로 넉넉하게 설정

        return executeKmaApiCall(uriPath, queryParams, responseType, apiNameForLog)
    }

    /**
     * 공통 KMA API 호출 로직 (단기예보용 - nx, ny 등 다양한 파라미터 사용)
     */
    private fun <T : KmaApiResponseBase> fetchKmaApi(
        uriPath: String,
        requestParams: Map<String, String>, // 단기예보용 파라미터 맵
        responseType: Class<T>,
        apiNameForLog: String
    ): Mono<T> {
        return executeKmaApiCall(uriPath, requestParams, responseType, apiNameForLog)
    }

    /**
     * 실제 KMA API 호출 실행 부분
     */
    private fun <T : KmaApiResponseBase> executeKmaApiCall(
        uriPath: String,
        params: Map<String, String>,
        responseType: Class<T>,
        apiNameForLog: String // 로깅을 위한 API 이름 (예: "MidLandFcst", "MidTa", "VilageFcst")
    ): Mono<T> {
        try {
            val encodedServiceKey = URLEncoder.encode(kmaApiKey, StandardCharsets.UTF_8.name())
            var urlBuilder = StringBuilder("${kmaApiBaseUrl}${uriPath}?serviceKey=${encodedServiceKey}&dataType=JSON")

            params.forEach { (key, value) ->
                urlBuilder.append(
                    "&${URLEncoder.encode(key, StandardCharsets.UTF_8.name())}=${
                        URLEncoder.encode(
                            value,
                            StandardCharsets.UTF_8.name()
                        )
                    }"
                )
            }
            val fullUrl = urlBuilder.toString()

            logger.info("KMA API Request ({}): GET {}", apiNameForLog, fullUrl)

            return webClient.get()
                .uri(URI.create(fullUrl))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus({ httpStatus -> httpStatus.isError }) { clientResponse ->
                    clientResponse.bodyToMono<String>().flatMap { errorBody ->
                        logger.error(
                            "KMA API call ({}) failed for {}. Status: {}, Body: {}",
                            apiNameForLog, fullUrl, clientResponse.statusCode(), errorBody
                        )
                        Mono.error(
                            ResponseStatusException(
                                clientResponse.statusCode(),
                                "기상청 API($apiNameForLog) 호출 실패: $errorBody"
                            )
                        )
                    }
                }
                .bodyToMono(responseType)
                .onErrorResume(org.springframework.web.reactive.function.UnsupportedMediaTypeException::class.java) { e ->
                    logger.error(
                        "UnsupportedMediaTypeException for URI ({}): {}. API likely returned XML. Error: {}",
                        apiNameForLog,
                        fullUrl,
                        e.message
                    )
                    Mono.error(
                        ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "기상청 API($apiNameForLog) 응답 형식이 올바르지 않습니다."
                        )
                    )
                }
                .flatMap { response ->
                    if (response.response?.header?.resultCode != "00") {
                        val errorMsg = response.response?.header?.resultMsg ?: "알 수 없는 오류"
                        logger.warn(
                            "KMA API error ({}) in response for URI {}. ResultCode: {}, ResultMsg: {}",
                            apiNameForLog, fullUrl, response.response?.header?.resultCode, errorMsg
                        )
                        Mono.error(
                            ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "기상청 API($apiNameForLog) 오류 응답: $errorMsg"
                            )
                        )
                    } else {
                        Mono.just(response)
                    }
                }
        } catch (e: Exception) {
            logger.error("Failed to build KMA API ({}) URI or encode params: ${e.message}", apiNameForLog, e)
            return Mono.error(
                ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "API($apiNameForLog) 요청 구성 중 오류 발생: ${e.message}"
                )
            )
        }
    }


    private fun isKmaResponseSuccess(response: KmaApiResponseBase?): Boolean {
        return response?.response?.header?.resultCode == "00"
    }

    /**
     * (기존) 중기예보 응답을 파싱하고 DailyWeatherForecast 엔티티로 변환하여 저장합니다.
     */
    private fun parseAndSaveMidTermForecasts(
        logEntry: WeatherApiLog,
        landResponse: MidLandFcstResponseDto?,
        tempResponse: MidTaResponseDto?,
        baseDate: LocalDate, // API 호출 시 사용된 base_date
        cityTempRegId: String, // 이 지역에 대한 예보임
        cityName: String?
    ) {
        val landItem = landResponse?.response?.body?.items?.item?.firstOrNull()
        val tempItem = tempResponse?.response?.body?.items?.item?.firstOrNull()

        if (landItem == null && tempItem == null) {
            logger.warn(
                "No mid-term forecast items found in land or temp response for {}({}), baseDate={}",
                cityName, cityTempRegId, baseDate
            )
            return
        }

        // 중기예보는 보통 D+3부터 D+10까지 제공됨
        val forecastStartOffset = 3
        val forecastEndOffset = 10 // 중기예보는 최대 10일까지 커버

        for (dayOffset in forecastStartOffset..forecastEndOffset) {
            val forecastDate = baseDate.plusDays(dayOffset.toLong())
            val landDayKey = dayOffset.toString() // wf3Am, rnSt3Am 등
            val tempDayKey = dayOffset.toString() // taMin3, taMax3 등

            // 중기 육상 예보에서 날씨(wf) 및 강수확률(rnSt) 추출
            val currentWfAm: String?
            val currentWfPm: String?
            val currentRnStAm: Int?
            val currentRnStPm: Int?

            if (dayOffset <= 7) { // D+3 ~ D+7은 오전/오후 구분 가능
                currentWfAm = landItem?.get("wf${landDayKey}Am") as? String
                currentWfPm = landItem?.get("wf${landDayKey}Pm") as? String
                currentRnStAm = (landItem?.get("rnSt${landDayKey}Am") as? Number)?.toInt()
                currentRnStPm = (landItem?.get("rnSt${landDayKey}Pm") as? Number)?.toInt()
            } else { // D+8 ~ D+10은 오전/오후 구분 없이 하나만 제공 (오전에 저장)
                currentWfAm = landItem?.get("wf$landDayKey") as? String
                currentWfPm = currentWfAm // 오후도 동일하게 처리하거나 null
                currentRnStAm = (landItem?.get("rnSt$landDayKey") as? Number)?.toInt()
                currentRnStPm = currentRnStAm // 오후도 동일하게 처리하거나 null
            }

            // 중기 기온 예보에서 최저/최고 기온 추출
            val currentTaMin = (tempItem?.get("taMin$tempDayKey") as? Number)?.toInt()
            val currentTaMax = (tempItem?.get("taMax$tempDayKey") as? Number)?.toInt()

            if (currentWfAm != null || currentWfPm != null || currentRnStAm != null || currentRnStPm != null || currentTaMin != null || currentTaMax != null) {
                val dailyForecast = DailyWeatherForecast(
                    logEntry = logEntry,
                    regionCode = cityTempRegId,
                    regionName = cityName,
                    forecastDate = forecastDate,
                    minTemp = currentTaMin,
                    maxTemp = currentTaMax,
                    weatherAm = currentWfAm,
                    weatherPm = currentWfPm,
                    rainProbAm = currentRnStAm,
                    rainProbPm = currentRnStPm
                    // 이 예보는 중기예보 API로부터 온 것이므로, 여기에 'forecastType = MID_TERM' 같은 구분자 추가 가능
                )
                // dailyWeatherForecastRepository.save(dailyForecast) // 여기서는 LogEntry에 추가하는 것으로 변경
                logEntry.addDailyWeatherForecast(dailyForecast) // LogEntry에 추가하고, LogEntry 저장 시 함께 저장되도록
            } else {
                logger.debug(
                    "No valid MID-TERM forecast data to save for {} on {} (offset: {})",
                    cityTempRegId,
                    forecastDate,
                    dayOffset
                )
            }
        }
    }


    /**
     * (신규) 단기예보 API (getVilageFcst) 응답을 파싱하고 DailyWeatherForecast 엔티티로 변환하여 저장합니다.
     * @param items API 응답의 item 리스트 (List<Map<String, Any>>)
     * @param regionCode 이 예보가 속한 지역의 대표 코드 (기온예보구역코드)
     * @param cityName 지역명
     * @param apiBaseDate API 호출 시 사용된 base_date (이 날짜 기준으로 D+0, D+1, D+2 예보가 생성됨)
     */
    @Transactional // 이 메서드는 DB 작업을 하므로 @Transactional 필요
    protected fun parseAndSaveShortTermForecasts(
        items: List<Map<String, Any>>,
        regionCode: String,
        cityName: String,
        apiBaseDate: LocalDate // API 호출의 기준이 된 날짜
    ) {
        if (items.isEmpty()) {
            logger.warn("No items to parse for short-term forecast for region: {}", regionCode)
            return
        }
        logger.info(
            "Parsing ${items.size} short-term forecast items for region: {}, city: {}, apiBaseDate: {}",
            regionCode,
            cityName,
            apiBaseDate
        )

        // 데이터 가공 단계:
        // 1. items를 fcstDate, fcstTime별로 그룹화하거나 정렬.
        // 2. 각 fcstDate에 대해 시간별 데이터를 수집.
        // 3. 수집된 시간별 데이터를 사용자가 정의한 규칙(24시간 통합)에 따라 DailyWeatherForecast 객체로 변환.
        // 4. 변환된 DailyWeatherForecast 객체를 DB에 저장 (기존 데이터가 있다면 업데이트 - UPSERT).

        // 예보일자(fcstDate)별로 아이템 그룹핑
        val forecastsByDate = items.groupBy { it["fcstDate"] as? String }

        val dailyForecastsToSave = mutableListOf<DailyWeatherForecast>()

        // D+0, D+1, D+2 (경우에 따라 D+3까지) 예보 처리
        // 단기예보는 보통 오늘, 내일, 모레까지의 상세 예보를 제공
        // API 응답의 fcstDate는 YYYYMMDD 형식
        for (dayOffset in 0..2) { // D+0, D+1, D+2 처리 (API가 D+3까지 주면 0..3)
            val targetForecastDateStr =
                apiBaseDate.plusDays(dayOffset.toLong()).format(DateTimeFormatter.BASIC_ISO_DATE)
            val hourlyItemsForDate = forecastsByDate[targetForecastDateStr]

            if (hourlyItemsForDate == null || hourlyItemsForDate.isEmpty()) {
                logger.debug(
                    "No short-term forecast items for date: {} (offset: {}) for region {}",
                    targetForecastDateStr,
                    dayOffset,
                    regionCode
                )
                continue
            }

            // 시간별 데이터 추출
            var dailyMinTemp: Int? = null
            var dailyMaxTemp: Int? = null
            val popValues = mutableListOf<Int>()
            val ptyValues = mutableMapOf<String, String>() // fcstTime -> PTY code
            val skyValues = mutableMapOf<String, String>() // fcstTime -> SKY code

            hourlyItemsForDate.forEach { item ->
                val category = item["category"] as? String
                val fcstValue = item["fcstValue"] as? String
                // val fcstTime = item["fcstTime"] as? String // HHMM

                when (category) {
                    "TMP" -> { // 1시간 기온
                        fcstValue?.toDoubleOrNull()?.toInt()?.let { temp ->
                            // dailyMinTemp, dailyMaxTemp는 TMN, TMX를 우선 사용하므로, 여기서는 참고용.
                            // 만약 TMN/TMX가 없다면 여기서 계산할 수도 있음.
                        }
                    }

                    "TMN" -> dailyMinTemp = fcstValue?.toDoubleOrNull()?.toInt() // 일 최저기온
                    "TMX" -> dailyMaxTemp = fcstValue?.toDoubleOrNull()?.toInt() // 일 최고기온
                    "POP" -> fcstValue?.toIntOrNull()?.let { popValues.add(it) } // 강수확률
                    "PTY" -> { // 강수형태
                        val fcstTime = item["fcstTime"] as? String
                        if (fcstTime != null && fcstValue != null) ptyValues[fcstTime] = fcstValue
                    }

                    "SKY" -> { // 하늘상태
                        val fcstTime = item["fcstTime"] as? String
                        if (fcstTime != null && fcstValue != null) skyValues[fcstTime] = fcstValue
                    }
                    // 다른 필요한 카테고리(REH 등)도 여기서 처리 가능
                }
            }

            // 24시간 통합 데이터 생성 로직
            val finalWeatherState: String
            val ptyCounts = ptyValues.values.groupBy { it }.mapValues { it.value.size }

            when {
                (ptyCounts["1"] ?: 0) > 0 -> finalWeatherState = "비"
                (ptyCounts["2"] ?: 0) > 0 -> finalWeatherState = "비/눈"
                (ptyCounts["3"] ?: 0) > 0 -> finalWeatherState = "눈"
                (ptyCounts["4"] ?: 0) > 0 -> finalWeatherState = "소나기"
                else -> { // 강수 없음 (모든 PTY가 0이거나, PTY 정보가 없는 시간들)
                    val skyCounts = skyValues.values.groupBy { it }.mapValues { it.value.size }
                    when {
                        (skyCounts["4"] ?: 0) > 0 -> finalWeatherState = "흐림"      // 흐림(4)
                        (skyCounts["3"] ?: 0) > 0 -> finalWeatherState = "구름많음" // 구름많음(3)
                        else -> finalWeatherState = "맑음"          // 맑음(1) 또는 정보 없음
                    }
                }
            }

            val finalRainProb = popValues.maxOrNull()

            val forecastDate = LocalDate.parse(targetForecastDateStr, DateTimeFormatter.BASIC_ISO_DATE)

            // 기존 DailyWeatherForecast 엔티티에 맞게 값 설정
            // weatherAm에 통합된 날씨 상태, rainProbAm에 통합된 강수확률 저장
            // weatherPm, rainProbPm은 null 또는 사용하지 않음으로 처리
            val dailyForecast = DailyWeatherForecast(
                // id는 자동 생성
                regionCode = regionCode,
                regionName = cityName,
                forecastDate = forecastDate,
                minTemp = dailyMinTemp,
                maxTemp = dailyMaxTemp,
                weatherAm = finalWeatherState, // 24시간 통합 날씨 상태
                weatherPm = null, // 24시간 통합이므로 Pm은 사용 안 함
                rainProbAm = finalRainProb, // 24시간 통합 최대 강수확률
                rainProbPm = null, // 24시간 통합이므로 Pm은 사용 안 함
                // logEntry는 단기예보 로그를 별도로 관리하거나, 혹은 null로 둘 수 있음.
                // 여기서는 WeatherApiLog와 직접 연동하지 않고 개별 저장한다고 가정.
                // 만약 연동한다면, 단기예보용 WeatherApiLog 인스턴스를 찾아 할당해야 함.
                logEntry = null // TODO: 단기예보용 로그 처리 방안 결정 필요
            )
            dailyForecastsToSave.add(dailyForecast)
            logger.debug(
                "Prepared short-term daily forecast for {} on {}: Weather='{}', RainProb={}, MinT={}, MaxT={}",
                regionCode, forecastDate, finalWeatherState, finalRainProb, dailyMinTemp, dailyMaxTemp
            )
        }

        if (dailyForecastsToSave.isNotEmpty()) {
            // 기존 데이터 삭제 후 새로 저장 (UPSERT 방식 권장)
            // 여기서는 각 forecastDate, regionCode에 대해 기존 데이터를 삭제하고 새로 삽입하는 방식 사용.
            // 또는 JpaRepository의 saveAll은 ID가 있으면 UPDATE, 없으면 INSERT를 수행하므로,
            // ID를 미리 결정하거나, (regionCode, forecastDate)를 UK로 하는 엔티티에서 find 후 업데이트/저장.
            // DailyWeatherForecast는 ID가 UUID이므로, (regionCode, forecastDate)로 먼저 조회 후 처리.

            dailyForecastsToSave.forEach { newForecast ->
                val existingForecast = dailyWeatherForecastRepository.findByRegionCodeAndForecastDate(
                    newForecast.regionCode,
                    newForecast.forecastDate
                )
                if (existingForecast.isPresent) {
                    val oldForecast = existingForecast.get()
                    // 기존 예보 업데이트
                    oldForecast.minTemp = newForecast.minTemp
                    oldForecast.maxTemp = newForecast.maxTemp
                    oldForecast.weatherAm = newForecast.weatherAm
                    oldForecast.weatherPm = newForecast.weatherPm // null
                    oldForecast.rainProbAm = newForecast.rainProbAm
                    oldForecast.rainProbPm = newForecast.rainProbPm // null
                    oldForecast.updatedAt = LocalDateTime.now()
                    // logEntry는 어떻게 처리할지...
                    dailyWeatherForecastRepository.save(oldForecast)
                    logger.info(
                        "Updated short-term forecast for {} on {}",
                        newForecast.regionCode,
                        newForecast.forecastDate
                    )
                } else {
                    dailyWeatherForecastRepository.save(newForecast)
                    logger.info(
                        "Saved new short-term forecast for {} on {}",
                        newForecast.regionCode,
                        newForecast.forecastDate
                    )
                }
            }
            logger.info(
                "Successfully saved/updated {} daily short-term forecasts for region {}",
                dailyForecastsToSave.size,
                regionCode
            )
        }
    }
}