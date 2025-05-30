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
     * 매일 오전 6시 10분 (한국 시간 기준)에 실행되어,
     * 각 대표 도시에 대한 주간 일기예보를 기상청으로부터 가져와 DB에 저장/업데이트합니다.
     * 기상청 중기예보는 보통 06시에 발표되므로, 해당 발표 자료를 가져옵니다.
     */
    @Scheduled(cron = "0 10 6 * * ?", zone = "Asia/Seoul") // 매일 오전 6시 10분
    fun fetchDailyWeeklyForecastsTask() {
        val executionTime = LocalDateTime.now(KOREA_ZONE_ID)
        logger.info(
            "Executing scheduled task: fetchDailyWeeklyForecastsTask at {} (Korea Time)",
            executionTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        // API 호출 기준 시각은 당일 오전 6시로 설정
        val baseDateTimeForKMA = LocalDate.now(KOREA_ZONE_ID).atTime(LocalTime.of(6, 0))

        // WeatherService에 정의된 대표 도시 목록 가져오기
        val cityCodesToFetch = weatherService.representativeCityTempCodes

        if (cityCodesToFetch.isEmpty()) {
            logger.warn("No representative city codes found in WeatherService. Skipping forecast fetch.")
            return
        }

        logger.info("Targeting KMA announcement time (tmFc): {}", baseDateTimeForKMA.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")))
        logger.info("Fetching forecasts for the following city temp_reg_ids: {}", cityCodesToFetch)

        cityCodesToFetch.forEach { cityTempRegId ->
            try {
                // 각 도시별로 예보를 가져와 저장하는 서비스 메소드 호출
                weatherService.fetchAndStoreWeeklyForecastsForCity(cityTempRegId, baseDateTimeForKMA)
                // 개별 도시에 대한 로그는 WeatherService 내부에서 처리됩니다.
            } catch (e: Exception) {
                // WeatherService 내부에서 예외를 로깅하고 DB 상태를 업데이트하지만,
                // 스케줄러 레벨에서도 특정 도시 처리 실패를 인지하고 로깅할 수 있습니다.
                logger.error(
                    "An unexpected error occurred during the scheduled fetch for city temp_reg_id {}: {}",
                    cityTempRegId,
                    e.message,
                    e
                )
            }
        }

        logger.info("Scheduled task fetchDailyWeeklyForecastsTask completed at {} (Korea Time)",
            LocalDateTime.now(KOREA_ZONE_ID).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }
}