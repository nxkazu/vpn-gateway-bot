plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "com.tessera"
    version = "1.1.0"

    repositories {
        mavenCentral()
    }
}