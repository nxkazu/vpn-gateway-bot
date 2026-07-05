plugins {
    java
    application
    id("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":tessera-config"))
    implementation(project(":tessera-core"))
    implementation(project(":tessera-db"))
    implementation(project(":tessera-experience"))
    implementation(project(":tessera-i18n"))
    implementation(project(":tessera-modules"))
    implementation(project(":tessera-remnawave"))
    implementation(project(":tessera-services"))
    implementation(project(":tessera-telegram"))

    // Lombok (compile-time boilerplate reduction)
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    // Bootstrap registration with Telegram long polling.
    implementation("org.telegram:telegrambots:6.9.7.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.37")
}

application {
    mainClass.set("com.tessera.bot.TesseraBotApplication")
}

tasks.shadowJar {
    archiveBaseName.set("tessera-vpn-bot")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
