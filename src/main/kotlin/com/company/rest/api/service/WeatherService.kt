package com.company.rest.api.service

import com.company.rest.api.dto.kma.KmaApiResponseBase
import com.company.rest.api.dto.kma.MidLandFcstResponseDto
import com.company.rest.api.dto.kma.MidTaResponseDto
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
import java.time.format.DateTimeFormatter

@Service
class WeatherService(
    @Qualifier("kmaWeatherWebClient") private val webClient: WebClient,
    private val weatherApiLogRepository: WeatherApiLogRepository,
    private val dailyWeatherForecastRepository: DailyWeatherForecastRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${weather.api.url}") private val kmaApiBaseUrl: String,
    private val regionCodeMap: Map<String, Pair<String, String>> // 주입된 맵
) {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)

    @Value("\${weather.api.key}")
    private lateinit var kmaApiKey: String

    private val midLandFcstUriPath = "/getMidLandFcst"
    private val midTaUriPath = "/getMidTa"

    val representativeCityTempCodes: List<String> = regionCodeMap.keys.toList()

    // regionCodeMap에 대한 public getter 추가
    fun getRegionCodeMap(): Map<String, Pair<String, String>> {
        return this.regionCodeMap
    }

    @Transactional
    fun fetchAndStoreWeeklyForecastsForCity(cityTempRegId: String, baseDateTime: LocalDateTime) {
        val cityInfo = regionCodeMap[cityTempRegId]
        if (cityInfo == null) {
            logger.warn("Unsupported city temperature regId: {}. Skipping forecast fetch.", cityTempRegId)
            return
        }
        val cityName = cityInfo.first
        val landFcstRegId = cityInfo.second
        val tmFc = baseDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

        logger.info("Attempting to fetch weekly forecasts for {} ({}), land_reg_id={}, baseTmFc={}", cityName, cityTempRegId, landFcstRegId, tmFc)

        var logEntry = weatherApiLogRepository.findByBaseDateTimeAndRegionCode(baseDateTime, cityTempRegId)
            .orElseGet {
                WeatherApiLog(
                    baseDateTime = baseDateTime,
                    regionCode = cityTempRegId,
                    apiCallStatus = WeatherApiCallStatus.PENDING
                )
            }

        if (logEntry.apiCallStatus == WeatherApiCallStatus.SUCCESS && logEntry.id != null) {
            logger.info("Weekly forecast for {} ({}) with baseTmFc={} already successfully processed. Skipping.", cityName, cityTempRegId, tmFc)
            return
        }
        if (logEntry.id != null) {
            dailyWeatherForecastRepository.deleteAllByLogEntry(logEntry)
            logEntry.dailyForecasts.clear()
        }

        logEntry.apiCallStatus = WeatherApiCallStatus.PENDING
        logEntry.updatedAt = LocalDateTime.now()
        logEntry = weatherApiLogRepository.save(logEntry)

        val landFcstMono = fetchKmaApi(midLandFcstUriPath, landFcstRegId, tmFc, MidLandFcstResponseDto::class.java)
            .doOnSubscribe { logger.debug("Fetching MidLandFcst for regId: {}, tmFc: {}", landFcstRegId, tmFc) }
            .onErrorResume { e ->
                logger.error("Error fetching MidLandFcst for regId: {}, tmFc: {}: {}", landFcstRegId, tmFc, e.message)
                logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_LAND
                Mono.empty<MidLandFcstResponseDto>()
            }

        val midTaMono = fetchKmaApi(midTaUriPath, cityTempRegId, tmFc, MidTaResponseDto::class.java)
            .doOnSubscribe { logger.debug("Fetching MidTa for regId: {}, tmFc: {}", cityTempRegId, tmFc) }
            .onErrorResume { e ->
                logger.error("Error fetching MidTa for regId: {}, tmFc: {}: {}", cityTempRegId, tmFc, e.message)
                if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING) {
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_TEMP
                } else if (logEntry.apiCallStatus == WeatherApiCallStatus.API_ERROR_LAND) {
                    logEntry.apiCallStatus = WeatherApiCallStatus.PARTIAL_SUCCESS
                }
                Mono.empty<MidTaResponseDto>()
            }

        Mono.zip(landFcstMono.defaultIfEmpty(MidLandFcstResponseDto()), midTaMono.defaultIfEmpty(MidTaResponseDto()))
            .flatMap { tuple ->
                val landResponse = tuple.t1
                val tempResponse = tuple.t2

                logEntry.rawResponseLand = if (landResponse.response?.body?.items?.item != null) objectMapper.writeValueAsString(landResponse) else null
                logEntry.rawResponseTemp = if (tempResponse.response?.body?.items?.item != null) objectMapper.writeValueAsString(tempResponse) else null

                val landDataAvailable = isKmaResponseSuccess(landResponse) && landResponse.response?.body?.items?.item?.isNotEmpty() == true
                val tempDataAvailable = isKmaResponseSuccess(tempResponse) && tempResponse.response?.body?.items?.item?.isNotEmpty() == true

                if (landDataAvailable && tempDataAvailable) {
                    try {
                        parseAndSaveForecasts(logEntry, landResponse, tempResponse, baseDateTime, cityTempRegId, cityName)
                        logEntry.apiCallStatus = WeatherApiCallStatus.SUCCESS
                        logger.info("Successfully processed weekly forecasts for {} ({}), tmFc={}", cityName, cityTempRegId, tmFc)
                    } catch (e: Exception) {
                        logger.error("Error parsing forecasts for {} ({}), tmFc={}: {}", cityName, cityTempRegId, tmFc, e.message, e)
                        logEntry.apiCallStatus = WeatherApiCallStatus.PARSING_FAILED
                    }
                } else if (landDataAvailable || tempDataAvailable) {
                    logEntry.apiCallStatus = WeatherApiCallStatus.PARTIAL_SUCCESS
                    logger.warn("Partially successful or only one type of data for {} ({}). Land: {}, Temp: {}. tmFc={}",
                        cityName, cityTempRegId, landDataAvailable, tempDataAvailable, tmFc)
                    try {
                        parseAndSaveForecasts(logEntry, landResponse, tempResponse, baseDateTime, cityTempRegId, cityName)
                    } catch (e: Exception) {
                        logger.error("Error parsing partially successful forecasts for {} ({}), tmFc={}: {}", cityName, cityTempRegId, tmFc, e.message, e)
                        logEntry.apiCallStatus = WeatherApiCallStatus.PARSING_FAILED
                    }
                } else {
                    if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING || logEntry.apiCallStatus == WeatherApiCallStatus.API_ERROR_UNKNOWN) {
                        logEntry.apiCallStatus = WeatherApiCallStatus.NO_DATA_FOUND
                    }
                    logger.error("Failed to fetch any valid forecast data for {} ({}), tmFc={}. Final status: {}", cityName, cityTempRegId, tmFc, logEntry.apiCallStatus)
                }
                Mono.just(logEntry)
            }
            .switchIfEmpty(Mono.fromRunnable {
                if (logEntry.apiCallStatus == WeatherApiCallStatus.PENDING || logEntry.apiCallStatus == WeatherApiCallStatus.PARTIAL_SUCCESS) {
                    logEntry.apiCallStatus = WeatherApiCallStatus.API_ERROR_UNKNOWN
                }
                logger.error("Both API calls resulted in empty Mono for {} ({}), tmFc={}. Final status: {}", cityName, cityTempRegId, tmFc, logEntry.apiCallStatus)
            })
            .doFinally {
                logEntry.updatedAt = LocalDateTime.now()
                weatherApiLogRepository.save(logEntry)
            }
            .subscribe(
                { updatedLog -> logger.info("Weather log updated for {}({}). Status: {}", cityName, cityTempRegId, updatedLog.apiCallStatus) },
                { e -> logger.error("Unexpected error in reactive chain for weather fetch {}({}): {}", cityName, cityTempRegId, e.message, e) }
            )
    }

    private fun <T : KmaApiResponseBase> fetchKmaApi(
        uriPath: String,
        regId: String,
        tmFc: String,
        responseType: Class<T>
    ): Mono<T> {
        try {
            val encodedServiceKey = URLEncoder.encode(kmaApiKey, StandardCharsets.UTF_8.name())
            val encodedRegId = URLEncoder.encode(regId, StandardCharsets.UTF_8.name())
            val encodedTmFc = URLEncoder.encode(tmFc, StandardCharsets.UTF_8.name())

            val fullUrl = "${kmaApiBaseUrl}${uriPath}" +
                    "?serviceKey=${encodedServiceKey}" +
                    "&pageNo=1" +
                    "&numOfRows=10" +
                    "&dataType=JSON" +
                    "&regId=${encodedRegId}" +
                    "&tmFc=${encodedTmFc}"

            logger.info("KMA API Request: GET {}", fullUrl)

            return webClient.get()
                .uri(URI.create(fullUrl))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus({ httpStatus -> httpStatus.isError }) { clientResponse ->
                    clientResponse.bodyToMono<String>().flatMap { errorBody ->
                        logger.error("KMA API call failed for {}. Status: {}, Body: {}",
                            fullUrl, clientResponse.statusCode(), errorBody)
                        Mono.error(ResponseStatusException(clientResponse.statusCode(), "기상청 API 호출 실패: $errorBody"))
                    }
                }
                .bodyToMono(responseType)
                .onErrorResume(org.springframework.web.reactive.function.UnsupportedMediaTypeException::class.java) { e ->
                    logger.error("UnsupportedMediaTypeException for URI: {}. API likely returned XML. Error: {}", fullUrl, e.message)
                    Mono.error(ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "기상청 API 응답 형식이 올바르지 않습니다."))
                }
                .flatMap { response ->
                    if (response.response?.header?.resultCode != "00") {
                        val errorMsg = response.response?.header?.resultMsg ?: "알 수 없는 오류"
                        logger.warn("KMA API error in response for URI {}. ResultCode: {}, ResultMsg: {}",
                            fullUrl, response.response?.header?.resultCode, errorMsg)
                        Mono.error(ResponseStatusException(HttpStatus.BAD_GATEWAY, "기상청 API 오류 응답: $errorMsg"))
                    } else {
                        Mono.just(response)
                    }
                }
        } catch (e: Exception) {
            logger.error("Failed to build KMA API URI or encode params: ${e.message}", e)
            return Mono.error(ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "API 요청 구성 중 오류 발생: ${e.message}"))
        }
    }

    private fun isKmaResponseSuccess(response: KmaApiResponseBase?): Boolean {
        return response?.response?.header?.resultCode == "00"
    }

    private fun parseAndSaveForecasts(
        logEntry: WeatherApiLog,
        landResponse: MidLandFcstResponseDto?,
        tempResponse: MidTaResponseDto?,
        baseDateTime: LocalDateTime,
        cityTempRegId: String,
        cityName: String?
    ) {
        val landItem = landResponse?.response?.body?.items?.item?.firstOrNull()
        val tempItem = tempResponse?.response?.body?.items?.item?.firstOrNull()

        if (landItem == null && tempItem == null) {
            logger.warn("No forecast items found in land or temp response for {}({}), tmFc={}",
                cityName, cityTempRegId, logEntry.baseDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")))
            return
        }

        val forecastStartOffset = 3
        val forecastEndOffset = 10

        for (dayOffset in forecastStartOffset..forecastEndOffset) {
            val forecastDate = baseDateTime.toLocalDate().plusDays(dayOffset.toLong())
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
                logger.debug("No valid forecast data to save for {} on {} (offset: {})", cityTempRegId, forecastDate, dayOffset)
            }
        }
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
}