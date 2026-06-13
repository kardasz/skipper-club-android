import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

val releaseSigningPropertiesFile = rootProject.file("release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseSigningConfig = releaseSigningPropertiesFile.isFile

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val googleMapsApiKey = providers.gradleProperty("GOOGLE_MAPS_API_KEY")
    .orElse(providers.environmentVariable("GOOGLE_MAPS_API_KEY"))
    .orElse(localProperties.getProperty("GOOGLE_MAPS_API_KEY") ?: "")
val appVersionCode = providers.gradleProperty("VERSION_CODE")
    .orElse(providers.environmentVariable("VERSION_CODE"))
    .map(String::toInt)
    .orElse(2)
val appVersionName = providers.gradleProperty("VERSION_NAME")
    .orElse(providers.environmentVariable("VERSION_NAME"))
    .orElse("0.2.0")

android {
    namespace = "app.skipperclub"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.skipperclub"
        minSdk = 26
        targetSdk {
            version = release(36)
        }
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"https://api.skipperclub.app\"")
        buildConfigField("String", "TURNSTILE_URL", "\"https://skipperclub.app/turnstile\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${googleMapsApiKey.get()}\"")
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey.get()
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            // Places SDK pulls in Glide's NotificationTarget, but this app does not post system notifications.
            "NotificationPermission",
            "OldTargetApi",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.tink.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)
    implementation(libs.places)
    implementation(libs.socketio.client) {
        // Android ships org.json in the platform; the transitive JVM copy only conflicts.
        exclude(group = "org.json", module = "json")
    }
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.okhttp.logging.interceptor)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val debugCoverageExclusions = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*ComposableSingletons*.*",
    "**/*Preview*.*",
    "**/*PreviewParameterProvider*.*",
)

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

fun JacocoReport.configureDebugCoverageReport() {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(debugCoverageExclusions)
            },
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
                exclude(debugCoverageExclusions)
            },
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
                exclude(debugCoverageExclusions)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/java"))
}

tasks.register<JacocoReport>("debugUnitTestCoverage") {
    group = "verification"
    description = "Generates JaCoCo HTML and XML coverage reports for debug unit tests."
    dependsOn("testDebugUnitTest")
    configureDebugCoverageReport()
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )
}

tasks.register<JacocoReport>("debugCombinedTestCoverage") {
    group = "verification"
    description = "Generates JaCoCo HTML and XML coverage reports for debug unit and connected UI tests."
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")
    configureDebugCoverageReport()
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
            )
        },
    )
}
