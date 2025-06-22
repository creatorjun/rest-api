package com.company.rest.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

data class LocationDetails(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val airkoreaRegionName: String
)

@Configuration
@ConfigurationProperties(prefix = "weather")
class LocationConfig {

    lateinit var locations: List<LocationDetails>

    @Bean
    fun locationDetailsList(): List<LocationDetails> {
        return locations
    }
}