package com.company.rest.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class RestApiApplication


fun main(args: Array<String>) {
    runApplication<RestApiApplication>(*args)
}