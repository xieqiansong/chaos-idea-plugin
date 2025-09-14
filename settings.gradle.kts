pluginManagement {
    repositories {
        mavenLocal()
        maven { url = uri("https://ubuntu.lan:10122/repository/maven-public") }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "confusion-idea-plugin"
