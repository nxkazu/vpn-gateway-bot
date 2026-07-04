plugins {
    id("com.gradleup.shadow") version "9.4.3" apply false
}

allprojects {
    group = "com.tessera"
    version = "1.1.0"

    repositories {
        mavenCentral()
    }
}