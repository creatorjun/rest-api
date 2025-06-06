package com.company.rest.api.scheduler

import com.company.rest.api.service.WeatherService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class WeatherScheduler(
    private val weatherService: WeatherService
) {
    private val logger = LoggerFactory.getLogger(WeatherScheduler::class.java)
    private val KOREA_ZONE_ID = ZoneId.of("Asia/Seoul")

    /**
     * 지정된 날짜를 기준으로 단기예보를 가져와서 저장/업데이트합니다.
     * @param date 기준 날짜
     */
    fun runFetchShortTermForecasts(date: LocalDate) {
        val executionTime = LocalDateTime.now(KOREA_ZONE_ID)
        logger.info(
            "Executing task: runFetchShortTermForecasts for date {} at {} (Korea Time)",
            date,
            executionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        // 단기예보 API 호출 기준 시각은 'date'의 오전 5시로 설정
        val baseDateTimeForShortTermApi = date.atTime(LocalTime.of(5, 0))

        val cityCodesToFetch = weatherService.representativeCityTempCodes
        if (cityCodesToFetch.isEmpty()) {
            logger.warn("No representative city codes found in WeatherService. Skipping short-term forecast fetch.")
            return
        }

        logger.info("Targeting KMA Short-Term API announcement time (base_date={}, base_time={})",
            baseDateTimeForShortTermApi.format(DateTimeFormatter.BASIC_ISO_DATE),
            baseDateTimeForShortTermApi.format(DateTimeFormatter.ofPattern("HHmm"))
        )
        logger.info("Fetching SHORT-TERM forecasts for the following city temp_reg_ids: {}", cityCodesToFetch)

        cityCodesToFetch.forEach { regionCode ->
            try {
                weatherService.fetchAndStoreShortTermForecastsForRegion(regionCode, baseDateTimeForShortTermApi)
            } catch (e: Exception) {
                logger.error(
                    "An unexpected error occurred during the short-term fetch for regionCode {}: {}",
                    regionCode,
                    e.message,
                    e
                )
            }
        }

        logger.info("Task runFetchShortTermForecasts for date {} completed at {} (Korea Time)",
            date,
            LocalDateTime.now(KOREA_ZONE_ID).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    /**
     * 지정된 날짜를 기준으로 중기예보를 가져와서 저장/업데이트합니다.
     * @param date 기준 날짜
     */
    fun runFetchMidTermForecasts(date: LocalDate) {
        val executionTime = LocalDateTime.now(KOREA_ZONE_ID)
        logger.info(
            "Executing task: runFetchMidTermForecasts for date {} at {} (Korea Time)",
            date,
            executionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        // 중기예보 API 호출 기준 시각은 'date'의 오전 6시로 설정
        val baseDateTimeForMidTermApi = date.atTime(LocalTime.of(6, 0))

        val cityCodesToFetch = weatherService.representativeCityTempCodes
        if (cityCodesToFetch.isEmpty()) {
            logger.warn("No representative city codes found in WeatherService. Skipping mid-term forecast fetch.")
            return
        }

        logger.info("Targeting KMA Mid-Term API announcement time (tmFc): {}", baseDateTimeForMidTermApi.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")))
        logger.info("Fetching MID-TERM forecasts for the following city temp_reg_ids: {}", cityCodesToFetch)

        cityCodesToFetch.forEach { cityTempRegId ->
            try {
                weatherService.fetchAndStoreWeeklyForecastsForCity(cityTempRegId, baseDateTimeForMidTermApi)
            } catch (e: Exception) {
                logger.error(
                    "An unexpected error occurred during the mid-term fetch for city temp_reg_id {}: {}",
                    cityTempRegId,
                    e.message,
                    e
                )
            }
        }

        logger.info("Task runFetchMidTermForecasts for date {} completed at {} (Korea Time)",
            date,
            LocalDateTime.now(KOREA_ZONE_ID).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }


    /**
     * 매일 오전 5시 15분 (한국 시간 기준)에 실행되는 스케줄러.
     * 오늘 날짜의 단기예보를 가져옵니다.
     */
    @Scheduled(cron = "0 15 5 * * ?", zone = "Asia/Seoul")
    fun fetchShortTermForecastsTask() {
        logger.info("Scheduled task 'fetchShortTermForecastsTask' triggered.")
        runFetchShortTermForecasts(LocalDate.now(KOREA_ZONE_ID))
    }

    /**
     * 매일 오전 6시 10분 (한국 시간 기준)에 실행되는 스케줄러.
     * 오늘 날짜의 중기예보를 가져옵니다.
     */
    @Scheduled(cron = "0 10 6 * * ?", zone = "Asia/Seoul")
    fun fetchMidTermForecastsTask() {
        logger.info("Scheduled task 'fetchMidTermForecastsTask' triggered.")
        runFetchMidTermForecasts(LocalDate.now(KOREA_ZONE_ID))
    }
}