package com.company.rest.api.service

import com.company.rest.api.config.AppleWeatherProperties // 임포트 추가
import com.company.rest.api.config.LocationDetails
import com.company.rest.api.dto.*
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
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import java.io.FileInputStream
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
    private val objectMapper: ObjectMapper,
    private val appleWeatherProperties: AppleWeatherProperties // AppleWeatherProperties 주입
) {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)

    private var cachedJwt: String? = null
    private var jwtExpiryTime: LocalDateTime? = null

    private fun generateWeatherKitJwt(): String {
        if (cachedJwt != null && jwtExpiryTime != null && LocalDateTime.now().isBefore(jwtExpiryTime)) {
            return cachedJwt!!
        }
        logger.info("Generating new WeatherKit JWT...")
        // 프로퍼티 사용
        val privateKeyInputStream = FileInputStream(appleWeatherProperties.keyPath)
        val privateKeyPem = privateKeyInputStream.readBytes().toString(StandardCharsets.UTF_8)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyPem)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey: PrivateKey = keyFactory.generatePrivate(keySpec)
        val now = Date()
        val expiry = Date(now.time + 3600 * 1000)

        // 프로퍼티 사용
        val jwt = Jwts.builder()
            .header()
            .keyId(appleWeatherProperties.keyId)
            .add("id", "${appleWeatherProperties.teamId}.${appleWeatherProperties.serviceId}")
            .and()
            .issuer(appleWeatherProperties.teamId)
            .subject(appleWeatherProperties.serviceId)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
        cachedJwt = jwt
        jwtExpiryTime = expiry.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime().minusMinutes(1)
        return jwt
    }

    // ... (이하 fetchAndStore... 및 getWeatherForLocation 메소드는 동일)
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
                    // 1. 위도/경도로 기존 데이터를 조회합니다.
                    val existingWeather = currentWeatherRepository.findByLatitudeAndLongitude(location.latitude, location.longitude)

                    if (existingWeather.isPresent) {
                        // 2. 데이터가 있으면, 기존 엔티티의 값을 업데이트합니다.
                        val weatherToUpdate = existingWeather.get()
                        weatherToUpdate.measuredAt = LocalDateTime.parse(weatherDto.asOf, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        weatherToUpdate.temperature = weatherDto.temperature
                        weatherToUpdate.apparentTemperature = weatherDto.temperatureApparent
                        weatherToUpdate.conditionCode = weatherDto.conditionCode
                        weatherToUpdate.humidity = weatherDto.humidity
                        weatherToUpdate.windSpeed = weatherDto.wind?.speed ?: 0.0
                        weatherToUpdate.uvIndex = weatherDto.uvIndex
                        currentWeatherRepository.save(weatherToUpdate) // save는 UPDATE 쿼리를 실행합니다.
                        logger.info("Successfully updated current weather for ${location.cityName}")
                    } else {
                        // 3. 데이터가 없으면, 새로운 엔티티를 생성합니다.
                        val newCurrentWeather = CurrentWeather(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            measuredAt = LocalDateTime.parse(weatherDto.asOf, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            temperature = weatherDto.temperature,
                            apparentTemperature = weatherDto.temperatureApparent,
                            conditionCode = weatherDto.conditionCode,
                            humidity = weatherDto.humidity,
                            windSpeed = weatherDto.wind?.speed ?: 0.0,
                            uvIndex = weatherDto.uvIndex
                        )
                        currentWeatherRepository.save(newCurrentWeather) // save는 INSERT 쿼리를 실행합니다.
                        logger.info("Successfully inserted current weather for ${location.cityName}")
                    }
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
                    // 1. 위도/경도로 기존 데이터를 조회합니다.
                    val existingForecast = hourlyForecastRepository.findByLatitudeAndLongitude(location.latitude, location.longitude)

                    if (existingForecast.isPresent) {
                        // 2. 데이터가 있으면, 기존 엔티티의 값을 업데이트합니다.
                        val forecastToUpdate = existingForecast.get()
                        forecastToUpdate.summary = forecastDto.summary?.firstOrNull()?.condition
                        forecastToUpdate.minutesJson = objectMapper.writeValueAsString(forecastDto.minutes)
                        forecastToUpdate.forecastExpireTime = LocalDateTime.parse(
                            forecastDto.metadata.expireTime,
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                        )
                        hourlyForecastRepository.save(forecastToUpdate) // save는 UPDATE 쿼리를 실행합니다.
                        logger.info("Successfully updated hourly forecast for ${location.cityName}")
                    } else {
                        // 3. 데이터가 없으면, 새로운 엔티티를 생성합니다.
                        val newHourlyForecast = HourlyForecast(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            summary = forecastDto.summary?.firstOrNull()?.condition,
                            minutesJson = objectMapper.writeValueAsString(forecastDto.minutes),
                            forecastExpireTime = LocalDateTime.parse(
                                forecastDto.metadata.expireTime,
                                DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            )
                        )
                        hourlyForecastRepository.save(newHourlyForecast) // save는 INSERT 쿼리를 실행합니다.
                        logger.info("Successfully inserted hourly forecast for ${location.cityName}")
                    }
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
                        // 여기서는 여러 날짜의 예보를 한 번에 다루므로,
                        // 해당 지역의 모든 예보를 지우고 새로 넣는 방식이 더 간단하고 효율적입니다.
                        // 이 메소드는 그대로 두어도 괜찮습니다. DeleteAll -> SaveAll 로직이 유효합니다.
                        dailyWeatherForecastRepository.deleteAllByLatitudeAndLongitude(
                            location.latitude,
                            location.longitude
                        )
                        val newForecastEntities = dailyForecastsDto.map { dayDto ->
                            DailyWeatherForecast(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                forecastDate = LocalDate.parse(
                                    dayDto.forecastStart,
                                    DateTimeFormatter.ISO_OFFSET_DATE_TIME
                                ),
                                minTemp = dayDto.temperatureMin,
                                maxTemp = dayDto.temperatureMax,
                                weatherAm = dayDto.daytimeForecast?.conditionCode ?: dayDto.conditionCode,
                                weatherPm = dayDto.overnightForecast?.conditionCode ?: dayDto.conditionCode,
                                rainProb = dayDto.precipitationChance,
                                humidity = dayDto.humidity,
                                windSpeed = dayDto.wind?.speed,
                                uvIndex = dayDto.uvIndex?.value,
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

    fun getWeatherForLocation(latitude: Double, longitude: Double): WeatherResponseDto {
        logger.info("Fetching weather data from DB for lat: {}, lon: {}", latitude, longitude)

        val currentWeatherEntity = currentWeatherRepository.findByLatitudeAndLongitude(latitude, longitude)
        val hourlyForecastEntity = hourlyForecastRepository.findByLatitudeAndLongitude(latitude, longitude)
        val dailyForecastEntities =
            dailyWeatherForecastRepository.findByLatitudeAndLongitudeOrderByForecastDateAsc(latitude, longitude)

        val currentWeatherDto = currentWeatherEntity.map { CurrentWeatherResponseDto.fromEntity(it) }.orElse(null)
        val hourlyForecastDto =
            hourlyForecastEntity.map { HourlyForecastResponseDto.fromEntity(it, objectMapper) }.orElse(null)
        val dailyForecastsDto = dailyForecastEntities.map { DailyWeatherForecastResponseDto.fromEntity(it) }

        return WeatherResponseDto(
            currentWeather = currentWeatherDto,
            hourlyForecast = hourlyForecastDto,
            dailyForecast = dailyForecastsDto
        )
    }
}