plugins {
    java
    id("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Provided by the host application's classloader at runtime (parent-first loading).
    compileOnly(project(":core-api"))
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    // Shaded into PaymentModule.jar.
    implementation("eu.okaeri:okaeri-configs-yaml-snakeyaml:5.0.5")

    // Tests
    testImplementation(project(":core-api"))
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("PaymentModule")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}