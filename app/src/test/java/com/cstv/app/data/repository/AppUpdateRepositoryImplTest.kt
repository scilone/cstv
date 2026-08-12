package com.cstv.app.data.repository

import com.cstv.app.data.remote.api.GithubReleaseApiService
import com.cstv.app.data.remote.dto.GithubReleaseAssetDto
import com.cstv.app.data.remote.dto.GithubReleaseDto
import com.cstv.app.data.update.ApkInspector
import com.cstv.app.data.update.InspectedApk
import com.cstv.app.data.update.UpdateApkStore
import com.cstv.app.domain.model.AppUpdateCheckResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.io.File

private const val APP_ID = "com.cstv.app"
private const val ASSET_URL = "https://github.com/scilone/cstv/releases/download/v1.78.0/app-release.apk"

private class NoopApkInspector : ApkInspector {
    override fun inspect(file: File): InspectedApk? = null
}

/** Reconnaît tout fichier comme un APK valide de `expectedVersionCode` pour `APP_ID`. */
private class AcceptingApkInspector(private val expectedVersionCode: Int) : ApkInspector {
    override fun inspect(file: File): InspectedApk? = InspectedApk(APP_ID, expectedVersionCode)
}

class AppUpdateRepositoryImplTest {

    @get:Rule
    val globalTimeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val apiService: GithubReleaseApiService = mock()

    private fun repository(inspector: ApkInspector = NoopApkInspector()): AppUpdateRepositoryImpl {
        val store = UpdateApkStore(tempFolder.newFolder("app_updates"), inspector, APP_ID)
        return AppUpdateRepositoryImpl(apiService, store)
    }

    private fun dto(
        tag: String? = "v1.78.0",
        draft: Boolean? = false,
        prerelease: Boolean? = false,
        assets: List<GithubReleaseAssetDto>? = listOf(
            GithubReleaseAssetDto(name = "app-release.apk", browserDownloadUrl = ASSET_URL, size = 1_000L)
        )
    ) = GithubReleaseDto(tagName = tag, draft = draft, prerelease = prerelease, assets = assets)

    @Test
    fun `fetchLatestRelease maps a valid release`() = runTest {
        whenever(apiService.getLatestRelease()).thenReturn(dto())

        val result = repository().fetchLatestRelease()

        assertTrue(result is AppUpdateCheckResult.ReleaseFound)
        val release = (result as AppUpdateCheckResult.ReleaseFound).release
        assertEquals(17_800, release.version.versionCode)
        assertEquals(ASSET_URL, release.assetDownloadUrl)
    }

    @Test
    fun `CA15 an invalid tag is NoUsableRelease`() = runTest {
        whenever(apiService.getLatestRelease()).thenReturn(dto(tag = "not-a-version"))
        assertEquals(AppUpdateCheckResult.NoUsableRelease, repository().fetchLatestRelease())
    }

    @Test
    fun `CA14 a response without an apk asset is NoUsableRelease`() = runTest {
        whenever(apiService.getLatestRelease()).thenReturn(
            dto(assets = listOf(GithubReleaseAssetDto("readme.txt", "https://github.com/x/readme.txt", 10L)))
        )
        assertEquals(AppUpdateCheckResult.NoUsableRelease, repository().fetchLatestRelease())
    }

    @Test
    fun `a draft release is NoUsableRelease`() = runTest {
        whenever(apiService.getLatestRelease()).thenReturn(dto(draft = true))
        assertEquals(AppUpdateCheckResult.NoUsableRelease, repository().fetchLatestRelease())
    }

    @Test
    fun `a prerelease is NoUsableRelease`() = runTest {
        whenever(apiService.getLatestRelease()).thenReturn(dto(prerelease = true))
        assertEquals(AppUpdateCheckResult.NoUsableRelease, repository().fetchLatestRelease())
    }

    @Test
    fun `an asset URL outside the expected github path is rejected`() = runTest {
        whenever(apiService.getLatestRelease()).thenReturn(
            dto(assets = listOf(GithubReleaseAssetDto("app-release.apk", "https://evil.example.com/app-release.apk", 10L)))
        )
        assertEquals(AppUpdateCheckResult.NoUsableRelease, repository().fetchLatestRelease())
    }

    @Test
    fun `a non-https asset URL is rejected`() = runTest {
        whenever(apiService.getLatestRelease()).thenReturn(
            dto(assets = listOf(GithubReleaseAssetDto("app-release.apk", ASSET_URL.replace("https", "http"), 10L)))
        )
        assertEquals(AppUpdateCheckResult.NoUsableRelease, repository().fetchLatestRelease())
    }

    @Test
    fun `network failure is reported without throwing`() = runTest {
        // `thenAnswer` plutôt que `thenThrow` : IOException est une exception
        // vérifiée, invalide pour `thenThrow` sur la signature bytecode d'une
        // fonction suspend (pas de `throws` déclaré).
        whenever(apiService.getLatestRelease()).thenAnswer { throw java.io.IOException("timeout") }

        val result = repository().fetchLatestRelease()

        assertTrue(result is AppUpdateCheckResult.Failure)
    }

    @Test
    fun `downloadApk streams the asset and promotes the part file`() = runBlocking {
        whenever(apiService.getLatestRelease()).thenReturn(dto())
        val bytes = ByteArray(5) { it.toByte() }
        whenever(apiService.downloadAsset(ASSET_URL)).thenReturn(
            Response.success(bytes.toResponseBody(null))
        )
        // F35-R2 : l'inspecteur doit reconnaître le fichier fraîchement promu
        // pour que le téléchargement soit accepté -- un inspecteur qui ne
        // renvoie jamais rien (voir le test de rejet ci-dessous) ne prouverait
        // pas que la validation post-téléchargement a réellement lieu.
        val repo = repository(AcceptingApkInspector(17_800))
        val result = repo.fetchLatestRelease() as AppUpdateCheckResult.ReleaseFound
        val release = result.release.copy(assetSizeBytes = bytes.size.toLong())

        val progress = mutableListOf<Int>()
        val file = repo.downloadApk(release) { progress.add(it) }

        assertTrue(file.exists())
        assertEquals(bytes.size.toLong(), file.length())
        assertEquals(100, progress.last())
    }

    @Test
    fun `F35-R2 a freshly downloaded apk that fails inspection is rejected, not installed`() = runBlocking {
        whenever(apiService.getLatestRelease()).thenReturn(dto())
        val bytes = ByteArray(5) { it.toByte() }
        whenever(apiService.downloadAsset(ASSET_URL)).thenReturn(Response.success(bytes.toResponseBody(null)))
        // NoopApkInspector : jamais reconnu comme un APK valide -- simule une
        // réponse tronquée ou un asset au mauvais package/version.
        val repo = repository(NoopApkInspector())
        val result = repo.fetchLatestRelease() as AppUpdateCheckResult.ReleaseFound
        val release = result.release.copy(assetSizeBytes = bytes.size.toLong())

        try {
            repo.downloadApk(release) {}
            org.junit.Assert.fail("un APK non reconnu par l'inspecteur ne doit jamais être promu comme fichier final")
        } catch (e: java.io.IOException) {
            // attendu : le repository refuse le fichier avant `PackageInstaller`.
        }
    }

    @Test
    fun `F35-R1 downloadApk completes when run on an injected dispatcher`() = runBlocking {
        // Preuve JVM disponible pour le correctif F35-R1 : le repository
        // accepte un dispatcher injecté (comme VodRepositoryImpl/
        // SeriesRepositoryImpl) au lieu d'utiliser implicitement le
        // dispatcher de l'appelant -- c'est ce détachement qui, en
        // production, sort la copie réseau->disque du thread Main du
        // ViewModel. La preuve que le thread Main n'est jamais bloqué
        // relève d'un test instrumenté, hors périmètre JVM (AGENTS.md).
        whenever(apiService.getLatestRelease()).thenReturn(dto())
        val bytes = ByteArray(5) { it.toByte() }
        whenever(apiService.downloadAsset(ASSET_URL)).thenReturn(Response.success(bytes.toResponseBody(null)))
        val store = UpdateApkStore(tempFolder.newFolder("app_updates"), AcceptingApkInspector(17_800), APP_ID)
        val repo = AppUpdateRepositoryImpl(apiService, store, kotlinx.coroutines.Dispatchers.Unconfined)
        val result = repo.fetchLatestRelease() as AppUpdateCheckResult.ReleaseFound
        val release = result.release.copy(assetSizeBytes = bytes.size.toLong())

        val file = repo.downloadApk(release) {}

        assertTrue(file.exists())
    }
}
