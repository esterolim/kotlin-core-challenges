plugins {
    kotlin("jvm") version "2.2.21"
}

group = "com.nubank.challenges"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // JUnit 5 - Modern testing framework
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.14.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.0")

    // Kotlin standard library
    testImplementation(kotlin("test"))
}

kotlin {
    // Java 17 LTS - Stable version for production use
    jvmToolchain(17)

    // Compiler options for better code quality
    compilerOptions {
        // Warnings as errors
        allWarningsAsErrors = false
        // Opt-in to progressive mode (enables new features)
        progressiveMode = true
    }
}

tasks.test {
    useJUnitPlatform()

    // Test configuration
    testLogging {
        // Show test output
        showStandardStreams = false
        // Log passed tests
        events("passed", "skipped", "failed")
        // Show test class and method names
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}