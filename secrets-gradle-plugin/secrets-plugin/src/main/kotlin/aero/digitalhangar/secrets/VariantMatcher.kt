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
     * Returns [variant.name] when at least one entry in [taskNames] targets the
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
        return if (matched) {
            variant.name
        } else null
    }

    private fun matchesVariant(
        taskName: String,
        variant: Variant,
        allFlavorsPerDimension: Map<String, List<String>>,
        allBuildTypes: List<String>,
    ): Boolean {
        val variantName: String = variant.name
        if (variantName.capitaliseFirst() in taskName) return true

        val flavorsMatch: Boolean = checkFlavors(
            taskName = taskName,
            variantFlavors = variant.productFlavors,
            allFlavorsPerDimension = allFlavorsPerDimension
        )
        val buildTypeMatch: Boolean? = checkBuildType(
            taskName = taskName,
            variantName = variantName,
            buildType = variant.buildType,
            allBuildTypes = allBuildTypes
        )

        // A `null` result means "not specified in the task" → compatible.
        // A `false` means "specified but conflicts" → incompatible.
//        if (flavorsMatch == false || buildTypeMatch == false) return false

        // At least one component must have been positively matched.
        val componentFound = flavorsMatch == true || buildTypeMatch == true
        if (!componentFound) {
//            logger.info(
//                "**********\nTask '{}' carries no variant component for '{}'\n**********",
//                taskName, variantName,
//            )
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
        variantFlavors: List<Pair<String, String>>,
        allFlavorsPerDimension: Map<String, List<String>>,
    ): Boolean {
        logger.info(
            "**********\nCurrent Variant Flavors\nflavors={}\n**********",
            variantFlavors
        )

        var anyFound = false
        for ((dimension, variantFlavor) in variantFlavors) {
            // TODO Regex to find current variant's flavor in the task name.
            // TODO if found anyFound = true

//            val allFlavorsInDimension = allFlavorsPerDimension[dimension] ?: continue
//            val flavorsRegexPattern = allFlavorsInDimension.toRegexPattern() ?: continue
//            val match = flavorsRegexPattern.find(taskName) ?: continue
//            val expected = variantFlavor.capitaliseFirst()

//            if (match.value != expected) {
//                logger.info(
//                    "**********\nFlavor Pattern\nflavorsInVariantDimension={}\npattern={}\n**********",
//                    flavorsInVariantDimension,
//                    pattern
//                )
//                logger.info(
//                    "**********\nMatch Value != Expected\nTask={}\ndimension={}\nmatchValue={}\nvariant={}\nexpects={}\n**********",
//                    taskName, dimension, match.value, variantName, expected,
//                )
//                return false
//            }
        }

        return anyFound
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
            "**********\nCurrent Variant Build Type\nbuildType={}\n**********",
            buildType
        )
        if (buildType == null || allBuildTypes.isEmpty()) return null
        val pattern = allBuildTypes.toRegexPattern() ?: return null
        val match = pattern.find(taskName) ?: return null

        val expected = buildType.capitaliseFirst()
        if (match.value != expected) {
//            logger.info(
//                "**********\nTask '{}': build type '{}' ≠ variant '{}' build type '{}'\n**********",
//                taskName, match.value, variantName, expected,
//            )
            return false
        }
        return true
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

