pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        gradlePluginPortal()
    }
}

rootProject.name = "chaos-idea-plugin"

include("IdeaVIM", "AceJump")
