plugins {
    id("java")
    id("maven-publish")
}

group = "bot.den"
version = project.findProperty("publishingVersion") ?: "dev"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    if (project.hasProperty("publishingVersion")) {
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