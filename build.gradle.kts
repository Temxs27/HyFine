plugins {
    id("java")
}

group = "me.temxs27"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("HyFine")
    archiveVersion.set("1.1.0")

    from("src/main/resources") {
        include("**/*")
    }

    manifest {
        attributes(
            "Implementation-Title" to "HyFine",
            "Implementation-Version" to "1.1.0"
        )
    }
}
