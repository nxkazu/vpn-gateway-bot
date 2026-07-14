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

    api("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.flywaydb:flyway-core:12.11.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.11.0")
}
