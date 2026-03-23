package aero.digitalhangar.secrets

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import java.io.File
import java.util.Properties

class Secrets : Plugin<Project> {

    private val logger = Logging.getLogger(Secrets::class.java)

    override fun apply(target: Project) {
        val extension: SecretsPluginExtension = target.extensions.create(
            "secrets",
            SecretsPluginExtension::class.java,
        )
        extension.defaultSecretsFileName.convention("local.properties")
        extension.ignoreList.convention(listOf("""sdk.dir"""))
        extension.variantSecretsMapping.put(".*", extension.defaultSecretsFileName)

        // Resolve the concrete AGP components extension (Application or Library).
        // Using the concrete type avoids the Kotlin variance issue with
        // AndroidComponentsExtension<*, *, *> where finalizeDsl produces a
        // Nothing-typed callback parameter.
        val appComponents =
            target.extensions.findByType(ApplicationAndroidComponentsExtension::class.java)
        val libComponents =
            target.extensions.findByType(LibraryAndroidComponentsExtension::class.java)
        val component: AndroidComponentsExtension<*, *, *> = (appComponents ?: libComponents)
            ?: throw GradleException(
                "The Secrets plugin requires the Android Application or Library plugin. " +
                        "Apply one of them before applying the Secrets plugin.",
            )

        // Collect all flavors-per-dimension and build types once the DSL is finalised.
        // finalizeDsl runs *before* onVariants, so these are guaranteed to be populated.
        var allFlavorsPerDimension: Map<String, List<String>> = emptyMap()
        var allBuildTypes: List<String> = emptyList()
        val taskNames = target.gradle.startParameter.taskNames
        fun collectVariantMetadata(commonExtension: CommonExtension) {
            allFlavorsPerDimension = commonExtension.productFlavors
                .filter { it.dimension != null }
                .groupBy(keySelector = { it.dimension!! }, valueTransform = { it.name })
            allBuildTypes = commonExtension.buildTypes.map { it.name }

            logger.info(
                "**********\nTask Name: {}\nAll Flavors: {}\nAll Build Types: {}\n**********",
                taskNames, allFlavorsPerDimension, allBuildTypes
            )
        }

        appComponents?.finalizeDsl { collectVariantMetadata(it) }
        libComponents?.finalizeDsl { collectVariantMetadata(it) }

        component.onVariants { variant ->
            // Resolve whether the running task targets this variant.
            // Returns the variant name when matched, null when the task
            // carries no variant component (e.g. "clean", "lint", IDE sync).
            val requestedVariant: String? = VariantMatcher.resolveRequestedVariant(
                taskNames = taskNames,
                variant = variant,
                allFlavorsPerDimension = allFlavorsPerDimension,
                allBuildTypes = allBuildTypes,
            )

            // When a specific variant was requested and this is not it, skip.
            if (taskNames.isNotEmpty() && requestedVariant == null) {
                logger.info(
                    "**********\nSecrets: skipping variant '{}' — not targeted by tasks {}\n**********",
                    variant.name, taskNames,
                )
                return@onVariants
            } else {
                logger.info(
                    "**********\nSecrets: using variant '{}' — targeted by tasks {}\n**********",
                    variant.name, taskNames,
                )
            }
//            extension.variantSecretsMapping.get()
//                .forEach { (pattern, fileName) ->
//                    if (fileName.isNotBlank() && pattern.toRegex().containsMatchIn(variant.name)) {
//                        val fileProperties: Properties = loadProperties(
//                            fileName = fileName,
//                            rootDir = target.rootProject.projectDir
//                        )
//                    }
//                }
        }
    }
//                        resolveSecrets(
//                            defaultsFileName = extension.defaultSecretsFileName.get(),
//                            fileName = fileName,
//                            ignoreList = extension.ignoreList.get(),
//                            properties = fileProperties,
//                            variant = variant
//                        )
//                    }
//                }

    private fun loadProperties(
        fileName: String,
        rootDir: File,
    ): Properties {
        val file: File = rootDir.resolve(fileName)
        if (!file.exists()) {
            throw GradleException("Secrets file $fileName does not exist.")
        }
        return Properties().apply { file.inputStream().use(::load) }
    }

    private fun resolveSecrets(
        fileName: String,
        ignoreList: List<String>,
        properties: Properties,
        variant: Variant,
    ) {
        properties.stringPropertyNames()
            .filterNot { key ->
                key.isNotBlank() && ignoreList.map(::Regex).any { it.containsMatchIn(key) }
            }
            .map { key ->
                key.replace(Regex(pattern = """((?![a-zA-Z_$0-9]).)"""), "")
            }
            .forEach { key ->
                val value: String = properties.getProperty(key).removeSurrounding("\"")
                if (value.isBlank() || value == "placeholder") {
                    throw GradleException(
                        "File '${fileName}' contains a wrong value for key '$key'."
                    )
                }
                variant.buildConfigFields?.put(
                    key,
                    BuildConfigField("String", value.addQuotationMarks(), null)
                )
                variant.manifestPlaceholders.put(key, value)
            }
    }

    private fun String.addQuotationMarks(): String =
        if (Regex("""^".*"$""").matches(this)) this else "\"$this\""

}