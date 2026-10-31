plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

val releaseId = providers.gradleProperty("releaseApplicationId").getOrElse("com.srizan12.shrink")
require(releaseId.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2,}"))) {
    "releaseApplicationId must be a valid, permanent application ID."
}
val releaseCode = providers.gradleProperty("releaseVersionCode").getOrElse("1").toIntOrNull()
require(releaseCode != null && releaseCode in 1..2_100_000_000) { "releaseVersionCode must be a positive Play-compatible integer." }
val releaseName = providers.gradleProperty("releaseVersionName").getOrElse("1.0.0")
require(releaseName.isNotBlank() && releaseName.length <= 50 && !releaseName.any { it.isISOControl() }) {
    "releaseVersionName must be a short, nonblank version label."
}
val signingNames = listOf("SHRINK_KEYSTORE_FILE", "SHRINK_KEYSTORE_PASSWORD", "SHRINK_KEY_ALIAS", "SHRINK_KEY_PASSWORD")
val signingValues = signingNames.map { providers.environmentVariable(it).orNull }
require(signingValues.all { it.isNullOrBlank() } || signingValues.all { !it.isNullOrBlank() }) {
    "Set all four SHRINK_KEYSTORE_FILE / SHRINK_KEYSTORE_PASSWORD / SHRINK_KEY_ALIAS / SHRINK_KEY_PASSWORD variables, or none for an unsigned candidate."
}
val hasReleaseSigning = signingValues.all { !it.isNullOrBlank() }
val deviceTestBuild = providers.gradleProperty("androidTestBuildType").getOrElse("debug")
require(deviceTestBuild in setOf("debug", "qa")) { "androidTestBuildType must be debug or qa." }

android {
    namespace = "com.example.imagecompressor"
    compileSdk = 37
    defaultConfig {
        applicationId = releaseId
        minSdk = 31
        targetSdk = 37
        versionCode = requireNotNull(releaseCode)
        versionName = releaseName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("production") {
                storeFile = file(signingValues[0]!!)
                require(storeFile!!.isFile) { "SHRINK_KEYSTORE_FILE must point to an existing keystore." }
                storePassword = signingValues[1]
                keyAlias = signingValues[2]
                keyPassword = signingValues[3]
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Exercises optimized release code on devices without using production keys or user data.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            testProguardFiles("test-proguard-rules.pro")
        }
    }
    testBuildType = deviceTestBuild
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
      it.systemProperty("shrink.screenshots", layout.buildDirectory.dir("design/${it.name}").get().asFile.absolutePath)
      it.systemProperty("shrink.preview.measure", providers.gradleProperty("previewMeasure").getOrElse("false"))
      it.systemProperty("shrink.preview.label", providers.gradleProperty("previewLabel").getOrElse("local"))
      it.systemProperty("shrink.preview.reports", layout.buildDirectory.dir("preview-performance").get().asFile.absolutePath)
      // Coil retains its main dispatcher. Keep opt-in probes isolated from Compose's test Looper.
      if (providers.gradleProperty("previewMeasure").getOrElse("false") == "true") it.forkEvery = 1
    }
    sourceSets.getByName("test").resources.directories.add("src/androidTest/assets")
    sourceSets.getByName("test").resources.directories.add("schemas")

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests.values.filter { test -> test.type == com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE }
            .forEach { test -> test.enable = true }
    }
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.compose.ui.test.accessibility)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  add("qaImplementation", libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.kotlinx.coroutines.test)
  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  // ATF's old transitive Material version references a removed AppCompat wrapper.
  androidTestImplementation("com.google.android.material:material:1.13.0")
  androidTestImplementation("com.google.auto.value:auto-value-annotations:1.11.0")

  // Image loading and metadata
  implementation(libs.coil.compose)
  implementation(libs.androidx.exifinterface)

  // Local compression history
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
}
