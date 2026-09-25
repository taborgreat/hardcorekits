plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.hardcorekits"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper moved to calendar versioning + build-stamped Maven coords (no more 1.21-R0.1-SNAPSHOT).
    // Bump this to the newest "<mc>.build.<n>-stable" from:
    // https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }

    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") { expand(props) }
    }

    // ./gradlew runServer  -> downloads Paper, installs this plugin, boots on localhost:25565
    runServer {
        minecraftVersion("26.2")
        // Attach a debugger from VS Code ("Attach to Paper" launch config) on port 5005.
        jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
        jvmArgs("-Xmx2G")
    }
}
