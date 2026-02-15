import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.google.devtools.ksp")
}

// ----------------------
// Secure Properties Loader
// ----------------------

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

ktlint {
    version.set("1.2.1")
    android.set(true)
    outputColorName.set("RED")
    baseline.set(file(".ktlint-baseline.xml"))
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        exclude("**/kotlin/**")
    }
}

android {
    namespace = "com.example.pipelineapp"
    compileSdk = 35

    // ----------------------
    // Signing Configurations
    // ----------------------

    signingConfigs {

        getByName("debug") {
            storeFile = file("daisy.jks")
            storePassword = "pinecone"
            keyAlias = "key0"
            keyPassword = "pinecone"
        }

        create("release") {

            val injectedStoreFile =
                findProperty("android.injected.signing.store.file") as String?

            if (injectedStoreFile != null) {

                storeFile = file(injectedStoreFile)
                storePassword = findProperty("android.injected.signing.store.password") as String?
                keyAlias = findProperty("android.injected.signing.key.alias") as String?
                keyPassword = findProperty("android.injected.signing.key.password") as String?

            } else if (findProperty("MYAPP_RELEASE_STORE_FILE") != null) {

                storeFile = file(getSecureProperty("MYAPP_RELEASE_STORE_FILE"))
                storePassword = getSecureProperty("MYAPP_RELEASE_STORE_PASSWORD")
                keyAlias = getSecureProperty("MYAPP_RELEASE_KEY_ALIAS")
                keyPassword = getSecureProperty("MYAPP_RELEASE_KEY_PASSWORD")

            } else {

                storeFile = file("debug.keystore")
                storePassword = "pinecone"
                keyAlias = "key0"
                keyPassword = "pinecone"
            }
        }
    }

    packagingOptions {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
            )
            merges += "META-INF/io.netty.versions.properties"
        }
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
    }

    defaultConfig {
        applicationId = "com.example.pipelineapp"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // -------- DataDog + AWS -------------
        buildConfigField("String", "DATA_DOG_CLIENT_TOKEN", "\"${getSecureProperty("DATA_DOG_CLIENT_TOKEN")}\"")
        buildConfigField("String", "DATA_DOG_APPLICATION_ID", "\"${getSecureProperty("DATA_DOG_APPLICATION_ID")}\"")
        buildConfigField("String", "AWS_ACCESS_KEY", "\"${getSecureProperty("AWS_ACCESS_KEY")}\"")
        buildConfigField("String", "AWS_SECRET_KEY", "\"${getSecureProperty("AWS_SECRET_KEY")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "ENVIRONMENT", "\"${getSecureProperty("ENVIRONMENT", "DEBUG")}\"")
            signingConfig = signingConfigs.getByName("debug")

        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ENVIRONMENT", "\"${getSecureProperty("ENVIRONMENT", "PROD")}\"")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions += "buildEnvironment"

    productFlavors {

        create("Dev") {
            dimension = "buildEnvironment"
            buildConfigField("String", "ENVIRONMENT", "\"DEV\"")
            buildConfigField("String", "SOCKET_URL", "\"${getSecureProperty("SOCKET_DEV_URL")}\"")
            buildConfigField("String", "API_INTERNAL_URL", "\"${getSecureProperty("API_INTERNAL_DEV_URL")}\"")
        }

        create("Qa") {
            dimension = "buildEnvironment"
            buildConfigField("String", "ENVIRONMENT", "\"QA\"")
            buildConfigField("String", "SOCKET_URL", "\"${getSecureProperty("SOCKET_QA_URL")}\"")
            buildConfigField("String", "API_INTERNAL_URL", "\"${getSecureProperty("API_INTERNAL_QA_URL")}\"")
        }

        create("Sandbox") {
            dimension = "buildEnvironment"
            buildConfigField("String", "ENVIRONMENT", "\"SANDBOX\"")
            buildConfigField("String", "SOCKET_URL", "\"${getSecureProperty("SOCKET_SANDBOX_URL")}\"")
            buildConfigField("String", "API_INTERNAL_URL", "\"${getSecureProperty("API_INTERNAL_SANDBOX_URL")}\"")
        }

        val carriers = listOf(
            "Verizon", "TMobile", "O2", "Boost", "VZW",
            "NZ", "US", "Optus", "Tesco", "RW",
            "Team", "Xfinity", "Digicel", "Nadiya", "Amtel", "aio"
        )

        carriers.forEach { carrier ->
            val cleanName = carrier.replace("_", "").lowercase()

            create("${cleanName}Staging") {
                dimension = "buildEnvironment"
                resValue("string", "app_name", "$carrier - STG")
                buildConfigField("String", "SERVER_URL", "\"${getSecureProperty("${carrier.uppercase()}_STAGING_SERVER_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"STAGING\"")
            }

            create("${cleanName}Production") {
                dimension = "buildEnvironment"
                resValue("string", "app_name", "$carrier - PROD")
                buildConfigField("String", "SERVER_URL", "\"${getSecureProperty("${carrier.uppercase()}_PROD_SERVER_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"PRODUCTION\"")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidComponents {
        beforeVariants(selector().all()) { variantBuilder ->
            val buildType = variantBuilder.buildType
            val flavorName = variantBuilder.flavorName

            val isProductionFlavor = flavorName?.contains("Production", true) == true
            val isStagingFlavor = flavorName?.contains("Staging", true) == true
            val isInternalFlavor =
                flavorName?.contains("Dev", true) == true ||
                        flavorName?.contains("Qa", true) == true ||
                        flavorName?.contains("Sandbox", true) == true

            if (isProductionFlavor && buildType == "debug") variantBuilder.enable = false
            if (isInternalFlavor && buildType == "release") variantBuilder.enable = false
            if (isStagingFlavor && buildType == "release") variantBuilder.enable = false
        }
    }
}

dependencies {

    implementation(project(":data"))
    implementation(project(":domain"))

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    implementation("com.airbnb.android:lottie:6.1.0")

    implementation(platform("com.google.firebase:firebase-bom:32.2.0"))
    implementation("com.google.firebase:firebase-messaging")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.window:window:1.5.0")
    implementation("androidx.window:window-core:1.5.0")

    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.greenrobot:eventbus:3.3.1")
    implementation("io.socket:socket.io-client:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // DataDog
    implementation("com.datadoghq:dd-sdk-android-logs:2.19.0")
    implementation("com.datadoghq:dd-sdk-android-okhttp:2.19.0")
    implementation("com.datadoghq:dd-sdk-android-trace:2.19.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    implementation("androidx.hilt:hilt-work:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
