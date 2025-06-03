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
     * 매일 오전 5시 15분 (한국 시간 기준)에 실행되어,
     * 각 대표 도시에 대한 단기예보(D+0 ~ D+2/3)를 기상청으로부터 가져와 DB에 저장/업데이트합니다.
     * 단기예보는 보통 05시 발표 자료를 활용합니다.
     */
    @Scheduled(cron = "0 15 5 * * ?", zone = "Asia/Seoul") // 매일 오전 5시 15분
    fun fetchShortTermForecastsTask() {
        val executionTime = LocalDateTime.now(KOREA_ZONE_ID)
        logger.info(
            "Executing scheduled task: fetchShortTermForecastsTask at {} (Korea Time)",
            executionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        // 단기예보 API 호출 기준 시각은 당일 오전 5시로 설정
        val baseDateTimeForShortTermApi = LocalDate.now(KOREA_ZONE_ID).atTime(LocalTime.of(5, 0))

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
                    "An unexpected error occurred during the scheduled short-term fetch for regionCode {}: {}",
                    regionCode,
                    e.message,
                    e
                )
            }
        }

        logger.info("Scheduled task fetchShortTermForecastsTask completed at {} (Korea Time)",
            LocalDateTime.now(KOREA_ZONE_ID).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    /**
     * 매일 오전 6시 10분 (한국 시간 기준)에 실행되어,
     * 각 대표 도시에 대한 중기예보(D+3 ~ D+10)를 기상청으로부터 가져와 DB에 저장/업데이트합니다.
     * 중기예보는 보통 06시 발표 자료를 활용합니다.
     */
    @Scheduled(cron = "0 10 6 * * ?", zone = "Asia/Seoul") // 매일 오전 6시 10분 (기존 유지)
    fun fetchMidTermForecastsTask() { // 메소드명 명확화 (기존: fetchDailyWeeklyForecastsTask)
        val executionTime = LocalDateTime.now(KOREA_ZONE_ID)
        logger.info(
            "Executing scheduled task: fetchMidTermForecastsTask at {} (Korea Time)",
            executionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        // 중기예보 API 호출 기준 시각은 당일 오전 6시로 설정
        val baseDateTimeForMidTermApi = LocalDate.now(KOREA_ZONE_ID).atTime(LocalTime.of(6, 0))

        val cityCodesToFetch = weatherService.representativeCityTempCodes
        if (cityCodesToFetch.isEmpty()) {
            logger.warn("No representative city codes found in WeatherService. Skipping mid-term forecast fetch.")
            return
        }

        logger.info("Targeting KMA Mid-Term API announcement time (tmFc): {}", baseDateTimeForMidTermApi.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")))
        logger.info("Fetching MID-TERM forecasts for the following city temp_reg_ids: {}", cityCodesToFetch)

        cityCodesToFetch.forEach { cityTempRegId -> // cityTempRegId는 RegionCodeConfig의 키 (기온 예보 구역 코드)
            try {
                weatherService.fetchAndStoreWeeklyForecastsForCity(cityTempRegId, baseDateTimeForMidTermApi)
            } catch (e: Exception) {
                logger.error(
                    "An unexpected error occurred during the scheduled mid-term fetch for city temp_reg_id {}: {}",
                    cityTempRegId,
                    e.message,
                    e
                )
            }
        }

        logger.info("Scheduled task fetchMidTermForecastsTask completed at {} (Korea Time)",
            LocalDateTime.now(KOREA_ZONE_ID).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }
}