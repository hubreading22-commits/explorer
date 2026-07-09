plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("com.rapid7.client:dcerpc:0.12.0")
}

kotlin {
    jvmToolchain(17)
}
