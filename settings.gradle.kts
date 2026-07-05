plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tessera-vpn-bot"

include("tessera-core")
include("tessera-config")
include("tessera-i18n")
include("tessera-common")
include("tessera-experience")
include("tessera-db")
include("tessera-remnawave")
include("tessera-services")
include("tessera-modules")
include("tessera-telegram")
include("tessera-app")
include("tessera-payment")
include("tessera-dashboard")
