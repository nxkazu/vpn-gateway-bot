plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":tessera-config"))

    api("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
}
