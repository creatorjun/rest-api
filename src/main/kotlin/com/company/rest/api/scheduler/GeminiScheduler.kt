package com.company.rest.api.scheduler

import com.company.rest.api.service.GeminiService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class GeminiScheduler( // 클래스 이름은 유지 (Gemini 관련 스케줄러임을 명시)
    private val geminiService: GeminiService
) {
    private val logger = LoggerFactory.getLogger(GeminiScheduler::class.java)

    /**
     * 매일 오전 9시에 실행되어 오늘의 띠별 운세(행운)를 제미나이로부터 가져와 DB에 저장합니다. (한국 시간 기준)
     */
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Seoul") // 매일 오전 9시
    fun fetchDailyLuckTask() { // 메소드명 변경
        val currentTime = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

        logger.info(
            "Executing scheduled task: fetchDailyLuckTask for date {} at {} (Korea Time)",
            today,
            currentTime
        ) // 로그 메시지 변경

        try {
            // GeminiService의 변경된 메소드 호출
            geminiService.fetchAndStoreDailyLuck(today) // 메소드 호출 변경
            logger.info("Scheduled task fetchDailyLuckTask completed for date: {}", today) // 로그 메시지 변경
        } catch (e: Exception) {
            // GeminiService 내부에서 예외를 로깅하고 DB 상태를 업데이트하지만,
            // 스케줄러 레벨에서도 작업 실패를 인지하고 로깅할 수 있습니다.
            logger.error(
                "An unexpected error occurred during the scheduled fetchDailyLuckTask for date {}: {}",
                today,
                e.message,
                e
            ) // 로그 메시지 변경
        }
    }
}