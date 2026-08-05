plugins {
    id("java-library")
}

group = "dev.simplified"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://jitpack.io")
}

dependencies {
    // https://central.sonatype.com/artifact/com.discord4j/discord4j-core/versions
    // api, not implementation: consumers build dispatches and assert on discord4j types
    api(libs.discord4j)

    // JetBrains Annotations
    // api, not implementation: @NotNull/@Nullable appear on the public signatures
    api(libs.annotations)

    // Logging
    // @Log generates a Log4j2 logger field, so the api is needed to compile against
    api(libs.log4j2.api)

    // Simplified Annotations
    implementation("io.github.simplified-dev:annotations:2.5.0")
    annotationProcessor("io.github.simplified-dev:annotations:2.5.0")
    testAnnotationProcessor("io.github.simplified-dev:annotations:2.5.0")

    // Tests
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)

    // Test logging provider (log4j-api has no binding on the test classpath otherwise)
    testRuntimeOnly(libs.log4j2.core)
    testRuntimeOnly(libs.log4j2.slf4j2)
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("harness.debug", System.getProperty("harness.debug", "false"))
    }
}
