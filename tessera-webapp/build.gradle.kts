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

    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
