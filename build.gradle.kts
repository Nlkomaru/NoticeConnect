import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("eclipse")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.0.1"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    kotlin("plugin.serialization") version "1.9.22"
    kotlin("jvm") version "1.9.0"
    id("com.github.evestera.depsize") version "0.1.0"
}

group = "com.noticemc"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://nexus.velocitypowered.com/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}
val lampVersion = "3.1.7"
val velocityVersion = "3.2.0-SNAPSHOT"
val serializationVersion = "1.6.0"
val typesafeVersion = "1.4.3"
val adventureVersion = "4.14.0"
val sqliteVersion = "3.43.2.1"
val commonLangVersion = "3.12.0"
dependencies {
    implementation(kotlin("stdlib-jdk8"))

    compileOnly("com.velocitypowered","velocity-api", velocityVersion)
    annotationProcessor("com.velocitypowered","velocity-api", velocityVersion)

    implementation("com.github.Revxrsal.Lamp","common", lampVersion)
    implementation("com.github.Revxrsal.Lamp","velocity",lampVersion)

    implementation("com.typesafe","config",typesafeVersion)
    implementation("org.jetbrains.kotlinx","kotlinx-serialization-hocon", serializationVersion)
    implementation("org.jetbrains.kotlinx","kotlinx-serialization-json", serializationVersion)

    implementation("net.kyori","adventure-api",adventureVersion)
    implementation("net.kyori","adventure-text-minimessage",adventureVersion)

    implementation("org.xerial","sqlite-jdbc",sqliteVersion)

    implementation("org.apache.commons","commons-lang3", commonLangVersion)
}

java {
    val version = JavaVersion.VERSION_17
    sourceCompatibility = version
    targetCompatibility = version
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    build {
        dependsOn(shadowJar)
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "17"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "17"
}


