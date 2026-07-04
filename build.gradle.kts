plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "com.tessera"
    version = "1.1.1"

    repositories {
        mavenCentral()
    }
}

tasks.register<Sync>("prepareRuntime") {
    dependsOn(":app:shadowJar", ":payment-module:shadowJar", ":admin-dashboard:shadowJar")

    into(layout.buildDirectory.dir("runtime"))

    from(project(":app").layout.buildDirectory.file("libs/tessera-vpn-bot.jar")) {
        rename { "app.jar" }
    }

    into("modules") {
        from(project(":payment-module").layout.buildDirectory.file("libs/PaymentModule.jar"))
        from(project(":admin-dashboard").layout.buildDirectory.file("libs/AdminDashboard.jar"))
    }
}
