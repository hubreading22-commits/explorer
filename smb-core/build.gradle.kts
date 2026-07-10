plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.rapid7.client:dcerpc:0.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

kotlin {
    jvmToolchain(17)
}
