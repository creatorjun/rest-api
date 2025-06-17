package com.company.rest.api.service

import com.company.rest.api.config.AirQualityProperties
import com.company.rest.api.config.LocationDetails
import com.company.rest.api.dto.AirKoreaForecastItemDto
import com.company.rest.api.dto.AirKoreaForecastResponseWrapper
import com.company.rest.api.dto.AirQualityInfoResponseDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Service
class AirQualityService(
    @Qualifier("airKoreaWebClient") private val webClient: WebClient,
    private val airQualityProperties: AirQualityProperties,
    private val locations: List<LocationDetails>
) {
    private val logger = LoggerFactory.getLogger(AirQualityService::class.java)

    private val forecastCache = ConcurrentHashMap<LocalDate, Map<String, AirQualityInfoResponseDto>>()
    private val regionNameMap = locations.associateBy { it.airkoreaRegionName }

    fun getAirQualityInfo(cityName: String, date: LocalDate): AirQualityInfoResponseDto? {
        val airkoreaRegionName = locations.find { it.cityName == cityName }?.airkoreaRegionName
        return forecastCache[date]?.get(airkoreaRegionName)
    }

    fun fetchDailyForecasts(date: LocalDate) {
        logger.info("Starting to fetch AirKorea daily forecasts for date: {}", date)
        try {
            val pm10Forecast = fetchForecastFor(date, "PM10")
            val pm25Forecast = fetchForecastFor(date, "PM25")

            if (pm10Forecast == null && pm25Forecast == null) {
                logger.warn("Both PM10 and PM2.5 forecasts are null for date: {}. Caching will be skipped.", date)
                return
            }

            val combinedForecasts = mutableMapOf<String, AirQualityInfoResponseDto>()
            val allRegionNames = regionNameMap.keys

            val pm10Grades = pm10Forecast?.let { parseGradeString(it.informGrade) } ?: emptyMap()
            val pm25Grades = pm25Forecast?.let { parseGradeString(it.informGrade) } ?: emptyMap()

            for (regionName in allRegionNames) {
                combinedForecasts[regionName] = AirQualityInfoResponseDto(
                    pm10Grade = pm10Grades[regionName],
                    pm25Grade = pm25Grades[regionName]
                )
            }

            forecastCache[date] = combinedForecasts
            logger.info("Successfully fetched and cached AirKorea forecasts for date: {}", date)

        } catch (e: Exception) {
            logger.error("Failed to fetch AirKorea daily forecasts for date: {}", date, e)
        }
    }

    private fun fetchForecastFor(date: LocalDate, informCode: String): AirKoreaForecastItemDto? {
        return try {
            val encodedServiceKey = URLEncoder.encode(airQualityProperties.serviceKey, StandardCharsets.UTF_8.toString())

            val requestUrlString = "${airQualityProperties.baseUrl}/getMinuDustFrcstDspth" +
                    "?serviceKey=$encodedServiceKey" +
                    "&returnType=json" +
                    "&numOfRows=100" +
                    "&pageNo=1" +
                    "&searchDate=${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
                    "&informCode=$informCode"

            val requestUri = URI.create(requestUrlString)

            val response = webClient.get()
                .uri(requestUri)
                .retrieve()
                .bodyToMono(AirKoreaForecastResponseWrapper::class.java)
                .block()

            val items = response?.response?.body?.items
            if (items.isNullOrEmpty()) {
                logger.warn("No items found in AirKorea response for date: {}, informCode: {}", date, informCode)
                null
            } else {
                items.first()
            }
        } catch (e: WebClientResponseException) {
            logger.error(
                "Error fetching AirKorea forecast for date: {}, informCode: {}. Status: {}, Body: {}",
                date,
                informCode,
                e.statusCode,
                e.responseBodyAsString
            )
            null
        } catch (e: Exception) {
            logger.error(
                "Error fetching AirKorea forecast for date: {}, informCode: {}. Error: {}",
                date,
                informCode,
                e.message
            )
            null
        }
    }

    private fun parseGradeString(gradeString: String?): Map<String, String> {
        if (gradeString.isNullOrBlank()) return emptyMap()
        return gradeString.split(',')
            .mapNotNull { part ->
                val pair = part.split(':').map { it.trim() }
                if (pair.size == 2) pair[0] to pair[1] else null
            }.toMap()
    }
}