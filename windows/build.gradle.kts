plugins {
    kotlin("multiplatform") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("com.badlogicgames.jamepad:jamepad:2.30.0.0")
                implementation(compose.materialIconsExtended)
                // We will link the shared brain logic here later
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.nandanes.emu.MainKt"
        jvmArgs += "-Djava.library.path=${project.rootDir.absolutePath}"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "NandaSNES"
            packageVersion = "1.0.0"
        }
    }
}
