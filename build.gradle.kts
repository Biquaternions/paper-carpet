plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.4.1"
    id("io.freefair.lombok") version "9.4.0"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io/")
}

dependencies {
    paperweight.paperDevBundle("26.3.build.+")
    // compileOnly("io.papermc.paper:paper-api:26.2-rc-2.build.+")

    implementation("me.biquaternions.jshepherd:core:4.2.1")
    implementation("me.biquaternions.jshepherd:yaml:4.2.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.3")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    jar {
        archiveClassifier.set("dev")
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()

        mapOf(
            "de.bsommerfeld.jshepherd" to "jshepherd",
            "me.biquaternions.jshepherd" to "jshepherd.biquaternions",
        ).forEach { (path, alias) -> relocate(path, "me.biquaternions.carpet.libs.$alias") }
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
