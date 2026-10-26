plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.nachiketa.scheduler"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(project(":scheduler-model"))

	implementation("software.amazon.smithy.java:json-codec:1.8.0")

	implementation("org.springframework.boot:spring-boot-starter-data-cassandra")

	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	testImplementation(
		platform("org.testcontainers:testcontainers-bom:2.0.5")
	)

	testImplementation("org.testcontainers:testcontainers-cassandra")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")

}

val integrationTest by sourceSets.creating {
	java.srcDir("src/integrationTest/java")
	resources.srcDir("src/integrationTest/resources")

	compileClasspath += sourceSets.main.get().output +
			configurations.testRuntimeClasspath.get()

	runtimeClasspath += output + compileClasspath
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(
	configurations.testImplementation.get()
)

configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(
	configurations.testRuntimeOnly.get()
)

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.register<Test>("integrationTest") {
	description = "Runs integration tests."
	group = "verification"

	testClassesDirs = integrationTest.output.classesDirs
	classpath = integrationTest.runtimeClasspath

	useJUnitPlatform()

	shouldRunAfter(tasks.test)
}