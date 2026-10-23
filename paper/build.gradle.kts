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
        // sqlite-jdbc と Exposed は META-INF/services で実装を登録しているので結合する
        mergeServiceFiles()
        dependencies {
            exclude(dependency("net.kyori:.*:.*"))
            // slf4j はサーバー側が提供する (Exposed が推移的に持ってくるものは同梱しない)
            exclude(dependency("org.slf4j:.*:.*"))
        }
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
