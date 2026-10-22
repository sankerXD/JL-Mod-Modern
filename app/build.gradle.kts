import java.util.Locale
import java.util.Properties
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val secret = Properties().also { properties ->
    rootProject.file("keystore.properties").runCatching { inputStream().use(properties::load) }
}

android {
    compileSdk = rootProject.extra["compileSdk"] as Int
    ndkVersion = rootProject.extra["ndkVersion"] as String
    namespace = "ru.playsoftware.j2meloader"

    defaultConfig {
        applicationId = "io.github.sankerxd.jlmodern"
        minSdk = rootProject.extra["minSdk"] as Int
        targetSdk = rootProject.extra["targetSdk"] as Int
        versionCode = 55
        versionName = "1.0.3"
        resValue("string", "app_name", rootProject.name)
        // 数据目录名与 app_name 解耦：release/debug 共用 /sdcard/JL-Modern
        resValue("string", "emulator_dir_name", "JL-Modern")
        resValue("string", "app_center", secret.getProperty("appCenterKey", ""))
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources.generateLocaleConfig = true

    buildFeatures {
        viewBinding = true
        prefab = true
        buildConfig = true
    }

    signingConfigs.create("emulator") {
        if (secret.isNotEmpty()) {
            keyAlias = secret.getProperty("keyAlias")
            keyPassword = secret.getProperty("keyPassword")
            storeFile = rootProject.file(secret.getProperty("storeFile"))
            storePassword = secret.getProperty("storePassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
        debug {
            applicationIdSuffix = ".debug"
            isJniDebuggable = true
            multiDexEnabled = true
            multiDexKeepProguard = file("multidex-config.pro")
        }
    }

    lint.disable += "MissingTranslation"

    flavorDimensions += "default"
    productFlavors {
        create("emulator") { // variant dimension for create emulator
            buildConfigField("boolean", "FULL_EMULATOR", "true")
            signingConfig = signingConfigs.getByName("emulator")
            versionNameSuffix = System.getenv("VERSION_SUFFIX")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("midlet") { // variant dimension for create android port from J2ME app source
            buildConfigField("boolean", "FULL_EMULATOR", "false")
            // configure midlet's port project params here, as default it read from app manifest,
            // placed to 'app/src/midlet/resources/MIDLET-META-INF/MANIFEST.MF'
            val props = getMidletManifestProperties()
            val midletName = props.getValue("MIDlet-Name")?.trim() ?: "Demo MIDlet"
            val apkName = midletName.replace("[/\\\\:*?\"<>|]".toRegex(), "").replace(" ", "_")
            applicationId = "com.example.androidlet.${apkName.lowercase(Locale.getDefault())}"
            versionName = props.getValue("MIDlet-Version") ?: "1.0"
            resValue("string", "app_name", midletName)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-midlet.pro"
            )
        }
    }

    splits.abi {
        isEnable = false
        reset()
        include("x86", "armeabi-v7a", "x86_64", "arm64-v8a")
        isUniversalApk = true
    }

    // externalNativeBuild.ndkBuild.path("src/main/cpp/Android.mk")  // disabled: reuse prebuilt .so from the shipped APK

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }

    applicationVariants.configureEach {
        if (buildType.name == "debug" && flavorName == "emulator") {
            resValue("string", "app_name", "JL-Modern-Debug")
        }
        outputs.configureEach {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                outputFileName = "${rootProject.name}_$versionName.apk"
            }
        }
    }
}

fun getMidletManifestProperties(): Attributes = Manifest().let { mf ->
    project.file("src/midlet/resources/MIDLET-META-INF/MANIFEST.MF").runCatching {
        inputStream().use(mf::read)
    }
    return mf.mainAttributes
}

dependencies {
    implementation("org.snapvm:snapvm:0.1.0")
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    val roomVersion = "2.6.1"
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-rxjava2:$roomVersion")

    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")

    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.arch.core:core-common:2.2.0")
    implementation("androidx.collection:collection-ktx:1.3.0")
    implementation("androidx.concurrent:concurrent-futures:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-common:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.transition:transition:1.4.1")
    implementation("com.google.android.material:material:1.11.0")
    //noinspection GradleDependency (next version incompatible with Android 4)
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.oboe:oboe:1.7.0")

    implementation("ch.acra:acra-http:5.11.3") {
        // acra ships the auto-service annotation processor on the runtime
        // classpath (upstream packaging wart). Its META-INF service file +
        // javax.annotation.processing refs break R8's missing-class check;
        // acra never touches it at runtime — drop it (and auto-common+guava
        // which only ride along through it).
        exclude(group = "com.google.auto.service", module = "auto-service")
    }
    implementation("com.github.nikita36078:mobile-ffmpeg:v4.3.2-compact")
    implementation("com.github.nikita36078:pngj:2.2.3")
    implementation("com.github.woesss:filepicker:4.4.0")
    implementation("com.github.yukuku:ambilwarna:2.0.1")
    //noinspection GradleDependency (next version incompatible with Android 4)
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// ---------------------------------------------------------------------------
// 发布前的硬性检查：着色器必须随 APK 自带（见 AGENTS.md「着色器已内置进 APK」）。
// 运行 ./gradlew verifyBundledShaders —— 少一个文件就编译失败，防止以后误删。
// ---------------------------------------------------------------------------
tasks.register("verifyBundledShaders") {
    dependsOn("assembleEmulatorRelease")
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/emulator/release").get().asFile
        val apk = dir.listFiles { f -> f.extension == "apk" }?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("没有找到 release APK: $dir")
        val required = listOf(
            "assets/bundled_shaders/hd.ini",
            "assets/bundled_shaders/hd.fsh",
            "assets/bundled_shaders/slotmask.ini",
            "assets/bundled_shaders/slotmask.fsh",
            "assets/bundled_shaders/slotmask.vsh",
            "assets/bundled_shaders/lcd3x.ini",
            "assets/bundled_shaders/lcd3x.fsh",
            "assets/bundled_shaders/dither.ini",
            "assets/bundled_shaders/dither.fsh"
        )
        ZipFile(apk).use { zip ->
            val missing = required.filter { zip.getEntry(it) == null }
            if (missing.isNotEmpty()) {
                throw IllegalStateException(
                    "APK ${apk.name} 缺少内置着色器: $missing\n" +
                            "别删 app/src/main/assets/bundled_shaders/ 和 BundledShaders.install() 的挂钩。"
                )
            }
        }
        println("OK: ${apk.name} 自带 ${required.size} 个着色器文件")
    }
}
