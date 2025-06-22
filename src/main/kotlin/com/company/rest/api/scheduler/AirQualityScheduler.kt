package com.company.rest.api.scheduler

import com.company.rest.api.service.AirQualityService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class AirQualityScheduler(
    private val airQualityService: AirQualityService
) {
    private val logger = LoggerFactory.getLogger(AirQualityScheduler::class.java)
    private val KOREA_ZONE_ID = ZoneId.of("Asia/Seoul")

    /**
     * 애플리케이션 시작 시 오늘의 대기질 예보 정보를 한 번 가져옵니다.
     */
    @PostConstruct
    fun initAirQualityFetch() {
        val today = LocalDate.now(KOREA_ZONE_ID)
        logger.info("Initial air quality data synchronization starting for date: {} on application startup...", today)
        try {
            airQualityService.fetchDailyForecasts(today)
            logger.info("Initial air quality data synchronization finished successfully.")
        } catch (e: Exception) {
            logger.error("Failed to sync air quality data on application startup.", e)
        }
    }

    /**
     * 매일 4회(5:10, 11:10, 17:10, 23:10) 에어코리아 API를 호출하여
     * 오늘의 대기질 예보 정보를 가져와 캐시에 저장합니다. (한국 시간 기준)
     * 에어코리아 예보 발표 시간: 5시, 11시, 17시, 23시
     */
    @Scheduled(cron = "0 10 5,11,17,23 * * ?", zone = "Asia/Seoul")
    fun fetchAirQualityForecastsTask() {
        val today = LocalDate.now(KOREA_ZONE_ID)
        logger.info("Scheduled task 'fetchAirQualityForecastsTask' triggered for date: {}", today)
        try {
            airQualityService.fetchDailyForecasts(today)
        } catch (e: Exception) {
            logger.error("Error during scheduled AirQuality forecast fetch for date {}: {}", today, e.message, e)
        }
    }
}