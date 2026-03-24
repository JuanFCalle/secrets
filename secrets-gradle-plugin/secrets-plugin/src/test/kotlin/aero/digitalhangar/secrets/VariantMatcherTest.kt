package aero.digitalhangar.secrets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VariantMatcherTest {

    private val allFlavorsPerDimension = mapOf(
        "mode" to listOf("demo", "full"),
        "api" to listOf("min21", "min24"),
    )

    private val allBuildTypes = listOf("debug", "release")

    @Test
    fun `generic assemble task matches all variants`() {
        val matcher = VariantMatcher(
            taskNames = listOf("assemble"),
            allFlavorsPerDimension = allFlavorsPerDimension,
            allBuildTypes = allBuildTypes,
        )

        assertEquals(
            "demoMin21Debug",
            matcher.resolveVariant(
                variantName = "demoMin21Debug",
                productFlavors = listOf("mode" to "demo", "api" to "min21"),
                buildType = "debug",
            ),
        )
        assertEquals(
            "fullMin24Release",
            matcher.resolveVariant(
                variantName = "fullMin24Release",
                productFlavors = listOf("mode" to "full", "api" to "min24"),
                buildType = "release",
            ),
        )
    }

    @Test
    fun `dimension not specified in task acts as wildcard`() {
        val matcher = VariantMatcher(
            taskNames = listOf("assembleMin21Debug"),
            allFlavorsPerDimension = allFlavorsPerDimension,
            allBuildTypes = allBuildTypes,
        )

        assertEquals(
            "demoMin21Debug",
            matcher.resolveVariant(
                variantName = "demoMin21Debug",
                productFlavors = listOf("mode" to "demo", "api" to "min21"),
                buildType = "debug",
            ),
        )
        assertEquals(
            "fullMin21Debug",
            matcher.resolveVariant(
                variantName = "fullMin21Debug",
                productFlavors = listOf("mode" to "full", "api" to "min21"),
                buildType = "debug",
            ),
        )
        assertNull(
            matcher.resolveVariant(
                variantName = "demoMin24Debug",
                productFlavors = listOf("mode" to "demo", "api" to "min24"),
                buildType = "debug",
            ),
        )
        assertNull(
            matcher.resolveVariant(
                variantName = "demoMin21Release",
                productFlavors = listOf("mode" to "demo", "api" to "min21"),
                buildType = "release",
            ),
        )
    }

    @Test
    fun `build type not specified in task acts as wildcard`() {
        val matcher = VariantMatcher(
            taskNames = listOf("assembleDemoMin21"),
            allFlavorsPerDimension = allFlavorsPerDimension,
            allBuildTypes = allBuildTypes,
        )

        assertEquals(
            "demoMin21Debug",
            matcher.resolveVariant(
                variantName = "demoMin21Debug",
                productFlavors = listOf("mode" to "demo", "api" to "min21"),
                buildType = "debug",
            ),
        )
        assertEquals(
            "demoMin21Release",
            matcher.resolveVariant(
                variantName = "demoMin21Release",
                productFlavors = listOf("mode" to "demo", "api" to "min21"),
                buildType = "release",
            ),
        )
        assertNull(
            matcher.resolveVariant(
                variantName = "fullMin21Debug",
                productFlavors = listOf("mode" to "full", "api" to "min21"),
                buildType = "debug",
            ),
        )
    }

    @Test
    fun `module qualified task still matches exact variant`() {
        val matcher = VariantMatcher(
            taskNames = listOf(":app:assembleDemoMin21Debug"),
            allFlavorsPerDimension = allFlavorsPerDimension,
            allBuildTypes = allBuildTypes,
        )

        assertEquals(
            "demoMin21Debug",
            matcher.resolveVariant(
                variantName = "demoMin21Debug",
                productFlavors = listOf("mode" to "demo", "api" to "min21"),
                buildType = "debug",
            ),
        )
        assertNull(
            matcher.resolveVariant(
                variantName = "fullMin21Debug",
                productFlavors = listOf("mode" to "full", "api" to "min21"),
                buildType = "debug",
            ),
        )
    }

    @Test
    fun `generic task among multiple requested tasks keeps all variants accepted`() {
        val matcher = VariantMatcher(
            taskNames = listOf("assemble", "installDemoMin21Debug"),
            allFlavorsPerDimension = allFlavorsPerDimension,
            allBuildTypes = allBuildTypes,
        )

        assertEquals(
            "fullMin24Release",
            matcher.resolveVariant(
                variantName = "fullMin24Release",
                productFlavors = listOf("mode" to "full", "api" to "min24"),
                buildType = "release",
            ),
        )
    }
}

