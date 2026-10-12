import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Konfiguracja podpisu release.
 * Zrodla (w kolejnosci priorytetu):
 *  1. zmienne srodowiskowe TERMINOWNIK_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD (CI),
 *  2. plik keystore.properties w korzeniu projektu (lokalnie, poza git - patrz .gitignore).
 * Brak konfiguracji = brak podpisu release; zadanie `requireReleaseSigning` wtedy przerywa build,
 * zeby nigdy nie wypuscic niepodpisanego APK jako "release".
 */
val signingProps = Properties().apply {
    val envStore = System.getenv("TERMINOWNIK_STORE_FILE")
    val propsFile = rootProject.file("keystore.properties")
    if (!envStore.isNullOrBlank()) {
        setProperty("storeFile", envStore)
        setProperty("storePassword", System.getenv("TERMINOWNIK_STORE_PASSWORD").orEmpty())
        setProperty("keyAlias", System.getenv("TERMINOWNIK_KEY_ALIAS").orEmpty())
        setProperty("keyPassword", System.getenv("TERMINOWNIK_KEY_PASSWORD").orEmpty())
    } else if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = signingProps.getProperty("storeFile").orEmpty().isNotBlank() &&
    signingProps.getProperty("storePassword").orEmpty().isNotBlank() &&
    signingProps.getProperty("keyAlias").orEmpty().isNotBlank() &&
    signingProps.getProperty("keyPassword").orEmpty().isNotBlank() &&
    file(signingProps.getProperty("storeFile")).let { it.isAbsolute || it.exists() }

android {
    namespace = "pl.terminownik.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.terminownik.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Dystrybucja poza sklepem: nie dokładamy do APK bloku metadanych zależnosci Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        // lint uruchamiamy jawnie w CI (`:app:lint`), zeby nie blokowal samego assembleRelease.
        checkReleaseBuilds = false
        sarifReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }
}

// AGP 9.3 ma wbudowane wsparcie dla Kotlina (bez pluginu org.jetbrains.kotlin.android);
// jvmTarget jest dopasowywany do compileOptions ponizej - tak samo robi android/nowinandroid.

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.incremental", "true")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("requireReleaseSigning") {
    group = "release"
    description = "Przerywa build release, jesli nie ma kompletnego klucza podpisu."
    val signed = hasReleaseSigning
    doLast {
        if (!signed) {
            throw GradleException(
                """
                Brak kompletnej konfiguracji podpisu release.
                Ustaw zmienne srodowiskowe TERMINOWNIK_STORE_FILE, TERMINOWNIK_STORE_PASSWORD,
                TERMINOWNIK_KEY_ALIAS, TERMINOWNIK_KEY_PASSWORD
                albo utworz plik keystore.properties (skrypt: scripts/make-keystore.sh).
                Nie budujemy niepodpisanego APK jako wydania release.
                """.trimIndent()
            )
        }
        logger.lifecycle("Podpis release: OK (${file(signingProps.getProperty("storeFile")).name})")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("requireReleaseSigning")
}
