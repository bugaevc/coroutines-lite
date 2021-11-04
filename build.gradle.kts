import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    id("org.jetbrains.dokka") version "1.5.30"
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

group = "io.github.bugaevc"
version = "0.1"

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.8.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


val compileKotlin: KotlinCompile by tasks
val compileTestKotlin: KotlinCompile by tasks

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        )
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource) {
        rename { "io/github/bugaevc/coroutineslite/$it" }
    }
}
val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(tasks["dokkaHtml"])
    dependsOn(tasks["dokkaHtml"])
}
publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
        }
    }
}
