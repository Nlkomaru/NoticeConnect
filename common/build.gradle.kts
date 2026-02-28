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
    implementation(libs.lamp.common)
    implementation(libs.commons.lang3)
    implementation(libs.configurate.hocon)
    compileOnly(libs.slf4j.api)
}
