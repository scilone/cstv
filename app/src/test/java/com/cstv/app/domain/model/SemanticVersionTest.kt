package com.cstv.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun `parse valid tag computes versionCode`() {
        val version = SemanticVersion.parse("v1.78.0")
        assertEquals(SemanticVersion(1, 78, 0), version)
        assertEquals(17_800, version!!.versionCode)
    }

    @Test
    fun `parse installed tag matches project formula`() {
        // v1.77.6 -> versionCode 17_706 (voir app/build.gradle.kts)
        assertEquals(17_706, SemanticVersion.parse("v1.77.6")!!.versionCode)
    }

    @Test
    fun `versionCode comparison detects a newer version`() {
        // CA1
        val remote = SemanticVersion.parse("v1.78.0")!!
        assertEquals(true, remote.versionCode > 17_706)
    }

    @Test
    fun `identical or older versions are not newer`() {
        // CA2
        assertEquals(17_706, SemanticVersion.parse("v1.77.6")!!.versionCode)
        assertEquals(true, SemanticVersion.parse("v1.77.5")!!.versionCode < 17_706)
    }

    @Test
    fun `missing v prefix is rejected`() {
        assertNull(SemanticVersion.parse("1.78.0"))
    }

    @Test
    fun `wrong component count is rejected`() {
        // CA15
        assertNull(SemanticVersion.parse("v1.78"))
        assertNull(SemanticVersion.parse("v1.78.0.1"))
    }

    @Test
    fun `non numeric components are rejected`() {
        assertNull(SemanticVersion.parse("v1.a.0"))
    }

    @Test
    fun `minor or patch above 99 is rejected`() {
        assertNull(SemanticVersion.parse("v1.100.0"))
        assertNull(SemanticVersion.parse("v1.0.100"))
    }

    @Test
    fun `blank or null tag is rejected`() {
        assertNull(SemanticVersion.parse(null))
        assertNull(SemanticVersion.parse(""))
    }

    @Test
    fun `overflow prone major is rejected instead of throwing`() {
        assertNull(SemanticVersion.parse("v999999999.0.0"))
    }

    @Test
    fun `major version bump is detected via versionCode division`() {
        // CA3/CA4 : la détection de majeure se fait par le use case (division
        // par 10_000), ce test vérifie seulement que versionCode le permet.
        val major = SemanticVersion.parse("v2.0.0")!!
        val minor = SemanticVersion.parse("v1.78.0")!!
        assertEquals(2, major.versionCode / 10_000)
        assertEquals(1, minor.versionCode / 10_000)
    }
}
