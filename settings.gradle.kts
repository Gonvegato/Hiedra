pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
// JitPack: LibretroDroid y RadialGamePad (el jugador interno; GPL-3.0, del autor de Lemuroid)
dependencyResolutionManagement { repositories { google(); mavenCentral(); maven { url = uri("https://jitpack.io") } } }
rootProject.name = "Hiedra"
include(":app")
