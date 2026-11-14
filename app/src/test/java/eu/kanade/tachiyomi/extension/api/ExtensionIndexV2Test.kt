package eu.kanade.tachiyomi.extension.api

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.buffer
import okio.gzip
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class ExtensionIndexV2Test {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val index = ExtensionIndexV2(
        name = "Test store",
        badgeLabel = "TEST",
        signingKey = "abc123",
        extensionList = ExtensionIndexV2.ExtensionList(
            extensions = listOf(
                ExtensionIndexV2.IndexExtension(
                    name = "Tachiyomi: Example",
                    packageName = "eu.kanade.tachiyomi.extension.en.example",
                    resources = ExtensionIndexV2.Resources(
                        apkUrl = "https://example.org/apk/example-v1.6.1.apk",
                        iconUrl = "https://example.org/icon/example.png",
                    ),
                    extensionLib = "1.6",
                    versionCode = 1,
                    versionName = "1.6.1",
                    contentWarning = ExtensionIndexV2.ContentWarning.NSFW,
                    sources = listOf(
                        ExtensionIndexV2.IndexSource(
                            id = 1,
                            name = "Example",
                            language = "en",
                            homeUrl = "https://example.org",
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `protobuf index is decoded and mapped to available extensions`() {
        val bytes = ProtoBuf.encodeToByteArray(index)

        val decoded = ExtensionIndexV2.decode(Buffer().write(bytes), json)
        val available = decoded.toAvailableExtensions("https://example.org")

        available shouldHaveSize 1
        with(available.single()) {
            name shouldBe "Example"
            pkgName shouldBe "eu.kanade.tachiyomi.extension.en.example"
            libVersion shouldBe 1.6
            lang shouldBe "en"
            isNsfw shouldBe true
            apkUrl shouldBe "https://example.org/apk/example-v1.6.1.apk"
            apkName shouldBe "example-v1.6.1.apk"
            repoUrl shouldBe "https://example.org"
        }
    }

    @Test
    fun `gzipped protobuf index is decoded`() {
        val raw = ProtoBuf.encodeToByteArray(index)
        val gzipped = Buffer().also { sink -> sink.gzip().buffer().use { it.write(raw) } }

        val decoded = ExtensionIndexV2.decode(gzipped, json)

        decoded.name shouldBe "Test store"
        decoded.extensionList!!.extensions shouldHaveSize 1
    }

    @Test
    fun `json index with prefixed content warning names is decoded`() {
        val source = Buffer().writeUtf8(
            """
            {
              "name": "Test store",
              "badgeLabel": "TEST",
              "signingKey": "abc123",
              "contact": { "website": "https://example.org" },
              "extensionList": {
                "extensions": [{
                  "name": "Example",
                  "packageName": "pkg.example",
                  "resources": { "apkUrl": "https://example.org/a.apk", "iconUrl": "https://example.org/a.png" },
                  "extensionLib": "1.6",
                  "versionCode": 3,
                  "versionName": "1.6.3",
                  "contentWarning": "CONTENT_WARNING_SAFE",
                  "sources": [
                    { "id": 5, "name": "A", "language": "en" },
                    { "id": 6, "name": "B", "language": "de" }
                  ]
                }]
              }
            }
            """.trimIndent(),
        )

        val available = ExtensionIndexV2.decode(source, json).toAvailableExtensions("https://example.org")

        with(available.single()) {
            versionCode shouldBe 3
            lang shouldBe "all"
            isNsfw shouldBe false
            sources shouldHaveSize 2
        }
    }
}
