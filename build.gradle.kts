import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.21"
    maven
    kotlin("plugin.serialization") version "1.5.21"
}



version = "1.0-SNAPSHOT"
group = "dev.mee42"


repositories {

    mavenCentral()

}

val JDBI_VERSION = "3.21.0"

dependencies {
    implementation("io.javalin:javalin:3.13.7")
    implementation("org.slf4j:slf4j-simple:1.8.0-beta4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.3")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jdbi:jdbi3-core:$JDBI_VERSION")
    implementation("org.jdbi:jdbi3-kotlin:$JDBI_VERSION")
    implementation("org.jdbi:jdbi3-sqlite:$JDBI_VERSION")
    implementation("org.xerial:sqlite-jdbc:$JDBI_VERSION")
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}