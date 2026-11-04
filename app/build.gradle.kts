plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "ir.namyranservice.customer"
    compileSdk = 36
    defaultConfig {
        applicationId = "ir.namyranservice.customer.stage"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.2-stage"
        buildConfigField("String", "API_BASE_URL", "\"https://stage.namyranservice.ir/wp-json/\"")
        buildConfigField("boolean", "BACKEND_ENABLED", "false")
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
val composeBom = platform("androidx.compose:compose-bom:2025.12.00")    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
