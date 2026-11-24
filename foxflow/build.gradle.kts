plugins {
    id("java")
    id("maven-publish")
    id("org.wpilib.WPILibRepositoriesPlugin") version "2027.0.0"
}

group = "bot.den"
version = project.findProperty("version") ?: "dev"

repositories {
    mavenCentral()
}
wpilibRepositories.addAllReleaseRepositories(project)

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.wpilib.wpiunits)
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    if (project.hasProperty("version")) {
        withSourcesJar()
        withJavadocJar()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "bot.den"
            artifactId = "foxflow"
            version = project.version.toString()

            from(components["java"])
        }
    }
}