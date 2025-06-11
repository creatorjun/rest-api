package com.company.rest.api.service

import com.company.rest.api.config.LocationDetails
import com.company.rest.api.dto.WeatherKitResponse
import com.company.rest.api.entity.CurrentWeather
import com.company.rest.api.entity.DailyWeatherForecast
import com.company.rest.api.entity.HourlyForecast
import com.company.rest.api.repository.CurrentWeatherRepository
import com.company.rest.api.repository.DailyWeatherForecastRepository
import com.company.rest.api.repository.HourlyForecastRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class WeatherService(
    @Qualifier("weatherKitWebClient") private val webClient: WebClient,
    private val currentWeatherRepository: CurrentWeatherRepository,
    private val hourlyForecastRepository: HourlyForecastRepository,
    private val dailyWeatherForecastRepository: DailyWeatherForecastRepository,
    private val locations: List<LocationDetails>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)

    @Value("\${apple.weather.team-id}")
    private lateinit var teamId: String

    @Value("\${apple.weather.key-id}")
    private lateinit var keyId: String

    @Value("\${apple.weather.service-id}")
    private lateinit var serviceId: String

    @Value("\${apple.weather.key-path}")
    private lateinit var keyPath: String

    private var cachedJwt: String? = null
    private var jwtExpiryTime: LocalDateTime? = null

    private fun generateWeatherKitJwt(): String {
        if (cachedJwt != null && jwtExpiryTime != null && LocalDateTime.now().isBefore(jwtExpiryTime)) {
            return cachedJwt!!
        }
        logger.info("Generating new WeatherKit JWT...")
        val privateKeyFile = ClassPathResource(keyPath)
        val privateKeyPem = privateKeyFile.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyPem)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey: PrivateKey = keyFactory.generatePrivate(keySpec)

        val now = Date()
        val expiry = Date(now.time + 3600 * 1000)

        // --- Deprecated 경고를 해결하기 위해 최신 jjwt API 스타일로 변경 ---
        val jwt = Jwts.builder()
            .header()
            .keyId(keyId)
            .add("id", "$teamId.$serviceId")
            .and()
            .issuer(teamId)
            .subject(serviceId)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
        // --- 여기까지 ---

        cachedJwt = jwt
        jwtExpiryTime = expiry.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime().minusMinutes(1)
        return jwt
    }

    // ... 나머지 메소드들은 동일 ...
    @Transactional
    fun fetchAndStoreCurrentWeather() {
        logger.info("Starting to fetch and store current weather for all locations.")
        val jwt = generateWeatherKitJwt()
        locations.forEach { location ->
            try {
                val response = webClient.get()
                    .uri("/api/v1/weather/ko/${location.latitude}/${location.longitude}?dataSets=currentWeather")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                    .retrieve()
                    .bodyToMono(WeatherKitResponse::class.java)
                    .block()
                response?.currentWeather?.let { weatherDto ->
                    currentWeatherRepository.deleteByLatitudeAndLongitude(location.latitude, location.longitude)
                    val newCurrentWeather = CurrentWeather(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        measuredAt = LocalDateTime.parse(weatherDto.asOf, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        temperature = weatherDto.temperature,
                        apparentTemperature = weatherDto.temperatureApparent,
                        conditionCode = weatherDto.conditionCode,
                        humidity = weatherDto.humidity,
                        windSpeed = weatherDto.wind.speed,
                        uvIndex = weatherDto.uvIndex
                    )
                    currentWeatherRepository.save(newCurrentWeather)
                    logger.info("Successfully processed current weather for ${location.cityName}")
                }
            } catch (e: Exception) {
                logger.error("Failed to process current weather for ${location.cityName}", e)
            }
        }
    }

    @Transactional
    fun fetchAndStoreHourlyForecasts() {
        logger.info("Starting to fetch and store hourly forecasts for all locations.")
        val jwt = generateWeatherKitJwt()
        locations.forEach { location ->
            try {
                val response = webClient.get()
                    .uri("/api/v1/weather/ko/${location.latitude}/${location.longitude}?dataSets=forecastNextHour")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                    .retrieve()
                    .bodyToMono(WeatherKitResponse::class.java)
                    .block()
                response?.forecastNextHour?.let { forecastDto ->
                    hourlyForecastRepository.deleteByLatitudeAndLongitude(location.latitude, location.longitude)
                    val newHourlyForecast = HourlyForecast(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        summary = forecastDto.summary?.firstOrNull()?.condition,
                        minutesJson = objectMapper.writeValueAsString(forecastDto.minutes),
                        forecastExpireTime = LocalDateTime.parse(forecastDto.metadata.expireTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    )
                    hourlyForecastRepository.save(newHourlyForecast)
                    logger.info("Successfully processed hourly forecast for ${location.cityName}")
                }
            } catch (e: Exception) {
                logger.error("Failed to process hourly forecast for ${location.cityName}", e)
            }
        }
    }

    @Transactional
    fun fetchAndStoreDailyForecasts() {
        logger.info("Starting to fetch and store daily forecasts for all locations.")
        val jwt = generateWeatherKitJwt()
        val dataSets = "forecastDaily"
        locations.forEach { location ->
            try {
                val response = webClient.get()
                    .uri("/api/v1/weather/ko/${location.latitude}/${location.longitude}?dataSets=$dataSets")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $jwt")
                    .retrieve()
                    .bodyToMono(WeatherKitResponse::class.java)
                    .block()
                response?.forecastDaily?.days?.let { dailyForecastsDto ->
                    if (dailyForecastsDto.isNotEmpty()) {
                        dailyWeatherForecastRepository.deleteAllByLatitudeAndLongitude(location.latitude, location.longitude)
                        val newForecastEntities = dailyForecastsDto.map { dayDto ->
                            DailyWeatherForecast(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                forecastDate = LocalDate.parse(dayDto.forecastStart, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                minTemp = dayDto.temperatureMin,
                                maxTemp = dayDto.temperatureMax,
                                weatherAm = dayDto.daytimeForecast?.conditionCode ?: dayDto.conditionCode,
                                weatherPm = dayDto.overnightForecast?.conditionCode ?: dayDto.conditionCode,
                                rainProb = dayDto.precipitationChance,
                                humidity = dayDto.humidity,
                                windSpeed = dayDto.wind.speed,
                                uvIndex = dayDto.uvIndex.value,
                                sunrise = dayDto.sunrise,
                                sunset = dayDto.sunset
                            )
                        }
                        dailyWeatherForecastRepository.saveAll(newForecastEntities)
                        logger.info("Successfully processed daily forecast for ${location.cityName}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to process daily forecast for ${location.cityName}", e)
            }
        }
    }

    fun getCombinedWeather(latitude: Double, longitude: Double): Any {
        logger.info("Fetching combined weather data for lat: {}, lon: {}", latitude, longitude)
        val currentWeather = currentWeatherRepository.findByLatitudeAndLongitude(latitude, longitude)
        val hourlyForecast = hourlyForecastRepository.findByLatitudeAndLongitude(latitude, longitude)
        val dailyForecasts = dailyWeatherForecastRepository.findByLatitudeAndLongitudeOrderByForecastDateAsc(latitude, longitude)
        return mapOf(
            "current" to currentWeather,
            "hourly" to hourlyForecast,
            "daily" to dailyForecasts
        )
    }
}