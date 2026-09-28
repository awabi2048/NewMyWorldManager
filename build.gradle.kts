import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.6.1"
    `maven-publish`
}

group = "awabi2048"
version = "26.927.1"

repositories {
    mavenLocal()
    maven { url = uri("../.m2-paper26-kotlin2320") }
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://maven.enginehub.org/repo/")
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

// pom.xml の provided スコープ相当（compileOnly へ配置し、テストのコンパイル・実行双方で見えるよう testImplementation にも追加）
val providedDeps = listOf(
    "io.papermc.paper:paper-api:26.1.2.build.72-stable",
    "com.awabi2048:CC-System:26.905.2",
    "com.github.LeonMangler:PremiumVanishAPI:2.9.18-2",
    "net.luckperms:api:5.4",
    "com.sk89q.worldedit:worldedit-bukkit:7.3.16",
    "com.sk89q.worldguard:worldguard-bukkit:7.0.14",
)

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    implementation("com.github.bea4dev:ChiyogamiLib:793983cef1")
    providedDeps.forEach {
        compileOnly(it)
        testImplementation(it)
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

// pom.xml の resources filtering 相当（${project.version} を展開）
tasks.processResources {
    val versionString = project.version.toString()
    inputs.property("projectVersion", versionString)
    filesMatching("plugin.yml") {
        expand(mapOf("project" to mapOf("version" to versionString)))
    }
}

tasks.shadowJar {
    archiveBaseName.set("MyWorldManager")
    archiveClassifier.set("")
    // pom.xml の shade フィルタ相当（依存JAR含め MANIFEST.MF を除外）
    exclude("META-INF/MANIFEST.MF")
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifactId = "my-world-manager"
            from(components["shadow"])
        }
    }
    repositories {
        maven {
            name = "workspace"
            url = uri("../.m2-paper26-kotlin2320")
        }
    }
}
