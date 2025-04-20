import java.util.Properties

val isNormalBuild: Boolean by rootProject.extra
val currentVersionCode = currentVersion.code

plugins {
    alias(libs.plugins.agp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.androidx.safeargs)
}

if (isNormalBuild) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    compileSdk = 36
    namespace = "com.uniqtech.musicplayer"

    defaultConfig {
        minSdk = 26
        targetSdk = 35

        applicationId = namespace
        versionCode = 1000010
        versionName = currentVersion.name
        check(versionCode == currentVersionCode)
    }

    flavorDimensions += "version"
    productFlavors {
        create("normal") {
            dimension = "version"
        }
        create("fdroid") {
            dimension = "version"
        }
    }

    val signingProperties = getProperties("keystore.properties")
    val releaseSigning = if (signingProperties != null) {
        signingConfigs.create("release") {
            keyAlias = signingProperties.property("keyAlias")
            keyPassword = signingProperties.property("keyPassword")
            storePassword = signingProperties.property("storePassword")
            storeFile = file(signingProperties.property("storeFile"))
        }
    } else {
        signingConfigs.getByName("debug")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " DEBUG"
            signingConfig = releaseSigning
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    packaging {
        resources {
            excludes += listOf("META-INF/LICENSE", "META-INF/NOTICE", "META-INF/java.properties")
        }
    }
    lint {
        abortOnError = true
        warning += listOf("ImpliedQuantity", "Instantiatable", "MissingQuantity", "MissingTranslation")
    }
    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Music-${defaultConfig.versionName}-${name}.apk"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

fun getProperties(fileName: String): Properties? {
    val file = rootProject.file(fileName)
    return if (file.exists()) {
        Properties().also { properties ->
            file.inputStream().use { properties.load(it) }
        }
    } else null
}

fun Properties.property(key: String) =
    this.getProperty(key) ?: "$key missing"

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    // Firebase BoM
    "normalImplementation"(platform(libs.firebase.bom))
    "normalImplementation"(libs.firebase.crashlytics)

    // Google/JetPack
    //https://developer.android.com/jetpack/androidx/versions
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.appcompat)
    implementation(libs.fragment.ktx)

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.common.java8)

    implementation(libs.navigation.common.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.runtime.ktx)
    implementation(libs.navigation.ui.ktx)

    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.media)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.material.components)

    implementation(libs.niervisualizer) {
        exclude(group = "com.android.support")
    }
    implementation(libs.fastscroll)
    implementation(libs.fadingedgelayout)
    implementation(libs.advrecyclerview) {
        isTransitive = true
    }
    implementation(libs.customactivityoncrash)
    implementation(libs.versioncompare)

    implementation(libs.markdown.core)
    implementation(libs.markdown.html)
    implementation(libs.markdown.glide)
    implementation(libs.markdown.linkify)

    implementation(libs.bundles.ktor)
    implementation(libs.gson)

    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.glide)
    implementation(libs.glide.okhttp3)
    ksp(libs.glide.ksp)

    implementation(libs.jaudiotagger)
}