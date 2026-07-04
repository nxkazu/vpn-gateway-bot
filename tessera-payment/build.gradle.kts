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
    compileOnly(project(":tessera-core"))
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.22.0")

    // Tests
    testImplementation(project(":tessera-core"))
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("tessera-payment")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
