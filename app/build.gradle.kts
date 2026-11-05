plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "ir.namyranservice.customer"
    compileSdk = 36
    defaultConfig {
        applicationId = "ir.namyranservice.customer.stage"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.4.5-stage"
        buildConfigField("String", "API_BASE_URL", "\"https://stage.namyranservice.ir/wp-json/\"")
        buildConfigField("boolean", "BACKEND_ENABLED", "true")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")

    implementation(composeBom)
    implementation("com.google.android.gms:play-services-auth-api-phone:17.4.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

