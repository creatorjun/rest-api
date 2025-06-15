package com.company.rest.api.service

import com.company.rest.api.config.HolidayApiProperties
import com.company.rest.api.dto.HolidayApiResponseWrapper
import com.company.rest.api.entity.Holiday
import com.company.rest.api.repository.HolidayRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.DigestUtils
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.DefaultUriBuilderFactory
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class HolidayService(
    @Qualifier("holidayApiWebClient") private val webClient: WebClient,
    private val holidayRepository: HolidayRepository,
    private val holidayApiProperties: HolidayApiProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(HolidayService::class.java)
    private val dateParser = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Transactional
    fun syncHolidaysForYear(year: Int) {
        logger.info("Starting to sync holidays for year: {}", year)

        val factory = DefaultUriBuilderFactory().apply {
            encodingMode = DefaultUriBuilderFactory.EncodingMode.NONE
        }

        val uri = factory.builder().path("/getRestDeInfo")
            .queryParam("solYear", year)
            .queryParam("ServiceKey", holidayApiProperties.serviceKey)
            .queryParam("_type", "json")
            .queryParam("numOfRows", "100") // 1년치 데이터를 모두 가져오기 위해 충분한 수로 설정
            .build()

        try {
            val response = webClient.get()
                .uri(uri.toString())
                .retrieve()
                .bodyToMono(HolidayApiResponseWrapper::class.java)
                .block()

            val holidayItems = response?.response?.body?.items?.item ?: emptyList()

            if (holidayItems.isNotEmpty()) {
                val holidays = holidayItems.filter { it.isHoliday == "Y" }
                    .map {
                        Holiday(
                            date = LocalDate.parse(it.locdate.toString(), dateParser),
                            name = it.dateName
                        )
                    }

                // 기존 연도 데이터 삭제 후 새로 삽입 (멱등성 보장)
                val yearStart = LocalDate.of(year, 1, 1)
                val yearEnd = LocalDate.of(year, 12, 31)
                holidayRepository.deleteAllByDateBetween(yearStart, yearEnd)
                holidayRepository.saveAll(holidays)
                logger.info("Successfully synced {} holidays for year: {}", holidays.size, year)
            } else {
                logger.warn("No holidays found from API for year: {}", year)
            }
        } catch (e: Exception) {
            logger.error("Failed to sync holidays for year: {}", year, e)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getHolidaysForYear(year: Int): List<Holiday> {
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        return holidayRepository.findByDateBetweenOrderByDateAsc(yearStart, yearEnd)
    }

    @Transactional(readOnly = true)
    fun getEtagForYear(year: Int): String {
        val holidays = getHolidaysForYear(year)
        if (holidays.isEmpty()) {
            return "empty-holidays-$year"
        }
        val jsonString = objectMapper.writeValueAsString(holidays)
        return DigestUtils.md5DigestAsHex(jsonString.toByteArray(StandardCharsets.UTF_8))
    }
}