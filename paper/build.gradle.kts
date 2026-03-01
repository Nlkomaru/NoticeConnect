plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":common"))

    compileOnly(libs.paper.api)

    implementation(libs.lamp.common)
    implementation(libs.lamp.bukkit)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        archiveBaseName.set("NoticeConnect-Paper")
        archiveClassifier.set("")
        dependencies {
            exclude(dependency("net.kyori:.*:.*"))
        }
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
