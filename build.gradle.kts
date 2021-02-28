import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.31"
    kotlin("plugin.serialization") version "1.4.30"
}
group = "com.cvbotunion"
version = "2.0"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation("org.json:json:20200518")
    api("net.mamoe:mirai-core:2.4.1")
    api("net.mamoe:mirai-core-utils:2.4.1")
    api("net.mamoe:mirai-console-terminal:2.4.1")
    api("net.mamoe:mirai-console:2.4.1")
    implementation("io.javalin:javalin:3.13.3")
    implementation("net.mamoe","kotlin-jvm-blocking-bridge","1.10.0")
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-protobuf", "1.1.0")
    implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.1.0")
    implementation("org.jetbrains.kotlinx","kotlinx-coroutines-io","0.1.16")
    implementation("org.slf4j","slf4j-api", "1.7.30")
    implementation("org.slf4j","slf4j-simple", "1.7.30")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "QBotKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
}