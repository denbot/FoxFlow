plugins {
    id("java")
    id("maven-publish")
    id("edu.wpi.first.wpilib.repositories.WPILibRepositoriesPlugin") version "2025.0"
}

group = "bot.den"
version = project.findProperty("version") ?: "dev"

repositories {
    mavenCentral()
}
wpilibRepositories.addAllReleaseRepositories(project)

val wpilibVersion = "2027.0.0-alpha-7"

dependencies {
    implementation("com.palantir.javapoet:javapoet:0.9.0")

    implementation(project(":foxflow"))

    implementation("org.wpilib.hal:hal-java:${wpilibVersion}")
    implementation("org.wpilib.wpilibj:wpilibj-java:${wpilibVersion}")
    implementation("org.wpilib.wpiutil:wpiutil-java:${wpilibVersion}")
    implementation("org.wpilib.wpimath:wpimath-java:${wpilibVersion}")
    implementation("org.wpilib.wpiunits:wpiunits-java:${wpilibVersion}")
    implementation("org.wpilib.ntcore:ntcore-java:${wpilibVersion}")
    implementation("org.wpilib.telemetry:telemetry-java:${wpilibVersion}")
    implementation("org.wpilib.tunables:tunables-java:${wpilibVersion}")
    implementation("org.wpilib.commandsv2:commandsv2-java:${wpilibVersion}")
    implementation("org.wpilib.commandsv2:commandsv2-cpp:${wpilibVersion}")
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