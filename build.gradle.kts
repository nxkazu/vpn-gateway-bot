plugins {
    id("com.gradleup.shadow") version "9.4.3" apply false
}

allprojects {
    group = "com.tessera"
    version = "1.3.0"

    repositories {
        mavenCentral()
    }
}

tasks.register<Sync>("prepareRuntime") {
    dependsOn(":tessera-app:shadowJar", ":tessera-payment:shadowJar", ":tessera-dashboard:shadowJar")

    into(layout.buildDirectory.dir("runtime"))

    from(project(":tessera-app").layout.buildDirectory.file("libs/tessera-vpn-bot.jar")) {
        rename { "app.jar" }
    }

    into("modules") {
        from(project(":tessera-payment").layout.buildDirectory.file("libs/tessera-payment.jar"))
        from(project(":tessera-dashboard").layout.buildDirectory.file("libs/tessera-dashboard.jar"))
    }
}
