// Pixel Art Generator, a BlockDesigner plugin released on its own. Build it with:  ./gradlew jar
// then install build/libs/pixel-art-generator-<version>.jar with Plugins > Manage plugins > Install.
//
// It compiles against the BlockDesigner plugin API jars in libs/. BlockDesigner provides them and JavaFX at runtime,
// so they are never bundled into the plugin.
plugins {
    `java-library`
    alias(libs.plugins.javafx)
}

group = "io.blockdesigner.plugins"
version = "0.2.1"

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(26)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing,-classfile", "-parameters"))
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls")
    configuration = "compileOnly"
}

// The BlockDesigner API this plugin targets (from BlockDesigner 0.4.27).
val blockDesigner = files("libs/blockdesigner-plugin-api-0.4.27.jar", "libs/blockdesigner-core-0.4.27.jar")

dependencies {
    compileOnly(blockDesigner)

    testImplementation(blockDesigner)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Fixture renders for checking by eye: IMAGE_SHOTS=1 ./gradlew test writes build/pixel-art-shots/*.png
    environment("IMAGE_SHOTS_DIR", layout.buildDirectory.dir("pixel-art-shots").get().asFile.path)
}

// The manifest's version is this project's.
tasks.named<ProcessResources>("processResources") {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("blockdesigner-plugin.json") { filter { it.replace("@VERSION@", v) } }
}
