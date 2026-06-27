import java.nio.file.Paths
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

base {
    archivesName.set("wadb-v1.0.0")
}

val localFile = rootProject.file("local.properties")
val localProps = Properties()
val dontRestartAdbd = if (localFile.canRead()) {
    localFile.inputStream().use(localProps::load)
    localProps.getProperty("debug.dontRestartAdbd", "false").toBoolean()
} else {
    false
}

val signFile = rootProject.file("signing.properties")
val signProps = Properties()
val hasSignConfig = signFile.canRead()
if (hasSignConfig) {
    signFile.inputStream().use(signProps::load)
}

android {
    namespace = "moe.haruue.wadb"
    compileSdk = 37
    ndkVersion = "30.0.14904198"

    defaultConfig {
        applicationId = "moe.haruue.wadb"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_URL", "\"https://github.com/RikkaApps/WADB\"")
        buildConfigField("String", "LICENSE", "\"Apache License 2.0\"")
        buildConfigField("String", "TRANSLATION_URL", "\"https://rikka.app/contribute_translation/\"")
        buildConfigField("String", "COPYRIGHT", "\"Copyright © Haruue Icymoon, PinkD, Rikka\"")
        buildConfigField("boolean", "DONOT_RESTART_ADBD", dontRestartAdbd.toString())

        externalNativeBuild {
            cmake {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    buildFeatures {
        prefab = true
        buildConfig = true
        compose = true
    }

    androidResources {
        localeFilters += listOf("de", "id", "it", "ja", "pt-rBR", "ru", "tr", "zh-rCN", "zh-rTW")
    }

    signingConfigs {
        if (hasSignConfig) {
            create("sign") {
                val debugConfig = getByName("debug")
                storeFile = signProps.getProperty("KEYSTORE_FILE")?.let(::file) ?: debugConfig.storeFile
                storePassword = signProps.getProperty("KEYSTORE_PASSWORD", debugConfig.storePassword)
                keyAlias = signProps.getProperty("KEYSTORE_ALIAS", debugConfig.keyAlias)
                keyPassword = signProps.getProperty("KEYSTORE_ALIAS_PASSWORD", debugConfig.keyPassword)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            if (hasSignConfig) {
                signingConfig = signingConfigs.getByName("sign")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSignConfig) {
                signingConfig = signingConfigs.getByName("sign")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            excludes += "/kotlin/**"
        }
        resources {
            excludes += setOf("/META-INF/*.version", "/META-INF/*.kotlin_module", "/kotlin/**")
        }
    }

    dependenciesInfo {
        includeInApk = false
    }

    lint {
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val optimizeReleaseLinkedResources by tasks.registering {
    doLast {
        val aapt2 = Paths.get(android.sdkDirectory.path, "build-tools", android.buildToolsVersion, "aapt2")
        val zip = fileTree(layout.buildDirectory.dir("intermediates")) {
            include("**/linked_resources_binary_format/release/**/*.ap_")
        }.files.singleOrNull() ?: return@doLast
        val optimized = File("$zip.opt")
        val result = providers.exec {
            commandLine(
                aapt2,
                "optimize",
                "--collapse-resource-names",
                "--shorten-resource-paths",
                "-o",
                optimized,
                zip,
            )
            isIgnoreExitValue = false
        }.result.get()
        if (result.exitValue == 0) {
            delete(zip)
            optimized.renameTo(zip)
        }
    }
}

tasks.whenTaskAdded {
    if (name == "processReleaseResources") {
        finalizedBy(optimizeReleaseLinkedResources)
    }
}

val copyReleaseArtifacts by tasks.registering {
    doLast {
        val mapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile
        if (mapping.exists()) {
            copy {
                from(mapping)
                into("release")
                rename { "mapping-1.txt" }
            }
        }
        copy {
            from(fileTree(layout.buildDirectory.dir("outputs/apk/release")) {
                include("*.apk")
            })
            into("release")
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyReleaseArtifacts)
}

repositories {
    maven {
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.topjohnwu.libsu")
        }
    }
}

configurations.all {
    exclude(group = "androidx.appcompat", module = "appcompat")
}

dependencies {
    compileOnly(project(":hidden"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.libsu.core)
    implementation(libs.shizuku.api)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
