plugins {
    id("dev.kikugie.stonecutter")
    id("gg.meza.stonecraft")

    // Declared here so the central build.gradle.kts can apply it without repeating the version.
    // 2.4.0 is the lowest Kotlin bundled by our runtime providers (Kotlin for Forge 5.12/6.3 ship
    // 2.4.0, fabric-language-kotlin 1.13.13 ships 2.4.10), so compiling against it is safe on both.
    kotlin("jvm") version "2.4.0" apply false
}

stonecutter active "1.20.4-neoforge" /* [SC] DO NOT EDIT */

// Architectury Loom generates its own "Minecraft Client (:target)" run configurations, but those
// are Application configs that need the IDE's per-target modules to exist, so they only work after
// a successful Gradle sync. These Gradle-type ones just invoke :<target>:runClient and work
// regardless. Regenerated from the live target list so they cannot drift when versions are added
// or removed - run ./gradlew generateRunConfigs after changing settings.gradle.kts.
tasks.register("generateRunConfigs") {
    group = "ide"
    description = "Writes an IntelliJ run configuration for every build target"

    val targets = subprojects.map { it.name }
    val outputDir = rootProject.file(".idea/runConfigurations")

    doLast {
        outputDir.mkdirs()
        // Clear ours first so targets that were removed do not linger.
        outputDir.listFiles { file -> file.name.startsWith("Gradle_Client_") }?.forEach { it.delete() }

        targets.forEach { target ->
            val fileName = "Gradle_Client_" + target.replace('.', '_').replace('-', '_') + ".xml"
            File(outputDir, fileName).writeText(
                """
                <component name="ProjectRunConfigurationManager">
                  <configuration default="false" name="Client $target" type="GradleRunConfiguration" factoryName="Gradle">
                    <ExternalSystemSettings>
                      <option name="executionName" />
                      <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}" />
                      <option name="externalSystemIdString" value="GRADLE" />
                      <option name="scriptParameters" value="" />
                      <option name="taskDescriptions">
                        <list />
                      </option>
                      <option name="taskNames">
                        <list>
                          <option value=":$target:runClient" />
                        </list>
                      </option>
                      <option name="vmOptions" />
                    </ExternalSystemSettings>
                    <ExternalSystemDebugServerProcess>true</ExternalSystemDebugServerProcess>
                    <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
                    <DebugAllEnabled>false</DebugAllEnabled>
                    <RunAsTest>false</RunAsTest>
                    <method v="2" />
                  </configuration>
                </component>
                """.trimIndent() + "\n"
            )
        }
        logger.lifecycle("Wrote ${targets.size} run configurations to .idea/runConfigurations")
    }
}
