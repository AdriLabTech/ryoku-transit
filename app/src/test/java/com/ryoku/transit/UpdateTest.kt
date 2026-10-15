package com.ryoku.transit

import com.ryoku.transit.data.ReleaseParser
import com.ryoku.transit.data.UpdateConfig
import com.ryoku.transit.domain.Version
import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
    private val sha = "a".repeat(64)
    private fun release(tag: String = "v0.2.0", extra: String = "", url: String = "${UpdateConfig.APK_URL_PREFIX}v0.2.0/ryoku-transit-0.2.0.apk") =
        """{"tag_name":"$tag","body":"Novedades","draft":false,"prerelease":false $extra,
        "assets":[{"name":"notas.txt","browser_download_url":"${UpdateConfig.APK_URL_PREFIX}x/notas.txt"},
        {"name":"ryoku-transit-0.2.0.apk","size":1234,"digest":"sha256:$sha","browser_download_url":"$url"}]}"""

    @Test fun versionOrdering() {
        assertTrue(Version.parse("v0.10.0")!! > Version.parse("0.9.9")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
        assertEquals(Version(0, 2, 0), Version.parse("v0.2.0-beta+7"))
        assertEquals(Version.parse("0.1.0"), Version.parse(" v0.1.0 "))
    }

    @Test fun versionRejectsGarbage() {
        assertNull(Version.parse("latest")); assertNull(Version.parse("1.2")); assertNull(Version.parse(""))
    }

    @Test fun parsesApkAssetWithDigest() {
        val r = ReleaseParser.parse(release())!!
        assertEquals(Version(0, 2, 0), r.version)
        assertEquals(1234L, r.apkSize); assertEquals(sha, r.sha256); assertEquals("Novedades", r.notes)
        assertTrue(r.apkUrl.endsWith("ryoku-transit-0.2.0.apk"))
    }

    @Test fun ignoresDraftsAndPrereleases() {
        assertNull(ReleaseParser.parse(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(ReleaseParser.parse(release().replace("\"prerelease\":false", "\"prerelease\":true")))
    }

    @Test fun rejectsForeignApkHostAndMissingApk() {
        assertNull(ReleaseParser.parse(release(url = "https://evil.example/ryoku-transit-0.2.0.apk")))
        assertNull(ReleaseParser.parse("""{"tag_name":"v0.2.0","assets":[]}"""))
        assertNull(ReleaseParser.parse(release(tag = "nightly")))
    }

    @Test fun missingDigestIsNull() {
        assertNull(ReleaseParser.parse(release().replace("sha256:$sha", ""))!!.sha256)
    }
}
