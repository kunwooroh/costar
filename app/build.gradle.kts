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
        versionCode = 2
        versionName = "1.1"
    }

    // 개인 설치용 고정 서명 키: 빌드마다 같은 키라서 새 버전을 기존 앱 위에 바로 업데이트 설치 가능
    // (Play 스토어 배포 시에는 비공개 키로 교체하세요)
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "voicetodo"
            keyAlias = "voicetodo"
            keyPassword = "voicetodo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
