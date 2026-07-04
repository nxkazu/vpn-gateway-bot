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
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.22.0")
}

tasks.shadowJar {
    archiveBaseName.set("tessera-dashboard")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
