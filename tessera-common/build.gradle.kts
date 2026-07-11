plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":tessera-i18n"))
    api("com.google.zxing:core:3.5.4")
    api("com.google.zxing:javase:3.5.4")
}
