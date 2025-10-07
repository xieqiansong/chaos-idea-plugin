pluginManagement {
    repositories {
        maven { url = uri("https://ubuntu.lan:10122/repository/maven-public") }
        gradlePluginPortal()
    }
}

rootProject.name = "confusion-idea-plugin"

include("IdeaVIM", "AceJump")
