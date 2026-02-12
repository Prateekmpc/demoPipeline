plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.google.devtools.ksp")
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

    packagingOptions {
        resources {
            excludes +=
                setOf(
                    "META-INF/INDEX.LIST",
                    "META-INF/DEPENDENCIES",
                )
            merges += "META-INF/io.netty.versions.properties"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    tasks.named<io.gitlab.arturbosch.detekt.Detekt>("detekt") {
        description = "Runs static code analysis for Kotlin files."
        buildUponDefaultConfig = true
        baseline.set(project.file("$rootDir/detekt-baseline.xml"))
        setSource(files("src/main/kotlin", "src/test/kotlin"))
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        reports {
            html {
                required.set(true)
                outputLocation.set(file("$buildDir/reports/detekt/detekt.html"))
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.pipelineapp"
        minSdk = 24
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DATA_DOG_CLIENT_TOKEN", "\"${getProjectProperty("DATA_DOG_CLIENT_TOKEN")}\"")
        buildConfigField("String", "DATA_DOG_APPLICATION_ID", "\"${getProjectProperty("DATA_DOG_APPLICATION_ID")}\"")
        buildConfigField("String", "AWS_ACCESS_KEY", "\"${getProjectProperty("AWS_ACCESS_KEY")}\"")
        buildConfigField("String", "AWS_SECRET_KEY", "\"${getProjectProperty("AWS_SECRET_KEY")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "ENVIRONMENT", "\"${getProjectProperty("ENVIRONMENT", "DEBUG")}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ENVIRONMENT", "\"${getProjectProperty("ENVIRONMENT", "PROD")}\"")
            signingConfig = signingConfigs.getByName("debug")
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
                buildConfigField("String", "SERVER_URL", "\"${getProjectProperty("${carrier.uppercase()}_STAGING_SERVER_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"STAGING\"")
                buildConfigField("String", "SOCKET_URL", "\"${getServerUrl("STAGING", "SOCKET")}\"")
                buildConfigField("String", "API_INTERNAL_URL", "\"${getServerUrl("STAGING", "API_INTERNAL")}\"")

                if (project.properties.containsKey("${carrier.uppercase()}_STAGING_STORE_ID")) {
                    buildConfigField("String", "STORE_ID", "\"${getProjectProperty("${carrier.uppercase()}_STAGING_STORE_ID")}\"")
                }
                if (project.properties.containsKey("${carrier.uppercase()}_STAGING_STORE_PASSWORD")) {
                    buildConfigField("String", "STORE_PASSWORD", "\"${getProjectProperty("${carrier.uppercase()}_STAGING_STORE_PASSWORD")}\"")
                }
            }

            create("${cleanCarrierName}Production") {
                dimension = "buildEnvironment"
                resValue("string", "app_name", "$carrier - PROD")
                buildConfigField("String", "SERVER_URL", "\"${getProjectProperty("${carrier.uppercase()}_PROD_SERVER_URL")}\"")
                buildConfigField("String", "ENVIRONMENT", "\"PRODUCTION\"")
                buildConfigField("String", "SOCKET_URL", "\"${getServerUrl("PROD", "SOCKET")}\"")
                buildConfigField("String", "API_INTERNAL_URL", "\"${getServerUrl("PROD", "API_INTERNAL")}\"")

                if (project.properties.containsKey("${carrier.uppercase()}_PROD_STORE_ID")) {
                    buildConfigField("String", "STORE_ID", "\"${getProjectProperty("${carrier.uppercase()}_PROD_STORE_ID")}\"")
                }
                if (project.properties.containsKey("${carrier.uppercase()}_PROD_STORE_PASSWORD")) {
                    buildConfigField("String", "STORE_PASSWORD", "\"${getProjectProperty("${carrier.uppercase()}_PROD_STORE_PASSWORD")}\"")
                }
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

    dataBinding {
        enable = true
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

            if (isProductionFlavor && buildType == "debug") {
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

fun getProjectProperty(propertyName: String, defaultValue: String = ""): String {
    return if (project.properties.containsKey(propertyName)) {
        project.properties[propertyName].toString()
    } else {
        println("WARNING: Property '$propertyName' not found in gradle.properties. Using default value: '$defaultValue'")
        defaultValue
    }
}

fun getServerUrl(environmentType: String, type: String): String {
    val socketBaseUrl = getProjectProperty("${type}_${environmentType}_URL")
    return socketBaseUrl
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    implementation("com.airbnb.android:lottie:6.1.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation(platform("com.google.firebase:firebase-bom:32.2.0")) // Firebase BoM
    implementation("com.google.firebase:firebase-messaging") // FCM
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // WindowManager for foldable support
    implementation("androidx.window:window:1.5.0")
    implementation("androidx.window:window-core:1.5.0")

    // Testing for foldable support
    testImplementation("androidx.window:window-testing:1.5.0")
    androidTestImplementation("androidx.window:window-testing:1.5.0")

    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.greenrobot:eventbus:3.3.1")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    implementation("io.socket:socket.io-client:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.56.2")
    implementation("androidx.hilt:hilt-work:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
}