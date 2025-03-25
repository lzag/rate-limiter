import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.Exec

plugins {
  kotlin("jvm") version "1.9.23"
  application
  id("com.gradleup.shadow") version "8.3.6"
  id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
  id("com.avast.gradle.docker-compose") version "0.17.12"
}

group = "com.lzag"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "4.5.13"
val junitJupiterVersion = "5.9.1"

val mainVerticleName = "com.lzag.ratelimiter.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "$projectDir/gradlew classes"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-web-client:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
  implementation("io.vertx:vertx-redis-client:$vertxVersion")
  implementation("io.vertx:vertx-config:$vertxVersion")
  implementation("io.vertx:vertx-config-yaml:$vertxVersion")
  implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
  implementation("io.vertx:vertx-circuit-breaker:$vertxVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

  implementation("org.slf4j:slf4j-api:2.0.17")
  implementation("ch.qos.logback:logback-classic:1.5.17")
  implementation(kotlin("stdlib-jdk8"))
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "21"

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

val ci: String? by project

tasks.withType<Test> {
  if (ci == null) {
    dependsOn("composeUp")
    finalizedBy("composeDown")
  }
  dependsOn("runLuaTests")
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

dockerCompose {
    useComposeFiles.set(listOf("docker-compose.yml"))
    startedServices.set(listOf("valkey"))
}

tasks.withType<JavaExec> {
  args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
//  jvmArgs = listOf(
//    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
//  )
}

tasks.register<Exec>("runLuaTests") {
  group = "verification"
  description = "Runs Lua unit tests"
  commandLine("lua", "tests.lua")
  workingDir = file("src/test/lua")
}
