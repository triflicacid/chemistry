plugins {
    id("java")
}

group = "com.ruben.utils"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("depict") {
    group = "chemistry"
    description = "Draws a SMILES string as an SVG document, e.g. --args=\"CCO --out=ethanol.svg\"."
    mainClass = "com.ruben.chemistry.cli.DepictSmiles"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("match") {
    group = "chemistry"
    description = "Draws a SMILES string with the matches of a SMARTS pattern highlighted, e.g. --args=\"CCO CO --out=ethanol.svg\"."
    mainClass = "com.ruben.chemistry.cli.DepictMatch"
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("gallery") {
    group = "chemistry"
    description = "Draws every molecule in a name,smiles file into out/, with a contact sheet."
    mainClass = "com.ruben.chemistry.cli.DepictGallery"
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    args("examples/molecules.csv")
}