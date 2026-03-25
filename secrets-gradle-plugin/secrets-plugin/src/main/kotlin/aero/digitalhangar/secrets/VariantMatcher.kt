package aero.digitalhangar.secrets

import com.android.build.api.variant.Variant
import org.gradle.api.logging.Logging

/**
 * Pre-parses Gradle task names **once** to extract which product-flavor and
 * build-type components they carry, then answers per-variant "does this variant
 * match?" questions with **O(dimensions + 1) string comparisons** — no regex
 * is executed after construction.
 *
 * ### Construction (one-time cost)
 *
 * For every task name the constructor:
 *
 * 1. Strips the module prefix (everything after the last `:`).
 * 2. For each flavor dimension builds a regex alternation of all flavor names
 *    in that dimension (capitalised, sorted longest-first) and searches the
 *    task name.  The matched flavor — if any — is stored.
 * 3. Does the same for build types.
 * 4. Records whether *any* variant component was found at all.
 *
 * ### Per-variant matching (very cheap)
 *
 * [resolveVariant] iterates over the pre-parsed results and for each task:
 *
 * 1. **Full-name shortcut** — if the capitalised variant name appears verbatim
 *    in the task string → immediate match.
 * 2. **Flavor check** — for every dimension where a flavor *was* found in the
 *    task, it must equal the variant's flavor for that dimension.  Dimensions
 *    that were absent in the task are treated as "unspecified" (OK).
 * 3. **Build-type check** — same logic.
 * 4. **Generic-task wildcard** — if the task carried no variant component at
 *    all (e.g. `assemble`) it is treated as targeting **all** variants.
 *
 * ### Limitations
 *
 * * Abbreviated task names (e.g. `aD` for `assembleDebug`) are **not**
 *   recognised — they are treated as a generic task, which means they match
 *   all variants.
 * * A flavor whose capitalised form coincidentally matches a word elsewhere in
 *   the task name (e.g. a flavor `test` matching the suffix `UnitTest`) may
 *   cause a false positive.
 */
class VariantMatcher(
    taskNames: List<String>,
    allFlavorsPerDimension: Map<String, List<String>>,
    allBuildTypes: List<String>,
) {

    // ── pre-parsed task data ───────────────────────────────────────────

    /**
     * Holds the variant components extracted from a single task name.
     *
     * @property name   task name without module prefix
     * @property flavors   dimension → capitalised flavor found (only
     *                          dimensions whose flavor appeared in the task)
     * @property buildType capitalised build-type found, or `null`
     * @property hasAnyComponent `true` when at least one flavor or build-type
     *                          was detected. When `false`, the task is treated
     *                          as generic and therefore targets all variants.
     */
    private data class TaskMetadata(
        val name: String,
        val flavors: Map<String, String>,
        val buildType: String?,
    )

    private val taskMetadata: List<TaskMetadata> = taskNames.map { qualifiedTaskName ->
        resolveTaskMetadata(qualifiedTaskName, allFlavorsPerDimension, allBuildTypes)
    }

    // ── public API ─────────────────────────────────────────────────────

    /**
     * Returns [variant]'s name when at least one task targets it, otherwise `null`.
     *
     * Unspecified flavor dimensions and unspecified build type are treated as
     * wildcards. A generic task such as `assemble` therefore matches all variants.
     */
    fun resolveVariant(variant: Variant): String? =
        variant.name.takeIf {
            taskMetadata.any { taskMetadata ->
                matchesVariant(taskMetadata, variant)
            }
        }

    /**
     * Test-friendly overload that avoids depending on AGP's [Variant] type.
     */
//    internal fun resolveVariant(
//        variantName: String,
//        productFlavors: List<Pair<String, String>>,
//        buildType: String?,
//    ): String? {
//        val matched = taskMetadata.any {
//            it.matches(
//                variantName = variantName,
//                productFlavors = productFlavors,
//                buildType = buildType,
//            )
//        }
//        return variantName.takeIf { matched }
//    }

// ── per-variant matching (no regex) ────────────────────────────────

    private fun matchesVariant(
        taskMetadata: TaskMetadata,
        variant: Variant,
    ): Boolean {
        // 0. Match all variants — no specific variant components found
        if (taskMetadata.flavors.isEmpty() && taskMetadata.buildType == null) {
            logger.info(
                "-- Generic Task --\nTask '{}'\nVariant '{}'\n",
                taskMetadata.name,
                variant.name,
            )
            return true
        }

        // 1. Match full variant name
        if (taskMetadata.name.contains(variant.name, ignoreCase = true)) {
            logger.info(
                "-- Full Match --\nTask '{}'\nVariant '{}'\n",
                taskMetadata.name,
                variant.name,
            )
            return true
        }

        // 2. Flavor check — only dimensions that were found in the task
        for ((dimension, variantFlavor) in variant.productFlavors) {
            val taskFlavorForVariantDimension = taskMetadata.flavors[dimension] ?: continue
            if (!(taskFlavorForVariantDimension.equals(variantFlavor, true))) {
                logger.info(
                    "-- Non Matching flavor ---\nTask '{}'\nVariant '{}')\nTask Flavor '{}'\nVariant Flavor '{}'\n",
                    taskMetadata.name,
                    variant.name,
                    taskFlavorForVariantDimension,
                    variantFlavor,
                )
                return false
            } else {
                logger.info(
                    "-- Matching flavor ---\nTask '{}'\nVariant '{}')\nTask Flavor '{}'\nVariant Flavor '{}'\n",
                    taskMetadata.name,
                    variant.name,
                    taskFlavorForVariantDimension,
                    variantFlavor,
                )
            }

        }

        // 3. Build-type check
        if (taskMetadata.buildType != null &&
            !taskMetadata.buildType.equals(variant.buildType, true)
        ) {
            logger.info(
                "-- Non Matching build-type --\nTask '{}'\nVariant '{}'\nTask BuildType '{}'\nVariant BuildType '{}'\n",
                taskMetadata.name,
                variant.name,
                taskMetadata.buildType,
                variant.buildType,
            )
            return false
        } else {
            logger.info(
                "-- Matching build-type --\nTask '{}'\nVariant '{}'\nTask BuildType '{}'\nVariant BuildType '{}'\n",
                taskMetadata.name,
                variant.name,
                taskMetadata.buildType,
                variant.buildType,
            )
        }

        return true
    }

// ── one-time parsing (constructor helper) ──────────────────────────

    private fun resolveTaskMetadata(
        qualifiedTaskName: String,
        allFlavorsPerDimension: Map<String, List<String>>,
        allBuildTypes: List<String>,
    ): TaskMetadata {
        logger.info(
            "-- Metadata --\nTaskName {}\nFlavors {}\nBuildTypes {}\n",
            qualifiedTaskName,
            allFlavorsPerDimension,
            allBuildTypes
        )
        val taskName = qualifiedTaskName.substringAfterLast(':')
        val taskBuildType = allBuildTypes.toAlternationRegex()?.find(taskName)?.value
        val taskFlavors = mutableMapOf<String, String>()
        for ((dimension, flavors) in allFlavorsPerDimension) {
            val regexFlavorsPattern = flavors.toAlternationRegex() ?: continue
            val matchResult = regexFlavorsPattern.find(taskName) ?: continue
            taskFlavors[dimension] = matchResult.value
        }
        logger.info(
            "-- Task Flavors --\nTask {}\nFlavors {}\n",
            qualifiedTaskName,
            taskFlavors,
        )
        return TaskMetadata(
            name = taskName,
            flavors = taskFlavors,
            buildType = taskBuildType,
        )
    }

// ── helpers ────────────────────────────────────────────────────────

    /**
     * Builds a regex alternation from the receiver list.  Every name is
     * capitalised and alternatives are sorted **longest-first** so that
     * `MinApi24` is preferred over `Min` during matching.
     *
     * Example: `["demo", "full"]` → regex `Demo|Full`
     */
    private fun List<String>.toAlternationRegex(): Regex? {
        if (isEmpty()) return null
        return sortedByDescending { it.length }
            .joinToString(separator = "|") {
                Regex.escape(it.run { replaceFirstChar { char -> char.uppercaseChar() } })
            }
            .toRegex()
    }

    private companion object {
        private val logger = Logging.getLogger(VariantMatcher::class.java)
    }
}
