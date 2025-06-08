package com.company.rest.api.config

import java.io.IOException
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import jakarta.annotation.PostConstruct
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration


@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)
    @Value("\${google.fcm.key}")
    private lateinit var firebaseSdkPath: String
    @PostConstruct
    fun initializeFirebase() {
        try {
            // FirebaseApp이 이미 초기화되지 않았을 경우에만 초기화 진행
            if (FirebaseApp.getApps().isEmpty()) {
                val serviceAccount = FileInputStream(firebaseSdkPath)

                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("Firebase Admin SDK initialized successfully.")
            } else {
                logger.info("Firebase Admin SDK already initialized.")
            }
        } catch (e: IOException) {
            logger.error("Error initializing Firebase Admin SDK: ${e.message}", e)
            // SDK 초기화 실패는 심각한 문제일 수 있으므로, 필요에 따라 애플리케이션 실행을 중단시킬 수도 있습니다.
            throw RuntimeException("Failed to initialize Firebase Admin SDK. Please check the service account key file and path.", e)
        }
    }
}