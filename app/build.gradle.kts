plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "cl.contasimp.tracker"
    compileSdk = 35
    defaultConfig { applicationId = "cl.contasimp.tracker"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.41.53" }
    buildTypes { release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
