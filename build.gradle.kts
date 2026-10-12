import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.aboutlibraries.multiplatform) apply false
    alias(libs.plugins.room) apply false

    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.build.config) apply false
    alias(libs.plugins.osdetector) apply false
    alias(libs.plugins.conveyor) apply false
    alias(libs.plugins.google.services) apply false
}

tasks.register<Delete>("Clean") {
    delete(rootProject.layout.buildDirectory)
}

// Extractor forks pin vulnerable parser/network dependencies. Patch both platforms
// centrally while retaining Wire 6.x compatibility with their generated messages.
val patchedExtractorDependencies = listOf(
    "com.squareup.wire:wire-runtime:${libs.versions.wire.runtime.get()}",
    "com.squareup.wire:wire-runtime-jvm:${libs.versions.wire.runtime.get()}",
    "org.apache.commons:commons-lang3:${libs.versions.commons.lang3.get()}",
    "org.java-websocket:Java-WebSocket:${libs.versions.java.websocket.get()}",
    "org.jsoup:jsoup:${libs.versions.jsoup.get()}",
)
val desktopJnaVersion = libs.versions.jna.get()

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.addAll(
                        listOf(
                            "-P",
                            "plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${layout.buildDirectory.asFile.get().absolutePath}/compose_metrics",
                        ),
                    )
                }
            }
        }
    }

    // PipePipe and Brave both depend on com.github.TeamNewPipe:nanojson with different commit
    // hashes. Gradle's default resolver picks PipePipe's older 1d9e1aea... commit which lacks
    // JsonArray.streamAsJsonObjects(), causing NoSuchMethodError when Brave's fallback runs at
    // runtime. Force the latest upstream commit (newer than both libs ship) across every module
    // so the merged APK/JAR carries a nanojson with the API both extractors expect.
    configurations.all {
        resolutionStrategy {
            force(*patchedExtractorDependencies.toTypedArray())
            // VLCJ's JPMS variants contain the same classes as the standard JNA
            // artifacts used by our tray and transport controls. Ship one pair.
            dependencySubstitution {
                substitute(module("net.java.dev.jna:jna-jpms"))
                    .using(module("net.java.dev.jna:jna:$desktopJnaVersion"))
                    .because("Avoid duplicate JNA classes and native binaries on desktop")
                substitute(module("net.java.dev.jna:jna-platform-jpms"))
                    .using(module("net.java.dev.jna:jna-platform:$desktopJnaVersion"))
                    .because("Use the same JNA version across desktop integrations")
            }
            force("com.github.TeamNewPipe:nanojson:c7a6c1c08d16b6d5ecded34758e6415e07be2166")
            
            // Supabase 3.0.3 relies on Clock$System which was completely removed in kotlinx-datetime 0.7.1
            // Force 0.6.1 globally to prevent transitive dependencies (like Keight) from breaking Supabase.
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
    }

}
