package com.company.rest.api.service

import com.company.rest.api.dto.GeminiLuckResponseDto
import com.company.rest.api.dto.ZodiacLuckDataDto
import com.company.rest.api.entity.DailyLuckLog
import com.company.rest.api.entity.LuckParsingStatus
import com.company.rest.api.entity.ZodiacSignLuck
import com.company.rest.api.repository.DailyLuckLogRepository
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.Client
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class GeminiService(
    @Value("\${gemini.api.key}") private val apiKey: String,
    private val dailyLuckLogRepository: DailyLuckLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(GeminiService::class.java)

    @Value("\${gemini.model.name}")
    private lateinit var modelName: String

    private lateinit var client: Client

    private val dailyLuckQuestion: String =
        """이제부터 넌 세계 최고의 점성술사 / 사주풀이 / 타로 마스터 전문가야. 별자리 사주풀이 운세 띠별운세 타로카드 명리학 동양철학 및 점성술에 관한 모든 사항을 완벽하게 숙지해서 다음 물음에 답변해줘 단, 너무 띠와 연관지어 생각하지 말고, 매번 다른 답변을 생성하는데 주력해야해. 1950년 이후 출생한 사람들에대한 띠별 운세를 각 띠별로 묶어서 오늘의 운세를 다음과 같은 형식을 갖는 json 파일로 줘

{
  "띠별운세": [
    {
      "띠": "쥐띠",
      "해당년도": ["1960년", "1972년", "1984년", "1996년", "2008년", "2020년"],
      "오늘의운세": "매우 긍정적인 하루가 될 것입니다. 새로운 기회가 찾아오며, 주변 사람들과의 관계도 원만할 것으로 보입니다.",
      "금전운": "예상치 못한 수입이 생길 수 있으나, 충동적인 지출은 피하는 것이 좋습니다. 전반적으로 안정적입니다.",
      "애정운": "연인과의 관계가 더욱 깊어지거나, 새로운 인연을 만날 가능성이 높습니다. 솔직한 마음을 표현하세요.",
      "건강운": "가벼운 스트레칭이나 산책으로 컨디션을 조절하는 것이 좋습니다. 전반적으로 양호합니다.",
      "행운의숫자": 7,
      "행운의색상": "파란색",
      "오늘의조언": "긍정적인 마음으로 하루를 시작하고, 찾아오는 기회를 놓치지 마세요."
    }
  ]
}"""

    @PostConstruct
    fun init() {
        if (apiKey.isBlank()) {
            logger.error("Gemini API key is not configured. Please set 'gemini.api.key' in application.properties.")
            return
        }
        try {
            client = Client.builder().apiKey(apiKey).build()
            logger.info("Gemini Client initialized successfully.")
        } catch (e: Exception) {
            logger.error("Failed to initialize Gemini Client: ${e.message}", e)
        }
    }

    fun askGemini(question: String): String? {
        if (!this::client.isInitialized) {
            logger.error("Gemini Client is not initialized. API key might be missing or initialization failed.")
            return "Gemini 클라이언트가 초기화되지 않았습니다. API 키를 확인하거나 초기화 오류를 확인하세요."
        }

        return try {
            logger.info(
                "Sending question to Gemini API. Model: {}, Question (first 100 chars): '{}'",
                modelName,
                question.take(100)
            )
            val response = client.models.generateContent(modelName, question, null)
            val responseText = response.text()

            if (responseText != null && responseText.isNotBlank()) {
                logger.info("Received response from Gemini API (first 200 chars): '{}'", responseText.take(200))
            } else {
                logger.warn("Gemini API returned a null, empty, or blank response.")
            }
            responseText
        } catch (e: Exception) {
            logger.error("Error while calling Gemini API with model {}: {}", modelName, e.message, e)
            "Gemini API 호출 중 오류가 발생했습니다: ${e.message}"
        }
    }

    @Transactional
    fun fetchAndStoreDailyLuck(requestDate: LocalDate) {
        var logEntry = dailyLuckLogRepository.findByRequestDate(requestDate).orElseGet {
            DailyLuckLog(
                requestDate = requestDate,
                questionAsked = dailyLuckQuestion,
                parsingStatus = LuckParsingStatus.PENDING,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        }

        if (logEntry.parsingStatus == LuckParsingStatus.SUCCESS) {
            logger.info(
                "Daily luck for {} has already been successfully processed. Skipping.",
                requestDate
            )
            return
        }

        logEntry.parsingStatus = LuckParsingStatus.PENDING
        logEntry.updatedAt = LocalDateTime.now()
        logEntry = dailyLuckLogRepository.save(logEntry)

        val rawResponse = askGemini(dailyLuckQuestion)
        logEntry.rawResponse = rawResponse
        logEntry.updatedAt = LocalDateTime.now()

        if (rawResponse == null || rawResponse.isBlank() || rawResponse.startsWith("Gemini API 호출 중 오류가 발생했습니다")) {
            logger.error(
                "Failed to get valid response from Gemini for date: {}. Response: {}",
                requestDate,
                rawResponse
            )
            logEntry.parsingStatus = LuckParsingStatus.GEMINI_ERROR
            dailyLuckLogRepository.save(logEntry)
            return
        }

        try {
            val jsonResponseString = extractJsonString(rawResponse)
            if (jsonResponseString == null) {
                logger.error(
                    "Failed to extract JSON from Gemini response for date: {}. Raw response: {}",
                    requestDate,
                    rawResponse.take(500)
                )
                logEntry.parsingStatus = LuckParsingStatus.PARSING_FAILED
                dailyLuckLogRepository.save(logEntry)
                return
            }

            val luckResponseDto =
                objectMapper.readValue(jsonResponseString, GeminiLuckResponseDto::class.java)

            if (luckResponseDto.zodiacLucks == null || luckResponseDto.zodiacLucks.isEmpty()) {
                logger.warn("Gemini response parsed, but no luck data found for date: {}", requestDate)
                logEntry.parsingStatus = LuckParsingStatus.NO_DATA
                dailyLuckLogRepository.save(logEntry)
                return
            }

            logEntry.luckEntries.clear()

            for (detailDto in luckResponseDto.zodiacLucks) {
                val applicableYearsJsonString = try {
                    objectMapper.writeValueAsString(detailDto.applicableYears ?: emptyList<String>())
                } catch (e: JsonProcessingException) {
                    logger.warn("Failed to serialize applicableYears for DTO: $detailDto, defaulting to empty array. Error: ${e.message}")
                    "[]"
                }

                val zodiacSignLuckEntry = ZodiacSignLuck(
                    zodiacName = detailDto.zodiacName ?: "알 수 없는 띠",
                    applicableYearsJson = applicableYearsJsonString,
                    overallLuck = detailDto.overallLuck,
                    financialLuck = detailDto.financialLuck,
                    loveLuck = detailDto.loveLuck,
                    healthLuck = detailDto.healthLuck,
                    luckyNumber = detailDto.luckyNumber,
                    luckyColor = detailDto.luckyColor,
                    advice = detailDto.advice
                )
                logEntry.addZodiacSignLuck(zodiacSignLuckEntry)
            }
            logEntry.parsingStatus = LuckParsingStatus.SUCCESS
            logger.info(
                "Successfully fetched, parsed, and mapped daily luck entries for date: {}",
                requestDate
            )

        } catch (e: JsonProcessingException) {
            logger.error(
                "Failed to parse JSON response from Gemini for date: {}. Error: {}. Response: {}",
                requestDate,
                e.message,
                rawResponse.take(500)
            )
            logEntry.parsingStatus = LuckParsingStatus.PARSING_FAILED
        } catch (e: Exception) {
            logger.error(
                "An unexpected error occurred during luck processing for date: {}. Error: {}",
                requestDate,
                e.message,
                e
            )
            logEntry.parsingStatus = LuckParsingStatus.PARSING_FAILED
        } finally {
            logEntry.updatedAt = LocalDateTime.now()
            dailyLuckLogRepository.save(logEntry)
        }
    }

    private fun extractJsonString(rawResponse: String): String? {
        val markdownJsonPattern = "```json\\s*([\\s\\S]+?)\\s*```".toRegex()
        val match = markdownJsonPattern.find(rawResponse)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val firstBrace = rawResponse.indexOf('{')
        val lastBrace = rawResponse.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val potentialJson = rawResponse.substring(firstBrace, lastBrace + 1)
            if (isValidJson(potentialJson)) return potentialJson
        }

        logger.warn(
            "Could not reliably extract JSON object from the raw response. Raw (first 300 chars): ${
                rawResponse.take(
                    300
                )
            }"
        )
        return null
    }

    private fun isValidJson(jsonString: String): Boolean {
        return try {
            objectMapper.readTree(jsonString)
            true
        } catch (e: JsonProcessingException) {
            false
        }
    }

    // --- 이 메소드들이 수정 및 추가되었습니다 ---

    /**
     * 특정 날짜의 모든 띠별 운세 정보를 조회합니다.
     * @param requestDate 조회할 날짜
     * @return List<ZodiacLuckDataDto> 해당 날짜의 모든 운세 정보 DTO 리스트, 없거나 준비되지 않았으면 빈 리스트
     */
    @Transactional(readOnly = true)
    fun getAllLucksForDate(requestDate: LocalDate): List<ZodiacLuckDataDto> {
        logger.debug("Attempting to fetch all lucks for date: {}", requestDate)

        val logEntryOpt = dailyLuckLogRepository.findByRequestDate(requestDate)
        if (logEntryOpt.isEmpty) {
            logger.warn("No luck log found for date: {}", requestDate)
            return emptyList()
        }

        val logEntry = logEntryOpt.get()
        if (logEntry.parsingStatus != LuckParsingStatus.SUCCESS) {
            logger.warn(
                "Luck log for date: {} is not yet successfully processed. Status: {}",
                requestDate,
                logEntry.parsingStatus
            )
            return emptyList()
        }

        return logEntry.luckEntries.map { luckEntity ->
            ZodiacLuckDataDto.fromEntity(luckEntity, requestDate, objectMapper)
        }
    }

    /**
     * 특정 날짜와 띠 이름에 해당하는 운세 정보를 조회합니다. (기존 메소드는 유지)
     */
    @Transactional(readOnly = true)
    fun getLuckForZodiacSign(requestDate: LocalDate, zodiacName: String): ZodiacLuckDataDto? {
        logger.debug("Attempting to fetch luck for date: {} and zodiac: {}", requestDate, zodiacName)

        val logEntryOpt = dailyLuckLogRepository.findByRequestDate(requestDate)
        if (logEntryOpt.isEmpty) {
            logger.warn("No luck log found for date: {}", requestDate)
            return null
        }

        val logEntry = logEntryOpt.get()
        if (logEntry.parsingStatus != LuckParsingStatus.SUCCESS) {
            logger.warn(
                "Luck log for date: {} is not yet successfully processed. Status: {}",
                requestDate,
                logEntry.parsingStatus
            )
            return null
        }

        val specificLuckEntity =
            logEntry.luckEntries.find { it.zodiacName.equals(zodiacName, ignoreCase = true) }

        return if (specificLuckEntity != null) {
            logger.info("Found luck for date: {} and zodiac: {}", requestDate, zodiacName)
            ZodiacLuckDataDto.fromEntity(specificLuckEntity, requestDate, objectMapper)
        } else {
            logger.warn(
                "Luck data for zodiac: {} not found within the log for date: {}",
                zodiacName,
                requestDate
            )
            null
        }
    }
}