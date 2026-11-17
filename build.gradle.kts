plugins {
    id("io.ia.sdk.modl") version "0.5.0"
}

// Ignition SDK version to compile against. Must not be newer than the target Gateway.
val sdkVersion by extra("8.1.35")

// The version comes from gradle.properties; release builds override it with -Pversion=x.y.z.
allprojects {
    group = "io.github.bearinmindcat.taghover"
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven { url = uri("https://nexus.inductiveautomation.com/repository/public/") }
    }

    // The 8.1.35 SDK is published as Java 17 libraries (Gateways and clients on this version run Java 17).
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
    }
}

ignitionModule {
    name.set("Tag Hover Tooltip")
    fileName.set("Tag-Hover-Tooltip.modl")
    id.set("io.github.bearinmindcat.taghover")
    moduleVersion.set("${project.version}")
    moduleDescription.set("Hold Ctrl and hover over a Vision component to see the tags it is bound to.")
    // Compiled against (and to the Java level of) the SDK above, so don't claim older Gateways.
    requiredIgnitionVersion.set(sdkVersion)
    requiredFrameworkVersion.set("8")
    freeModule.set(true)

    // The client jar holds all the tooltip logic and is loaded in both Vision clients and the Designer.
    projectScopes.putAll(
        mapOf(
            ":client" to "CD",
            ":designer" to "D",
            ":gateway" to "G",
        )
    )

    // Depend on Vision so our classloader can see Vision's classes in clients and the Designer.
    // In Ignition 8 its module ID is com.inductiveautomation.vision; the old 7.x ID "fpmi" makes
    // clients and the Designer skip this module.
    moduleDependencySpecs {
        register("com.inductiveautomation.vision") {
            scope = "CD"
            required = true
        }
    }

    hooks.putAll(
        mapOf(
            "io.github.bearinmindcat.taghover.client.ClientHook" to "C",
            "io.github.bearinmindcat.taghover.designer.DesignerHook" to "D",
            "io.github.bearinmindcat.taghover.gateway.GatewayHook" to "G",
        )
    )

    // Signs when the ignition.signing.* properties are set (e.g. in ~/.gradle/gradle.properties);
    // otherwise builds an unsigned module, which needs -Dignition.allowunsignedmodules=true on the Gateway.
    skipModlSigning.set((findProperty("ignition.signing.keystoreFile") as String?).isNullOrBlank())
}
