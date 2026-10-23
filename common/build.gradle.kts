plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.typesafe.config)
    implementation(libs.bundles.serialization)
    compileOnly(libs.bundles.adventure)
    implementation(libs.sqlite.jdbc)
    implementation(libs.bundles.exposed)
    implementation(libs.lamp.common)
    implementation(libs.commons.lang3)
    implementation(libs.configurate.hocon)
    compileOnly(libs.slf4j.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.adventure)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    // サンプル DB での検証用 (任意)。NOTICECONNECT_SAMPLE_DB が指すファイルのコピーに対して移行を試す
    System.getenv("NOTICECONNECT_SAMPLE_DB")?.let { environment("NOTICECONNECT_SAMPLE_DB", it) }
    testLogging {
        showStandardStreams = true
    }
}
