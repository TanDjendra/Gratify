plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "com.tan.gratify"

dependencies {
    implementation(projects.jmtc)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}