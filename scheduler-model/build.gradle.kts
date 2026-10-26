plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-jar") version "1.5.0"
}

repositories {
    mavenCentral()
}

dependencies {
    smithyBuild("software.amazon.smithy.java:codegen-plugin:1.8.0")
    implementation("software.amazon.smithy:smithy-aws-traits:1.73.0")
    api("software.amazon.smithy.java:core:1.8.0")
}

afterEvaluate {
    val generatedPath =
        smithy.getPluginProjectionPath(
            smithy.sourceProjection.get(),
            "java-codegen"
        ).get()

    sourceSets {
        main {
            java {
                srcDir("$generatedPath/java")
            }

            resources {
                srcDir("$generatedPath/resources")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}