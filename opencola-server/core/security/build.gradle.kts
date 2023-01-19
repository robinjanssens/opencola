val kotlinVersion: String by project
val bcprovVersion: String by project
val kotlinLoggingVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:util"))
    implementation(project(":core:serialization"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprovVersion")
    implementation("org.bouncycastle:bcpkix-jdk15on:$bcprovVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}