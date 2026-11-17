val sdkVersion: String by rootProject.extra

dependencies {
    // The client jar is already shipped in Designer scope ("CD"), so it is compile-only here.
    compileOnly(project(":client"))
    compileOnly("com.inductiveautomation.ignitionsdk:ignition-common:$sdkVersion")
    compileOnly("com.inductiveautomation.ignitionsdk:client-api:$sdkVersion")
    compileOnly("com.inductiveautomation.ignitionsdk:designer-api:$sdkVersion")
    compileOnly("com.inductiveautomation.ignitionsdk:vision-client-api:$sdkVersion")
}
