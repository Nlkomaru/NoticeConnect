plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://nexus.velocitypowered.com/repository/maven-public/")
}

dependencies {
    implementation(project(":common"))

    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)

    implementation(libs.lamp.common)
    implementation(libs.lamp.velocity)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        archiveBaseName.set("NoticeConnect-Velocity")
        archiveClassifier.set("")
        dependencies {
            exclude(dependency("net.kyori:.*:.*"))
        }
    }
}
