package com.company.rest.api.service

import com.company.rest.api.config.RegionDetails
import com.company.rest.api.dto.WeatherForecastResponseDto
import com.company.rest.api.dto.WeeklyForecastResponseDto
import com.company.rest.api.dto.kma.KmaApiResponseBase
import com.company.rest.api.dto.kma.KmaVilageFcstResponseDto
import com.company.rest.api.dto.kma.MidLandFcstResponseDto
import com.company.rest.api.dto.kma.MidTaResponseDto
import com.company.rest.api.entity.DailyWeatherForecast
import com.company.rest.api.entity.WeatherApiCallStatus
import com.company.rest.api.entity.WeatherApiLog
import com.company.rest.api.exception.CustomException
import com.company.rest.api.exception.ErrorCode
import com.company.rest.api.repository.DailyWeatherForecastRepository
import com.company.rest.api.repository.WeatherApiLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
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
    private val regionCodeMap: Map<String, RegionDetails>
) {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)

    @Value("\${weather.api.url}")
    private lateinit var midTermApiBaseUrl: String

    @Value("\${weather.api.key}")
    private lateinit var midTermApiKey: String

    @Value("\${village.api.url}")
    private lateinit var villageApiUrl: String

    @Value("\${village.api.key}")
    private lateinit var villageApiKey: String

    private val midLandFcstUriPath = "/getMidLandFcst"
    private val midTaUriPath = "/getMidTa"
    private val vilageFcstUriPath = "/getVilageFcst"

    val representativeCityTempCodes: List<String> = regionCodeMap.keys.toList()

    fun getRegionCodeMap(): Map<String, RegionDetails> {
        return this.regionCodeMap
    }

    private fun findRegionInfoByCityName(cityName: String): Pair<String, RegionDetails>? {
        return regionCodeMap.entries.find { it.value.cityName.equals(cityName, ignoreCase = true) }
            ?.toPair()
    }

    @Transactional(readOnly = true)
    fun getWeeklyForecastsByCityName(cityName: String, startDate: LocalDate): WeeklyForecastResponseDto? {
        logger.info("Attempting to get weekly forecast for city: {}, start date: {}", cityName, startDate)
        val regionInfoPair = findRegionInfoByCityName(cityName)

        if (regionInfoPair == null) {
            logger.warn("Region details not found for city name: {}", cityName)
            return null
        }
        val regionCode = regionInfoPair.first
        val regionDetails = regionInfoPair.second

        val endDate = startDate.plusDays(6) // startDate (D+0) 부터 D+6까지 총 7일간의 데이터 조회
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
            return null
        }

        val processedForecastDtos = mutableListOf<WeatherForecastResponseDto>()
        for (i in dailyForecastEntities.indices) {
            val currentEntity = dailyForecastEntities[i]
            var minTempForDto = currentEntity.minTemp
            var maxTempForDto = currentEntity.maxTemp

            // 현재 날짜의 minTemp 또는 maxTemp가 null인 경우, 다음 날 데이터에서 가져오기 시도
            if (minTempForDto == null || maxTempForDto == null) {
                if (i + 1 < dailyForecastEntities.size) { // 다음 날 데이터가 리스트에 있는지 확인
                    val nextDayEntity = dailyForecastEntities[i + 1]
                    if (minTempForDto == null) {
                        minTempForDto = nextDayEntity.minTemp
                        if (minTempForDto != null) {
                            logger.debug(
                                "Borrowed minTemp ({}) from next day ({}) for current day ({}) in region {}",
                                minTempForDto, nextDayEntity.forecastDate, currentEntity.forecastDate, regionCode
                            )
                        }
                    }
                    if (maxTempForDto == null) {
                        maxTempForDto = nextDayEntity.maxTemp
                        if (maxTempForDto != null) {
                            logger.debug(
                                "Borrowed maxTemp ({}) from next day ({}) for current day ({}) in region {}",
                                maxTempForDto, nextDayEntity.forecastDate, currentEntity.forecastDate, regionCode
                            )
                        }
                    }
                }
            }

            processedForecastDtos.add(
                WeatherForecastResponseDto(
                    date = currentEntity.forecastDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    regionCode = currentEntity.regionCode,
                    regionName = currentEntity.regionName ?: regionDetails.cityName, // 엔티티에 이름 없으면 Config에서 가져옴
                    minTemp = minTempForDto,
                    maxTemp = maxTempForDto,
                    weatherAm = currentEntity.weatherAm,
                    weatherPm = currentEntity.weatherPm,
                    rainProbAm = currentEntity.rainProbAm,
                    rainProbPm = currentEntity.rainProbPm
                )
            )
        }

        return WeeklyForecastResponseDto(
            regionCode = regionCode,
            regionName = regionDetails.cityName,
            forecasts = processedForecastDtos
        )
    }

    @Transactional(readOnly = true)
    fun getWeeklyForecasts(regionCode: String, startDate: LocalDate): List<DailyWeatherForecast> {
        val endDate = startDate.plusDays(6)
        return dailyWeatherForecastRepository.findByRegionCodeAndForecastDateBetweenOrderByForecastDateAsc(
            regionCode,
            startDate,
            endDate
        )
    }

    @Transactional
    fun fetchAndStoreWeeklyForecastsForCity(cityTempRegId: String, baseDateTime: LocalDateTime) {
        val regionDetails = regionCodeMap[cityTempRegId]
        if (regionDetails == null) {
            logger.warn("Unsupported city temperature regId: {}. Skipping mid-term forecast fetch.", cityTempRegId)
            return
        }
        val cityName = regionDetails.cityName
        val landFcstRegId = regionDetails.landFcstRegId
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
                    regionCode = cityTempRegId,
                    apiCallStatus = WeatherApiCallStatus.PENDING
                )
            }

        if (logEntry.apiCallStatus == WeatherApiCallStatus.SUCCESS) {
            logger.info(
                "Mid-term forecast for {} ({}) with baseTmFc={} already successfully processed. Skipping.",
                cityName,
                cityTempRegId,
                tmFc
            )
            return
        }

        dailyWeatherForecastRepository.deleteAllByLogEntry(logEntry)
        logEntry.dailyForecasts.clear()

        logEntry.apiCallStatus = WeatherApiCallStatus.PENDING
        logEntry.updatedAt = LocalDateTime.now()
        logEntry = weatherApiLogRepository.save(logEntry)

        val landFcstMono = fetchKmaApi(
            baseUrl = midTermApiBaseUrl,
            apiKey = midTermApiKey,
            uriPath = midLandFcstUriPath,
            queryParams = mapOf("regId" to landFcstRegId, "tmFc" to tmFc, "pageNo" to "1", "numOfRows" to "100"),
            responseType = MidLandFcstResponseDto::class.java,
            apiNameForLog = "MidLandFcst"
        )
            .doOnSubscribe { logger.debug("Fetching MidLandFcst for land_reg_id: {}, tmFc: {}", landFcstRegId, tmFc) }
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

        val midTaMono = fetchKmaApi(
            baseUrl = midTermApiBaseUrl,
            apiKey = midTermApiKey,
            uriPath = midTaUriPath,
            queryParams = mapOf("regId" to cityTempRegId, "tmFc" to tmFc, "pageNo" to "1", "numOfRows" to "100"),
            responseType = MidTaResponseDto::class.java,
            apiNameForLog = "MidTa"
        )
            .doOnSubscribe { logger.debug("Fetching MidTa for temp_reg_id: {}, tmFc: {}", cityTempRegId, tmFc) }
            .onErrorResume { e ->
                logger.error("Error fetching MidTa for temp_reg_id: {}, tmFc: {}: {}", cityTempRegId, tmFc, e.message)
                if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING) {
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_TEMP
                } else if (logEntry.apiCallStatus == WeatherApiCallStatus.API_ERROR_LAND) {
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_BOTH
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
                        logEntry.apiCallStatus = WeatherApiCallStatus.PARSING_FAILED
                    }
                } else {
                    if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING || logEntry.apiCallStatus == WeatherApiCallStatus.API_ERROR_UNKNOWN) {
                        if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING) logEntry.apiCallStatus =
                            WeatherApiCallStatus.NO_DATA_FOUND
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
            .switchIfEmpty(Mono.fromRunnable {
                if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING) {
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_UNKNOWN
                }
                logger.error(
                    "Both MID-TERM API calls resulted in empty Mono for {} ({}), tmFc={}. Final status: {}",
                    cityName,
                    cityTempRegId,
                    tmFc,
                    logEntry.apiCallStatus
                )
            })
            .doFinally { _ ->
                logEntry.updatedAt = LocalDateTime.now()
                weatherApiLogRepository.save(logEntry)
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
        val baseDateStr = baseDateTime.format(DateTimeFormatter.BASIC_ISO_DATE)
        val baseTimeStr = baseDateTime.format(DateTimeFormatter.ofPattern("HHmm"))

        logger.info(
            "Attempting to fetch SHORT-TERM forecasts (getVilageFcst) for {} (regionCode={}), nx={}, ny={}, baseDate={}, baseTime={}",
            cityName, regionCode, nx, ny, baseDateStr, baseTimeStr
        )

        val queryParams = mapOf(
            "nx" to nx.toString(),
            "ny" to ny.toString(),
            "base_date" to baseDateStr,
            "base_time" to baseTimeStr,
            "numOfRows" to "1000",
            "pageNo" to "1"
        )

        fetchKmaApi(
            baseUrl = villageApiUrl,
            apiKey = villageApiKey,
            uriPath = vilageFcstUriPath,
            queryParams = queryParams,
            responseType = KmaVilageFcstResponseDto::class.java,
            apiNameForLog = "VilageFcst"
        )
            .flatMap { response ->
                if (!isKmaResponseSuccess(response)) {
                    val errorMsg = response.response?.header?.resultMsg ?: "Unknown error"
                    logger.error(
                        "Short-term forecast API call failed for {}({}) with baseDate {}, baseTime {}. Result: {} - {}",
                        cityName, regionCode, baseDateStr, baseTimeStr, response.response?.header?.resultCode, errorMsg
                    )
                    return@flatMap Mono.error<Unit>(CustomException(ErrorCode.INTERNAL_SERVER_ERROR))
                }

                val items = response.response?.body?.items?.item
                if (items == null || items.isEmpty()) {
                    logger.warn(
                        "No items found in short-term forecast response for {}({}) with baseDate {}, baseTime {}",
                        cityName, regionCode, baseDateStr, baseTimeStr
                    )
                    return@flatMap Mono.empty<Unit>()
                }

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
                        regionCode,
                        cityName,
                        e.message,
                        e
                    )
                    return@flatMap Mono.error<Unit>(e)
                }
                Mono.just(Unit)
            }
            .doOnError { e ->
                logger.error(
                    "Failed to fetch or process short-term forecasts for {}({}) with baseDate {}, baseTime {}: {}",
                    cityName, regionCode, baseDateStr, baseTimeStr, e.message
                )
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

    private fun <T : KmaApiResponseBase> fetchKmaApi(
        baseUrl: String,
        apiKey: String,
        uriPath: String,
        queryParams: Map<String, String>,
        responseType: Class<T>,
        apiNameForLog: String
    ): Mono<T> {
        try {
            val encodedServiceKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
            var urlBuilder = StringBuilder("${baseUrl}${uriPath}?serviceKey=${encodedServiceKey}&dataType=JSON")

            queryParams.forEach { (key, value) ->
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
                        Mono.error(CustomException(ErrorCode.INTERNAL_SERVER_ERROR))
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
                    Mono.error(CustomException(ErrorCode.INTERNAL_SERVER_ERROR))
                }
                .flatMap { response ->
                    if (response.response?.header?.resultCode != "00") {
                        val errorMsg = response.response?.header?.resultMsg ?: "알 수 없는 오류"
                        logger.warn(
                            "KMA API error ({}) in response for URI {}. ResultCode: {}, ResultMsg: {}",
                            apiNameForLog, fullUrl, response.response?.header?.resultCode, errorMsg
                        )
                        Mono.error(CustomException(ErrorCode.INTERNAL_SERVER_ERROR))
                    } else {
                        Mono.just(response)
                    }
                }
        } catch (e: Exception) {
            logger.error("Failed to build KMA API ({}) URI or encode params: ${e.message}", apiNameForLog, e)
            return Mono.error(CustomException(ErrorCode.INTERNAL_SERVER_ERROR))
        }
    }

    private fun isKmaResponseSuccess(response: KmaApiResponseBase?): Boolean {
        return response?.response?.header?.resultCode == "00"
    }

    private fun parseAndSaveMidTermForecasts(
        logEntry: WeatherApiLog,
        landResponse: MidLandFcstResponseDto?,
        tempResponse: MidTaResponseDto?,
        baseDate: LocalDate,
        cityTempRegId: String,
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

        val forecastStartOffset = 3
        val forecastEndOffset = 10

        for (dayOffset in forecastStartOffset..forecastEndOffset) {
            val forecastDate = baseDate.plusDays(dayOffset.toLong())
            val landDayKey = dayOffset.toString()
            val tempDayKey = dayOffset.toString()

            val currentWfAm: String?
            val currentWfPm: String?
            val currentRnStAm: Int?
            val currentRnStPm: Int?

            if (dayOffset <= 7) {
                currentWfAm = landItem?.get("wf${landDayKey}Am") as? String
                currentWfPm = landItem?.get("wf${landDayKey}Pm") as? String
                currentRnStAm = (landItem?.get("rnSt${landDayKey}Am") as? Number)?.toInt()
                currentRnStPm = (landItem?.get("rnSt${landDayKey}Pm") as? Number)?.toInt()
            } else {
                currentWfAm = landItem?.get("wf$landDayKey") as? String
                currentWfPm = currentWfAm
                currentRnStAm = (landItem?.get("rnSt$landDayKey") as? Number)?.toInt()
                currentRnStPm = currentRnStAm
            }

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
                )
                logEntry.addDailyWeatherForecast(dailyForecast)
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

    private val fcstTimeFormatter = DateTimeFormatter.ofPattern("HHmm")

    private fun isAm(fcstTimeStr: String): Boolean {
        return try {
            val time = LocalTime.parse(fcstTimeStr, fcstTimeFormatter)
            time.hour < 12
        } catch (e: Exception) {
            logger.warn(
                "Could not parse fcstTimeStr: {} for AM/PM check, assuming PM for safety or specific logic needed.",
                fcstTimeStr
            )
            false
        }
    }

    private fun determineWeatherState(ptyValues: List<String>, skyValues: List<String>): String? { // 반환타입 Nullable로 변경
        if (ptyValues.isEmpty() && skyValues.isEmpty()) { // 해당 시간대에 PTY, SKY 정보가 모두 없으면 날씨 상태를 알 수 없음
            return null
        }
        return when {
            ptyValues.any { it == "1" } -> "비"
            ptyValues.any { it == "2" } -> "비/눈"
            ptyValues.any { it == "3" } -> "눈"
            ptyValues.any { it == "4" } -> "소나기"
            else -> {
                when {
                    skyValues.any { it == "4" } -> "흐림"
                    skyValues.any { it == "3" } -> "구름많음"
                    skyValues.any { it == "1" } -> "맑음" // SKY 정보가 하나라도 '맑음'이 있으면 '맑음'
                    else -> null // PTY도 없고 SKY 정보도 유효한 게 없으면 null
                }
            }
        }
    }

    @Transactional
    protected fun parseAndSaveShortTermForecasts(
        items: List<Map<String, Any>>,
        regionCode: String,
        cityName: String,
        apiBaseDate: LocalDate
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

        val forecastsByDate = items.groupBy { it["fcstDate"] as? String }
        val dailyForecastsToSaveOrUpdate = mutableListOf<DailyWeatherForecast>()

        for (dayOffset in 0..3) {
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

            var dailyMinTemp: Int? = null
            var dailyMaxTemp: Int? = null

            val amPopValues = mutableListOf<Int>()
            val pmPopValues = mutableListOf<Int>()
            val amPtyValues = mutableListOf<String>()
            val pmPtyValues = mutableListOf<String>()
            val amSkyValues = mutableListOf<String>()
            val pmSkyValues = mutableListOf<String>()

            hourlyItemsForDate.forEach { item ->
                val category = item["category"] as? String
                val fcstValueStr = item["fcstValue"] as? String
                val fcstTimeStr = item["fcstTime"] as? String

                if (fcstTimeStr == null) return@forEach

                val isCurrentAm = isAm(fcstTimeStr)

                when (category) {
                    "TMN" -> dailyMinTemp = fcstValueStr?.toDoubleOrNull()?.toInt()
                    "TMX" -> dailyMaxTemp = fcstValueStr?.toDoubleOrNull()?.toInt()
                    "POP" -> {
                        fcstValueStr?.toIntOrNull()?.let {
                            if (isCurrentAm) amPopValues.add(it) else pmPopValues.add(it)
                        }
                    }

                    "PTY" -> {
                        fcstValueStr?.let {
                            if (it != "0") {
                                if (isCurrentAm) amPtyValues.add(it) else pmPtyValues.add(it)
                            }
                        }
                    }

                    "SKY" -> {
                        fcstValueStr?.let {
                            if (isCurrentAm) amSkyValues.add(it) else pmSkyValues.add(it)
                        }
                    }
                }
            }

            val weatherAm = determineWeatherState(amPtyValues, amSkyValues)
            val weatherPm = determineWeatherState(pmPtyValues, pmSkyValues)
            val rainProbAm = amPopValues.maxOrNull()
            val rainProbPm = pmPopValues.maxOrNull()

            val forecastDate = LocalDate.parse(targetForecastDateStr, DateTimeFormatter.BASIC_ISO_DATE)
            val existingForecast =
                dailyWeatherForecastRepository.findByRegionCodeAndForecastDate(regionCode, forecastDate)
            val dailyForecast = existingForecast.orElseGet {
                DailyWeatherForecast(
                    regionCode = regionCode,
                    regionName = cityName,
                    forecastDate = forecastDate,
                    logEntry = null
                )
            }

            dailyForecast.minTemp = dailyMinTemp ?: dailyForecast.minTemp
            dailyForecast.maxTemp = dailyMaxTemp ?: dailyForecast.maxTemp
            dailyForecast.weatherAm = weatherAm
            dailyForecast.weatherPm = weatherPm
            dailyForecast.rainProbAm = rainProbAm
            dailyForecast.rainProbPm = rainProbPm
            dailyForecast.updatedAt = LocalDateTime.now()

            dailyForecastsToSaveOrUpdate.add(dailyForecast)
            logger.debug(
                "Prepared short-term daily forecast for {} on {}: AM='{}'({}%). PM='{}'({}%). MinT={}. MaxT={}",
                regionCode,
                forecastDate,
                weatherAm ?: "N/A",
                rainProbAm ?: "N/A",
                weatherPm ?: "N/A",
                rainProbPm ?: "N/A",
                dailyMinTemp ?: "N/A",
                dailyMaxTemp ?: "N/A"
            )
        }

        if (dailyForecastsToSaveOrUpdate.isNotEmpty()) {
            dailyWeatherForecastRepository.saveAll(dailyForecastsToSaveOrUpdate)
            logger.info(
                "Successfully saved/updated {} daily short-term forecasts for region {}",
                dailyForecastsToSaveOrUpdate.size,
                regionCode
            )
        }
    }
}