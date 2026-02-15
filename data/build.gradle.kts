import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

val keystoreProps = Properties()
var keystoreFile = rootProject.file("keystore.local.properties")

if (!keystoreFile.exists()) {
    keystoreFile = rootProject.file("keystore.properties")
}

if (keystoreFile.exists()) {
    keystoreProps.load(FileInputStream(keystoreFile))
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

fun getSecureProperty(name: String, defaultValue: String = ""): String {
    return project.findProperty(name)?.toString()
        ?: keystoreProps.getProperty(name)
        ?: localProps.getProperty(name)
        ?: System.getenv(name)
        ?: defaultValue
}

android {
    namespace = "com.example.pipelineapp.networking"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DATA_DOG_CLIENT_TOKEN", "\"${getSecureProperty("DATA_DOG_CLIENT_TOKEN")}\"")
        buildConfigField("String", "DATA_DOG_APPLICATION_ID", "\"${getSecureProperty("DATA_DOG_APPLICATION_ID")}\"")
        buildConfigField("String", "AWS_ACCESS_KEY", "\"${getSecureProperty("AWS_ACCESS_KEY")}\"")
        buildConfigField("String", "AWS_SECRET_KEY", "\"${getSecureProperty("AWS_SECRET_KEY")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "ENVIRONMENT", "\"${getSecureProperty("ENVIRONMENT", "DEBUG")}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ENVIRONMENT", "\"${getSecureProperty("ENVIRONMENT", "PROD")}\"")
        }
    }

    flavorDimensions += "buildEnvironment"
    productFlavors {
        create("Dev") {
            dimension = "buildEnvironment"
            buildConfigField("String", "ENVIRONMENT", "\"DEV\"")
            buildConfigField("String", "SOCKET_URL", "\"${getServerUrl("DEV", "SOCKET")}\"")
            buildConfigField("String", "API_INTERNAL_URL", "\"${getServerUrl("DEV", "API_INTERNAL")}\"")
        }

        create("Qa") {
            dimension = "buildEnvironment"
            buildConfigField("String", "ENVIRONMENT", "\"QA\"")
            buildConfigField("String", "SOCKET_URL", "\"${getServerUrl("QA", "SOCKET")}\"")
            buildConfigField("String", "API_INTERNAL_URL", "\"${getServerUrl("QA", "API_INTERNAL")}\"")
        }

        create("Sandbox") {
            dimension = "buildEnvironment"
            buildConfigField("String", "ENVIRONMENT", "\"SANDBOX\"")
            buildConfigField("String", "SOCKET_URL", "\"${getServerUrl("SANDBOX", "SOCKET")}\"")
            buildConfigField("String", "API_INTERNAL_URL", "\"${getServerUrl("SANDBOX", "API_INTERNAL")}\"")
        }

        val carriers = listOf(
            "Verizon",
            "TMobile",
            "O2",
            "Boost",
            "VZW",
            "NZ",
            "US",
            "Optus",
            "Tesco",
            "RW",
            "Team",
            "Xfinity",
            "Digicel",
            "Nadiya",
            "Amtel",
            "aio"
        )

        carriers.forEach { carrier ->
            val cleanCarrierName = carrier.replace("_", "").lowercase()
            create("${cleanCarrierName}Staging") {
                dimension = "buildEnvironment"
                resValue("string", "app_name", "$carrier - STG")
                buildConfigField("String", "SERVER_URL", "\"${getSecureProperty("${carrier.uppercase()}_STAGING_SERVER_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"STAGING\"")
                buildConfigField("String", "SOCKET_URL", "\"${getServerUrl("STAGING", "SOCKET")}\"")
                buildConfigField("String", "API_INTERNAL_URL", "\"${getServerUrl("STAGING", "API_INTERNAL")}\"")

                if (project.properties.containsKey("${carrier.uppercase()}_STAGING_STORE_ID}")) {
                    buildConfigField("String", "STORE_ID", "\"${getSecureProperty("${carrier.uppercase()}_STAGING_STORE_ID")}\"")
                }
                if (project.properties.containsKey("${carrier.uppercase()}_STAGING_STORE_PASSWORD")) {
                    buildConfigField("String", "STORE_PASSWORD", "\"${getSecureProperty("${carrier.uppercase()}_STAGING_STORE_PASSWORD")}\"")
                }
            }

            create("${cleanCarrierName}Production") {
                dimension = "buildEnvironment"
                resValue("string", "app_name", "$carrier - PROD")
                buildConfigField("String", "SERVER_URL", "\"${getSecureProperty("${carrier.uppercase()}_PROD_SERVER_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"PROD\"")
                buildConfigField("String", "SOCKET_URL", "\"${getServerUrl("PROD", "SOCKET")}\"")
                buildConfigField("String", "API_INTERNAL_URL", "\"${getServerUrl("PROD", "API_INTERNAL")}\"")

                if (project.properties.containsKey("${carrier.uppercase()}_PROD_STORE_ID")) {
                    buildConfigField("String", "STORE_ID", "\"${getSecureProperty("${carrier.uppercase()}_PROD_STORE_ID")}\"")
                }
                if (project.properties.containsKey("${carrier.uppercase()}_PROD_STORE_PASSWORD")) {
                    buildConfigField("String", "STORE_PASSWORD", "\"${getSecureProperty("${carrier.uppercase()}_PROD_STORE_PASSWORD")}\"")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }


    androidComponents {
        beforeVariants(selector().all()) { variantBuilder ->
            val buildType = variantBuilder.buildType
            val flavorName = variantBuilder.flavorName

            val isProductionFlavor: Boolean = flavorName?.contains("Production", ignoreCase = true) ?: false
            val isStagingFlavor: Boolean = flavorName?.contains("Staging", ignoreCase = true) ?: false
            val isInternalFlavor: Boolean =
                flavorName?.contains("Dev", ignoreCase = true) == true ||
                        flavorName?.contains("Qa", ignoreCase = true) == true ||
                        flavorName?.contains("Sandbox", ignoreCase = true) == true

            if (isProductionFlavor && buildType =="debug") {
                variantBuilder.enable = false
                return@beforeVariants
            } else if (isInternalFlavor && buildType == "release") {
                variantBuilder.enable = false
                return@beforeVariants
            } else if (isStagingFlavor && buildType == "release") {
                variantBuilder.enable = false
                return@beforeVariants
            }
        }
    }
}

fun getServerUrl(environmentType: String, type: String): String {
    val socketBaseUrl = getSecureProperty("${type}_${environmentType}_URL")
    return socketBaseUrl
}

dependencies {
    implementation(project(":domain"))

    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.datadoghq:dd-sdk-android-logs:2.19.0")
    implementation("com.datadoghq:dd-sdk-android-okhttp:2.19.0")
    implementation("com.datadoghq:dd-sdk-android-logs:2.19.0")
    implementation("com.datadoghq:dd-sdk-android-trace:2.19.0")

    implementation("software.amazon.awssdk:secretsmanager:2.20.124")
    implementation("com.amazonaws:aws-android-sdk-core:2.45.0")
    implementation("software.amazon.awssdk:url-connection-client:2.17+")
    implementation("org.greenrobot:eventbus:3.3.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("io.socket:socket.io-client:2.0.0")
    implementation("org.apache.commons:commons-io:1.3.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}