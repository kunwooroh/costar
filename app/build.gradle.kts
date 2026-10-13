plugins {
    id("com.android.application")
}

android {
    namespace = "com.voicetodo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voicetodo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 개인용 설치를 위해 디버그 키로 서명 (Play 스토어 배포 시 별도 키로 교체)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // 개인용 앱: 린트 경고 때문에 릴리스 빌드가 멈추지 않도록
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 외부 라이브러리 없이 안드로이드 기본 API만 사용합니다.
dependencies {
}
