plugins {
    java
}

group = "dev.shane.minecraft"
version = providers.gradleProperty("pluginVersion").get()
description = providers.gradleProperty("pluginDescription").get()

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val paperApiDependencyVersion = providers.gradleProperty("paperApiDependencyVersion").get()
val artifactBaseName = providers.gradleProperty("artifactBaseName").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiDependencyVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiDependencyVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks {
    test {
        useJUnitPlatform()
    }

    withType<JavaCompile>().configureEach {
        options.release.set(25)
    }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "paperApiVersion" to paperApiVersion,
            "pluginName" to providers.gradleProperty("pluginName").get(),
            "pluginMain" to providers.gradleProperty("pluginMain").get(),
            "pluginDescription" to providers.gradleProperty("pluginDescription").get()
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName.set(artifactBaseName)
        archiveClassifier.set("paper-$paperApiVersion")
    }
}
