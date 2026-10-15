import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Única fuente de versión: versionName `mayor.menor.parche`; versionCode se deriva y siempre crece con ella.
val appVersion = "0.1.0"
val (vMajor, vMinor, vPatch) = appVersion.split(".").map { it.toInt() }

// Firma de release: keystore.properties (fuera de git) con storeFile, storePassword, keyAlias y keyPassword.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "com.ryoku.transit"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.ryoku.transit"
        minSdk = 26
        targetSdk = 36
        versionCode = vMajor * 10_000 + vMinor * 100 + vPatch
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        keystoreProps?.let { p ->
            create("release") {
                fun prop(k: String) = p.getProperty(k)
                storeFile = rootProject.file(prop("storeFile"))
                storePassword = prop("storePassword")
                keyAlias = prop("keyAlias")
                keyPassword = prop("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // Sin keystore.properties el release queda sin firmar y no se puede instalar: tools/release.sh lo comprueba.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += "db" }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
