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
    implementation("com.palantir.javapoet:javapoet:0.9.0")

    implementation(project(":foxflow"))

    implementation(libs.bundles.wpilib)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    if (project.hasProperty("version")) {
        withSourcesJar()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "bot.den"
            artifactId = "foxflow-annotations"
            version = project.version.toString()

            from(components["java"])
        }
    }
}