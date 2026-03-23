package aero.digitalhangar.secrets

import com.android.build.api.variant.Variant
import org.gradle.api.logging.Logging

/**
 * Determines whether a given Android variant is targeted by the currently
 * requested Gradle task(s).
 *
 * The algorithm uses **regex** matching to locate capitalised variant components
 * (product-flavor names and build-type name) inside task names — without
 * hard-coding any task prefix such as `assemble`, `bundle`, `test`, etc.
 *
 * ### How it works
 *
 * 1. For every flavour **dimension** a regex alternation of **all** flavors in
 *    that dimension is built (capitalised first char, sorted longest-first so
 *    that `MinApi24` is tried before `Min`).
 * 2. Each requested task name is searched with the regex.  If a flavor from
 *    that dimension is found it **must** equal the current variant's flavor;
 *    otherwise the task targets a different variant and the match fails.
 * 3. The same check is performed for **build types**.
 * 4. At least **one** variant component (any flavor or build type) must be
 *    present in the task name.  If none is found the task is not
 *    variant-specific (e.g. `clean`, `lint`) and `null` is returned.
 *
 * ### Limitations
 *
 * * Abbreviated task names (e.g. `aD` for `assembleDebug`) are **not**
 *   matched — they will be treated as "no variant component found".
 * * A flavor name that coincidentally equals a capitalised word in the task
 *   suffix (e.g. a flavor named `test` matching `UnitTest`) may cause a false
 *   positive.  Avoid using common Gradle/AGP keywords as flavor names.
 */
object VariantMatcher {

    private val logger = Logging.getLogger(VariantMatcher::class.java)

    /**
     * Returns [variant] when at least one entry in [taskNames] targets the
     * described variant, or `null` when no task carries variant information.
     *
     * @param taskNames               `gradle.startParameter.taskNames`
     * @param allFlavorsPerDimension  every dimension → list of flavor names
     * @param allBuildTypes           every build-type name
     */
    fun resolveRequestedVariant(
        taskNames: List<String>,
        variant: Variant,
        allFlavorsPerDimension: Map<String, List<String>>,
        allBuildTypes: List<String>,
    ): String? {
        val matched: Boolean = taskNames.any { taskName ->
            matchesVariant(
                taskName = taskName.substringAfterLast(':'),
                variant = variant,
                allFlavorsPerDimension = allFlavorsPerDimension,
                allBuildTypes = allBuildTypes,
            )
        }
        return variant.name.takeIf { matched }
    }

    private fun matchesVariant(
        taskName: String,
        variant: Variant,
        allFlavorsPerDimension: Map<String, List<String>>,
        allBuildTypes: List<String>,
    ): Boolean {
        val variantName: String = variant.name
        if (variantName.capitaliseFirst() in taskName) return true

        val flavorsMatch: Boolean? = checkFlavors(
            taskName = taskName,
            variantName = variantName,
            variantFlavors = variant.productFlavors,
            allFlavorsPerDimension = allFlavorsPerDimension
        )
        val buildTypeMatch: Boolean? = checkBuildType(
            taskName = taskName,
            variantName = variantName,
            buildType = variant.buildType,
            allBuildTypes = allBuildTypes
        )

        // A `null` result means "not specified in the task".
        // A `false` means "specified but doesn't match current variant".
        // A `true` means "specified and matches current variant".
        if (flavorsMatch == false || buildTypeMatch == false) return false
        val componentFound = flavorsMatch == true || buildTypeMatch == true
        if (!componentFound) {
            logger.info(
                "**********\nTask '{}' carries no variant component for '{}'\n**********",
                taskName, variantName,
            )
        }
        return componentFound
    }

    /**
     * Checks every flavour dimension in the task name.
     *
     * @return `true`  — at least one dimension's flavor was found and matches
     *         `false` — a dimension's flavor was found but conflicts
     *         `null`  — no flavor from any dimension was found (unspecified)
     */
    private fun checkFlavors(
        taskName: String,
        variantName: String,
        variantFlavors: List<Pair<String, String>>,
        allFlavorsPerDimension: Map<String, List<String>>,
    ): Boolean? {
        logger.info(
            "\n\n\n**********\nChecking Target [ {} ] Flavors for Variant [ {} ]\n**********\n\n",
            taskName, variantName
        )
        var anyFound = false
        for ((index, flavorDimension) in variantFlavors.withIndex()) {
            val (dimension, variantFlavor) = flavorDimension
            val flavorsInVariantDimension = allFlavorsPerDimension[dimension] ?: continue
            logger.info(
                "**********\nVariant [ {} ]\nChecking Flavors [ {} ]\nIn Task [ {} ]",
                variantName, flavorsInVariantDimension, taskName
            )
            val pattern = flavorsInVariantDimension.toRegexPattern() ?: continue
            pattern.find(taskName).let {
                if (it == null) {
                    logger.info(
                        "**********\nNO FLAVOR FOUND\n**********\n\n\n",
                    )
                    continue
                } else {
                    val capitalizedFlavor = variantFlavor.capitaliseFirst()
                    logger.info(
                        "**********\nFOUND\nFlavor [ {} ]\n",
                        it.value,
                    )
                    if (it.value != capitalizedFlavor) {
                        logger.info(
                            "**********\nMatch Dimension --- Skip Variant\n**********\n",
                        )
                        return false
                    } else {
                        logger.info(
                            "**********\nMatch Target Flavor --> Keep searching in variant\n**********\n",
                        )
                    }
                    anyFound = true
                }
            }
        }
        return if (anyFound) true else {
            logger.info(
                "**********\nNO FLAVORS IN TASK\n**********",
            )
            null
        }
    }


    /**
     * Checks whether the task name contains a build-type component.
     *
     * @return `true`  — build type found and matches
     *         `false` — build type found but conflicts
     *         `null`  — no build type found (unspecified)
     */
    private fun checkBuildType(
        taskName: String,
        variantName: String,
        buildType: String?,
        allBuildTypes: List<String>,
    ): Boolean? {
        logger.info(
            "\n\n\n**********\nChecking Target [ {} ] Build Type for Variant [ {} ]\n**********\n",
            taskName, variantName
        )
        if (buildType == null || allBuildTypes.isEmpty()) return null
        val pattern = allBuildTypes.toRegexPattern() ?: return null

        pattern.find(taskName).let {
            if (it == null) {
                logger.info(
                    "**********\nNO BUILD TYPE FOUND\n**********\n\n\n",
                )
                return null
            } else {
                val capitalizedBuildType = buildType.capitaliseFirst()
                logger.info(
                    "**********\nFOUND BUILD TYPE\nTarget Build Type [ {} ]\nCurrent Build Type [ {} ]\n",
                    it.value, capitalizedBuildType
                )
                if (it.value != capitalizedBuildType) {
                    logger.info(
                        "**********\nNot in current Variant --- Skip Variant\n**********\n",
                    )
                    return false
                } else {
                    logger.info(
                        "\n\n\n**********\nMatch Build Type Target -->  Use this variant\n**********\n",
                    )
                    return true
                }
            }
        }
    }

// ── helpers ────────────────────────────────────────────────────────

    /**
     * Builds a regex alternation from the receiver list where every name is
     * capitalised and alternatives are sorted **longest-first** so that
     * `MinApi24` is preferred over `Min` during matching.
     */
    private fun List<String>.toRegexPattern(): Regex? {
        if (isEmpty()) return null
        return sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it.capitaliseFirst()) }
            .toRegex()
    }

    private fun String.capitaliseFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }
}

