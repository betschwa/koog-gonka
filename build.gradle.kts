import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

allprojects {
    group = "de.betchvaia"
    version = "0.1.0-SNAPSHOT"
}

// Single JVM module today (koog-gonka only) — kept as a subprojects{} block, not
// applied directly in koog-gonka/build.gradle.kts, so a future second module (e.g. a
// wallet-dispatch module once Gonka's devshard/bridge is implemented upstream, see the
// vault note) picks up the same publishing config automatically.
subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "com.vanniktech.maven.publish")

        configure<MavenPublishBaseExtension> {
            // automaticRelease = true: uploads to the Central Portal staging and
            // immediately publishes, instead of leaving the deployment sitting at
            // VALIDATED until someone clicks "Publish" at
            // https://central.sonatype.com/publishing/deployments.
            publishToMavenCentral(automaticRelease = true)

            // Only wire up GPG signing when a signing key is actually configured (CI
            // secrets or a maintainer's own ~/.gradle/gradle.properties, see README.adoc's
            // "Publishing to Maven Central" section). Without this guard,
            // signAllPublications() unconditionally registers a `sign*` task with no
            // signatory, which breaks the credential-free `./gradlew publishToMavenLocal`
            // dry run.
            if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                signAllPublications()
            }

            coordinates(
                groupId = "de.betchvaia",
                artifactId = project.name,
                version = project.version.toString(),
            )

            configure(
                KotlinJvm(
                    javadocJar = JavadocJar.Empty(),
                    sourcesJar = SourcesJar.Sources(),
                ),
            )

            pom {
                name.set(project.name)
                description.set(
                    "A Koog LLMClient implementation for Gonka, a decentralized " +
                        "blockchain-based AI compute network.",
                )
                url.set("https://github.com/betschwa/koog-gonka")

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("betschwa")
                        name.set("Irakli Betchvaia")
                        email.set("irakli@betchvaia.de")
                    }
                }

                scm {
                    url.set("https://github.com/betschwa/koog-gonka")
                    connection.set("scm:git:https://github.com/betschwa/koog-gonka.git")
                    developerConnection.set("scm:git:ssh://git@github.com/betschwa/koog-gonka.git")
                }
            }
        }
    }
}
