package com.company.rest.api.service

import com.company.rest.api.dto.GeminiLuckResponseDto // DTO 이름 변경
import com.company.rest.api.dto.ZodiacLuckDataDto // DTO 이름 변경
import com.company.rest.api.entity.DailyLuckLog // 엔티티 이름 변경
import com.company.rest.api.entity.LuckParsingStatus // 열거형 이름 변경
import com.company.rest.api.entity.ZodiacSignLuck // 엔티티 이름 변경
import com.company.rest.api.repository.DailyLuckLogRepository // Repository 이름 변경
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.Client // 이전 Gemini API 클라이언트 유지
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
    private val dailyLuckLogRepository: DailyLuckLogRepository, // Repository 주입 변경
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(GeminiService::class.java)

    @Value("\${gemini.model.name}")
    private lateinit var modelName: String

    private lateinit var client: Client // 이전 Gemini API 클라이언트 유지

    // Gemini API에 전달하는 질문은 "운세"라는 단어를 포함하는 것이 자연스러울 수 있으므로,
    // 이 부분은 사용자님의 판단에 따라 "운세"를 유지하거나 "행운" 등으로 변경할 수 있습니다.
    // 현재는 "운세"를 유지하겠습니다.
    private val dailyLuckQuestion: String = """이제부터 넌 세계 최고의 점성술사 / 사주풀이 / 타로 마스터 전문가야. 별자리 사주풀이 운세 띠별운세 타로카드 명리학 동양철학 및 점성술에 관한 모든 사항을 완벽하게 숙지해서 다음 물음에 답변해줘 1950년 이후 출생한 사람들에대한 띠별 운세를 각 띠별로 묶어서 오늘의 운세를 다음과 같은 형식을 갖는 json 파일로 줘

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
}""" // 필드명 변경 dailyHoroscopeQuestion -> dailyLuckQuestion

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
            logger.info("Sending question to Gemini API. Model: {}, Question (first 100 chars): '{}'", modelName, question.take(100))
            val response = client.models.generateContent(modelName, question, null) // 이전 Gemini API 호출 방식 유지
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
    fun fetchAndStoreDailyLuck(requestDate: LocalDate) { // 메소드명 변경
        var logEntry = dailyLuckLogRepository.findByRequestDate(requestDate).orElseGet {
            DailyLuckLog( // 엔티티 이름 변경
                requestDate = requestDate,
                questionAsked = dailyLuckQuestion, // 질문 필드명 변경
                parsingStatus = LuckParsingStatus.PENDING, // 열거형 이름 변경
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        }

        if (logEntry.parsingStatus == LuckParsingStatus.SUCCESS && logEntry.id != null) { // 열거형 이름 변경
            logger.info("Daily luck for {} has already been successfully processed. Skipping.", requestDate) // 로그 메시지 변경
            return
        }

        logEntry.parsingStatus = LuckParsingStatus.PENDING // 열거형 이름 변경
        logEntry.updatedAt = LocalDateTime.now()
        logEntry = dailyLuckLogRepository.save(logEntry)

        val rawResponse = askGemini(dailyLuckQuestion) // 질문 필드명 변경
        logEntry.rawResponse = rawResponse
        logEntry.updatedAt = LocalDateTime.now()

        if (rawResponse == null || rawResponse.isBlank() || rawResponse.startsWith("Gemini API 호출 중 오류가 발생했습니다")) {
            logger.error("Failed to get valid response from Gemini for date: {}. Response: {}", requestDate, rawResponse)
            logEntry.parsingStatus = LuckParsingStatus.GEMINI_ERROR // 열거형 이름 변경
            dailyLuckLogRepository.save(logEntry)
            return
        }

        try {
            val jsonResponseString = extractJsonString(rawResponse)
            if (jsonResponseString == null) {
                logger.error("Failed to extract JSON from Gemini response for date: {}. Raw response: {}", requestDate, rawResponse.take(500))
                logEntry.parsingStatus = LuckParsingStatus.PARSING_FAILED // 열거형 이름 변경
                dailyLuckLogRepository.save(logEntry)
                return
            }

            val luckResponseDto = objectMapper.readValue(jsonResponseString, GeminiLuckResponseDto::class.java) // DTO 이름 변경

            if (luckResponseDto.zodiacLucks == null || luckResponseDto.zodiacLucks.isEmpty()) { // 필드명 변경
                logger.warn("Gemini response parsed, but no luck data found for date: {}", requestDate) // 로그 메시지 변경
                logEntry.parsingStatus = LuckParsingStatus.NO_DATA // 열거형 이름 변경
                dailyLuckLogRepository.save(logEntry)
                return
            }

            logEntry.luckEntries.clear() // 필드명 변경

            for (detailDto in luckResponseDto.zodiacLucks) { // 필드명 변경
                val applicableYearsJsonString = try {
                    objectMapper.writeValueAsString(detailDto.applicableYears ?: emptyList<String>())
                } catch (e: JsonProcessingException) {
                    logger.warn("Failed to serialize applicableYears for DTO: $detailDto, defaulting to empty array. Error: ${e.message}")
                    "[]"
                }

                val zodiacSignLuckEntry = ZodiacSignLuck( // 엔티티 이름 변경
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
                logEntry.addZodiacSignLuck(zodiacSignLuckEntry) // 메소드명 변경
            }
            logEntry.parsingStatus = LuckParsingStatus.SUCCESS // 열거형 이름 변경
            logger.info("Successfully fetched, parsed, and mapped daily luck entries for date: {}", requestDate) // 로그 메시지 변경

        } catch (e: JsonProcessingException) {
            logger.error("Failed to parse JSON response from Gemini for date: {}. Error: {}. Response: {}", requestDate, e.message, rawResponse.take(500))
            logEntry.parsingStatus = LuckParsingStatus.PARSING_FAILED // 열거형 이름 변경
        } catch (e: Exception) {
            logger.error("An unexpected error occurred during luck processing for date: {}. Error: {}", requestDate, e.message, e) // 로그 메시지 변경
            logEntry.parsingStatus = LuckParsingStatus.PARSING_FAILED // 열거형 이름 변경
        } finally {
            logEntry.updatedAt = LocalDateTime.now()
            dailyLuckLogRepository.save(logEntry)
        }
    }

    private fun extractJsonString(rawResponse: String): String? {
        val markdownJsonPattern = "```json\\s*([\\s\\S]+?)\\s*```".toRegex()
        var match = markdownJsonPattern.find(rawResponse)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val firstBrace = rawResponse.indexOf('{')
        val lastBrace = rawResponse.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val potentialJson = rawResponse.substring(firstBrace, lastBrace + 1)
            if (isValidJson(potentialJson)) return potentialJson
        }

        val firstBracket = rawResponse.indexOf('[')
        val lastBracket = rawResponse.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            val potentialJsonArray = rawResponse.substring(firstBracket, lastBracket + 1)
            // Gemini 응답이 { "띠별운세": [...] } 형태이므로, 최상위가 배열인 경우는 직접 파싱 대상으로 적합하지 않을 수 있음.
            // 하지만, 만약 API가 때때로 배열 자체를 반환한다면 이 검사도 유용할 수 있습니다.
            // 여기서는 일반적으로 JSON 객체를 기대하므로, 이 부분은 주석 처리하거나 제거해도 무방합니다.
            // if (isValidJson(potentialJsonArray)) return potentialJsonArray
        }


        logger.warn("Could not reliably extract JSON object from the raw response. Raw (first 300 chars): ${rawResponse.take(300)}")
        return null // JSON 객체를 찾지 못한 경우 null 반환
    }

    private fun isValidJson(jsonString: String): Boolean {
        return try {
            objectMapper.readTree(jsonString)
            true
        } catch (e: JsonProcessingException) {
            false
        }
    }

    /**
     * 특정 날짜와 띠 이름에 해당하는 운세 정보를 조회합니다.
     * @param requestDate 조회할 날짜
     * @param zodiacName 조회할 띠 이름 (예: "쥐띠")
     * @return ZodiacLuckDataDto? 해당 띠의 운세 정보 DTO, 없거나 준비되지 않았으면 null
     */
    @Transactional(readOnly = true)
    fun getLuckForZodiacSign(requestDate: LocalDate, zodiacName: String): ZodiacLuckDataDto? { // 메소드명 및 반환타입 변경
        logger.debug("Attempting to fetch luck for date: {} and zodiac: {}", requestDate, zodiacName) // 로그 메시지 변경

        val logEntryOpt = dailyLuckLogRepository.findByRequestDate(requestDate)
        if (logEntryOpt.isEmpty) {
            logger.warn("No luck log found for date: {}", requestDate) // 로그 메시지 변경
            return null
        }

        val logEntry = logEntryOpt.get()
        if (logEntry.parsingStatus != LuckParsingStatus.SUCCESS) { // 열거형 이름 변경
            logger.warn("Luck log for date: {} is not yet successfully processed. Status: {}", requestDate, logEntry.parsingStatus) // 로그 메시지 변경
            return null
        }

        val specificLuckEntity = logEntry.luckEntries.find { it.zodiacName.equals(zodiacName, ignoreCase = true) } // 필드명 변경

        return if (specificLuckEntity != null) {
            logger.info("Found luck for date: {} and zodiac: {}", requestDate, zodiacName) // 로그 메시지 변경
            ZodiacLuckDataDto.fromEntity(specificLuckEntity, requestDate, objectMapper) // DTO 이름 변경
        } else {
            logger.warn("Luck data for zodiac: {} not found within the log for date: {}", zodiacName, requestDate) // 로그 메시지 변경
            null
        }
    }
}