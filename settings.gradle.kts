pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // Termux terminal-view/terminal-emulator（Apache 2.0，控制台真终端用）。
    maven { url = uri("https://jitpack.io") }
  }
}
rootProject.name = "dsh-mobile-apk"
include(":app")
