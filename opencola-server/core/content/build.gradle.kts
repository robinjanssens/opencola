val kotlinVersion: String by project
val jsoupVersion: String by project
val mime4jVersion: String by project
val tikaVersion: String by project

plugins {
    kotlin("jvm") version "1.9.0"
}

dependencies {
    implementation(project(":core:util"))
    implementation("org.jsoup:jsoup:$jsoupVersion")
    implementation("org.apache.james:apache-mime4j-core:$mime4jVersion")
    implementation("org.apache.james:apache-mime4j-dom:$mime4jVersion")
    implementation("org.apache.tika:tika-core:$tikaVersion")
    implementation("org.apache.tika:tika-parsers-standard-package:$tikaVersion")
    implementation("org.apache.pdfbox:pdfbox:2.0.27")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}