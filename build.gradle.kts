import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("com.diffplug.spotless") version "6.25.0"
    id("pmd")
}

repositories {
    maven { url = uri("https://ubuntu.lan:10122/repository/maven-public") }
    mavenCentral()
    intellijPlatform {
        maven { url = uri("https://ubuntu.lan:10122/repository/maven-public") }
        mavenCentral()
        defaultRepositories()
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("cn.hutool:hutool-all:5.8.40")
    testImplementation("junit:junit:4.13.2")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    intellijPlatform {
        local("D:\\opt\\ideaIU-2024.3.6")

//        create("IC", "2024.2.1")

        plugins("AceJump:3.8.22")
        plugins("IdeaVIM:2.18.1")

        pluginVerifier()
        zipSigner()
        instrumentationTools()

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

intellijPlatform {
    publishing {
        token.set("")
    }
    pluginConfiguration {
        ideaVersion {
            untilBuild = provider { null }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceSets.all {
        languageSettings.apply {
            languageVersion = "2.0"
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    wrapper {
        gradleVersion = gradleVersion
    }
}
