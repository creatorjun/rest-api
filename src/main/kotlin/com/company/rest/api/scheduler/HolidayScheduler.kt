package com.company.rest.api.scheduler

import com.company.rest.api.service.HolidayService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class HolidayScheduler(
    private val holidayService: HolidayService
) {
    private val logger = LoggerFactory.getLogger(HolidayScheduler::class.java)

    /**
     * 애플리케이션 시작 시, 올해와 내년의 공휴일 정보를 동기화합니다.
     */
    @PostConstruct
    fun initHolidayData() {
        logger.info("Initial holiday data synchronization starting...")
        val currentYear = LocalDate.now().year
        try {
            holidayService.syncHolidaysForYear(currentYear)
            holidayService.syncHolidaysForYear(currentYear + 1)
        } catch (e: Exception) {
            logger.error("Failed to sync holidays on application startup.", e)
        }
    }

    /**
     * 매일 새벽 3시에 실행되어, 올해와 내년의 공휴일 정보를 DB에 업데이트합니다.
     * 이를 통해 연중에 발표되는 임시 공휴일 등을 반영할 수 있습니다.
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Seoul")
    fun syncHolidayDataDaily() {
        logger.info("Daily holiday data synchronization triggered.")
        val currentYear = LocalDate.now().year
        try {
            holidayService.syncHolidaysForYear(currentYear)
        } catch (e: Exception) {
            logger.error("Failed to sync holidays for current year ({}) in scheduled task.", currentYear, e)
        }
        try {
            holidayService.syncHolidaysForYear(currentYear + 1)
        } catch (e: Exception) {
            logger.error("Failed to sync holidays for next year ({}) in scheduled task.", currentYear + 1, e)
        }
    }
}