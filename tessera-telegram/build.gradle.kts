plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":tessera-common"))
    api(project(":tessera-config"))
    api(project(":tessera-db"))
    api(project(":tessera-experience"))
    api(project(":tessera-i18n"))
    api(project(":tessera-remnawave"))
    api(project(":tessera-services"))

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    api("org.telegram:telegrambots:6.9.7.1")
}
