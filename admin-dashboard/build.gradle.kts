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
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

tasks.shadowJar {
    archiveBaseName.set("AdminDashboard")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}