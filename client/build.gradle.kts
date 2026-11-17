val sdkVersion: String by rootProject.extra

dependencies {
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:$sdkVersion")
    compileOnly("com.inductiveautomation.ignitionsdk:client-api:$sdkVersion")
    compileOnly("com.inductiveautomation.ignitionsdk:vision-client-api:$sdkVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Tests build real Vision component trees, so they need the SDK at runtime too.
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

// Try the tooltip on real Vision components with fake tags, no Gateway needed (needs a desktop).
// Vision's own component code needs java.awt opened, as the real client launcher does.
val visionJvmArgs = listOf("--add-opens=java.desktop/java.awt=ALL-UNNAMED")

tasks.register<JavaExec>("runDemo") {
    group = "verification"
    description = "Opens a demo window: hold Ctrl and hover over the boxes."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.bearinmindcat.taghover.client.TooltipDemo")
    jvmArgs(visionJvmArgs)
}

tasks.register<JavaExec>("demoCheck") {
    group = "verification"
    description = "Simulates Ctrl+hover in the demo window, checks each tooltip and saves screenshots."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.bearinmindcat.taghover.client.TooltipDemo")
    jvmArgs(visionJvmArgs)
    args("--check", layout.buildDirectory.dir("demo").get().asFile.absolutePath)
}
