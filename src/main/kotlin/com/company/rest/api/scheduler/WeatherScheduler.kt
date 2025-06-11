package com.company.rest.api.scheduler

import com.company.rest.api.service.WeatherService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class WeatherScheduler(
    private val weatherService: WeatherService
) {
    private val logger = LoggerFactory.getLogger(WeatherScheduler::class.java)

    /**
     * 매 5분마다 실행되어 현재 날씨 정보를 업데이트합니다.
     * (cron: 0분부터 시작하여 5분 간격으로, 예: 0, 5, 10, 15...)
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    fun fetchCurrentWeatherTask() {
        logger.info(
            "Scheduled task 'fetchCurrentWeatherTask' triggered at {}",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        weatherService.fetchAndStoreCurrentWeather()
    }

    /**
     * 매 시간 정각에 실행되어 1시간 예보를 업데이트합니다.
     */
    @Scheduled(cron = "0 0 * * * ?")
    fun fetchHourlyForecastsTask() {
        logger.info(
            "Scheduled task 'fetchHourlyForecastsTask' triggered at {}",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        weatherService.fetchAndStoreHourlyForecasts()
    }

    /**
     * 매일 자정(00:00)에 실행되어 일일 예보를 업데이트합니다.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    fun fetchDailyForecastsTask() {
        logger.info(
            "Scheduled task 'fetchDailyForecastsTask' triggered at {}",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        weatherService.fetchAndStoreDailyForecasts()
    }
}