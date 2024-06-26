val kotlinVersion: String by project
val kotlinLoggingVersion: String by project

plugins {
    kotlin("jvm") version "1.9.0"
}

dependencies {
    implementation(project(":core:io"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}