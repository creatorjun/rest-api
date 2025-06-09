package com.company.rest.api.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.IOException

@Configuration
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    // application.properties에서 키 파일의 '이름'을 읽어오도록 수정
    @Value("\${google.fcm.key}")
    private lateinit var firebaseSdkKeyName: String

    @PostConstruct
    fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                // resources 폴더 내의 파일을 안전하게 읽어오기 위해 ClassPathResource 사용
                val resource = ClassPathResource(firebaseSdkKeyName)

                // 파일이 존재하는지 확인
                if (!resource.exists()) {
                    throw IOException("Firebase Admin SDK key file not found in classpath: $firebaseSdkKeyName")
                }

                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(resource.inputStream))
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("Firebase Admin SDK initialized successfully with key: {}", firebaseSdkKeyName)
            } else {
                logger.info("Firebase Admin SDK already initialized.")
            }
        } catch (e: IOException) {
            logger.error("Error initializing Firebase Admin SDK with key '{}': {}", firebaseSdkKeyName, e.message, e)
            throw RuntimeException(
                "Failed to initialize Firebase Admin SDK. Please check the service account key file '$firebaseSdkKeyName' in the resources folder.",
                e
            )
        }
    }
}