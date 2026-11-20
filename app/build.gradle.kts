plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Stage is the default. Production must be selected explicitly at build time.
val targetEnvironment = providers.gradleProperty("namyranEnvironment").orElse("stage").get()
require(targetEnvironment in setOf("stage", "production")) { "Unsupported Namyran environment" }
val productionTarget = targetEnvironment == "production"
val targetHost = if (productionTarget) "namyranservice.ir" else "stage.namyranservice.ir"

android {
    namespace = "ir.namyranservice.customer"
    compileSdk = 36
    defaultConfig {
        applicationId = if (productionTarget) "ir.namyranservice.customer" else "ir.namyranservice.customer.stage"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "0.5.2-" + targetEnvironment
        buildConfigField("String", "API_BASE_URL", "\"https://$targetHost/wp-json/\"")
        buildConfigField("String", "API_HOST", "\"$targetHost\"")
        buildConfigField("String", "ENVIRONMENT", "\"$targetEnvironment\"")
        manifestPlaceholders["appLabel"] = if (productionTarget) "نامیران سرویس" else "نامیران سرویس آزمایشی"
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

    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
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

