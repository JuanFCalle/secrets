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
 * 1. **Full-name shortcut** – If the capitalised variant name (e.g.
 *    `DemoMin24Debug`) appears verbatim inside any task name the variant is
 *    immediately accepted.
 *
 * 2. **Per-dimension flavor check** – For every flavour dimension a regex
 *    alternation of *all* flavors in that dimension is built (capitalised,
 *    sorted longest-first so `MinApi24` is tried before `Min`).
 *    - If a flavor from that dimension is **found** in the task name it
 *      **must** equal the current variant's flavor; otherwise the variant is
 *      rejected.
 *    - If **no** flavor from that dimension is found the dimension is treated
 *      as *unspecified* (the task does not filter by that dimension).
 *
 * 3. **Build-type check** – Same logic applied to the set of all build types.
 *
 * 4. **At least one component required** – If none of the flavors or the build
 *    type was found in the task name the task is not variant-specific
 *    (e.g. `clean`, `lint`) and `null` is returned.
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
     * Returns the [variant]'s name when at least one entry in [taskNames]
     * targets that variant, or `null` when no task carries variant information.
     *
     * @param taskNames               `gradle.startParameter.taskNames`
     * @param variant                 the variant under inspection
     * @param allFlavorsPerDimension  every dimension → list of flavor names
     * @param allBuildTypes           every build-type name
     */
    fun resolveRequestedVariant(
        taskNames: List<String>,
        variant: Variant,
        allFlavorsPerDimension: Map<String, List<String>>,
        allBuildTypes: List<String>,
    ): String? {
        val matched = taskNames.any { taskName ->
            matchesVariant(
                taskName = taskName.substringAfterLast(':'),
                variant = variant,
                allFlavorsPerDimension = allFlavorsPerDimension,
                allBuildTypes = allBuildTypes,
            )
        }
        return variant.name.takeIf { matched }
    }

    // ── core matching ──────────────────────────────────────────────────

    /**
     * Checks whether [taskName] (without module prefix) targets [variant].
     *
     * Three-valued per-component logic:
     * - `true`  → component found **and** matches current variant
     * - `false` → component found but belongs to a **different** variant
     * - `null`  → component absent (dimension/build-type unspecified)
     */
    private fun matchesVariant(
        taskName: String,
        variant: Variant,
        allFlavorsPerDimension: Map<String, List<String>>,
        allBuildTypes: List<String>,
    ): Boolean {
        // ── 1. Fast path: full capitalised variant name in task ──────
        if (variant.name.capitaliseFirst() in taskName) {
            logger.info("Task '{}' contains full variant name '{}'", taskName, variant.name)
            return true
        }

        // ── 2. Per-dimension flavor matching ────────────────────────
        val flavorResult: Boolean? = matchFlavors(
            taskName = taskName,
            variant = variant,
            allFlavorsPerDimension = allFlavorsPerDimension,
        )
        // Early exit: a flavor from some dimension was found but it
        // belongs to a different variant.
        if (flavorResult == false) return false

        // ── 3. Build-type matching ──────────────────────────────────
        val buildTypeResult: Boolean? = matchBuildType(
            taskName = taskName,
            variant = variant,
            allBuildTypes = allBuildTypes,
        )
        if (buildTypeResult == false) return false

        // ── 4. At least one component must have been present ────────
        val hasAnyComponent = flavorResult == true || buildTypeResult == true
        if (!hasAnyComponent) {
            logger.info(
                "Task '{}' carries no variant component — not variant-specific",
                taskName,
            )
        }
        return hasAnyComponent
    }

    // ── flavor matching ────────────────────────────────────────────────

    /**
     * Inspects every flavour dimension independently.
     *
     * @return `true`  if ≥ 1 dimension's flavor was found **and** all found
     *                 flavors match the variant
     *         `false` if any dimension's flavor was found but **conflicts**
     *         `null`  if no flavor from any dimension appears in [taskName]
     */
    private fun matchFlavors(
        taskName: String,
        variant: Variant,
        allFlavorsPerDimension: Map<String, List<String>>,
    ): Boolean? {
        var anyFound = false

        for ((dimension, variantFlavor) in variant.productFlavors) {
            val allFlavors: List<String> =
                allFlavorsPerDimension[dimension] ?: continue
            val pattern: Regex = allFlavors.toAlternationRegex() ?: continue

            val match = pattern.find(taskName)
            if (match == null) {
                // Dimension is not specified in the task — that's fine.
                logger.info(
                    "Dimension '{}': no flavor found in task '{}'",
                    dimension, taskName,
                )
                continue
            }

            val expected = variantFlavor.capitaliseFirst()
            if (match.value != expected) {
                logger.info(
                    "Dimension '{}': found '{}' but variant needs '{}' — skip",
                    dimension, match.value, expected,
                )
                return false
            }

            logger.info(
                "Dimension '{}': flavor '{}' matches variant",
                dimension, match.value,
            )
            anyFound = true
        }

        return if (anyFound) true else null
    }

    // ── build-type matching ────────────────────────────────────────────

    /**
     * @return `true`  if a build type is found **and** matches the variant
     *         `false` if a build type is found but **conflicts**
     *         `null`  if no build type appears in [taskName]
     */
    private fun matchBuildType(
        taskName: String,
        variant: Variant,
        allBuildTypes: List<String>,
    ): Boolean? {
        val buildType: String = variant.buildType ?: return null
        if (allBuildTypes.isEmpty()) return null

        val pattern: Regex = allBuildTypes.toAlternationRegex() ?: return null
        val match = pattern.find(taskName) ?: run {
            logger.info("No build type found in task '{}'", taskName)
            return null
        }

        val expected = buildType.capitaliseFirst()
        return if (match.value == expected) {
            logger.info("Build type '{}' matches variant", match.value)
            true
        } else {
            logger.info(
                "Build type '{}' found but variant needs '{}' — skip",
                match.value, expected,
            )
            false
        }
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
            .joinToString(separator = "|") { Regex.escape(it.capitaliseFirst()) }
            .toRegex()
    }

    private fun String.capitaliseFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }
}
