plugins {
    kotlin("jvm") version "2.2.10"
    application
}

group = "uz.ttpu"
version = "1.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("uz.ttpu.coroutineslab.MainKt")
}
