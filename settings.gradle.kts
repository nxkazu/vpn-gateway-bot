plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tessera-vpn-bot"

include("core-api")
include("app")
include("payment-module")
include("admin-dashboard")
