package eu.kanade.tachiyomi.extension.api

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okio.BufferedSource
import okio.buffer
import okio.gzip

/**
 * Model of the version 2 extension store index (`index.pb` / `index.json`) used by tachiyomix (lib 1.6)
 * extension repositories, such as Keiyoushi's.
 *
 * The same schema is published as protobuf (optionally gzipped) and as JSON.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ExtensionIndexV2(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String? = null,
    @ProtoNumber(3) val signingKey: String? = null,
    @ProtoNumber(101) val extensionList: ExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
) {
    @Serializable
    data class ExtensionList(
        @ProtoNumber(1) val extensions: List<IndexExtension> = emptyList(),
    )

    @Serializable
    data class IndexExtension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val contentWarning: ContentWarning = ContentWarning.UNSPECIFIED,
        @ProtoNumber(8) val sources: List<IndexSource> = emptyList(),
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String = "",
    )

    @Serializable
    data class IndexSource(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
    )

    @Suppress("Unused")
    enum class ContentWarning {
        @ProtoNumber(0)
        @JsonNames("CONTENT_WARNING_UNSPECIFIED")
        UNSPECIFIED,

        @ProtoNumber(1)
        @JsonNames("CONTENT_WARNING_SAFE")
        SAFE,

        @ProtoNumber(2)
        @JsonNames("CONTENT_WARNING_MIXED")
        MIXED,

        @ProtoNumber(3)
        @JsonNames("CONTENT_WARNING_NSFW")
        NSFW,
    }

    fun toAvailableExtensions(
        repoUrl: String,
        list: ExtensionList? = extensionList,
    ): List<Extension.Available> {
        return list?.extensions.orEmpty().mapNotNull { ext ->
            val libVersion = ext.extensionLib.toDoubleOrNull() ?: return@mapNotNull null
            val languages = ext.sources.map { it.language }.toSet()
            Extension.Available(
                name = ext.name.substringAfter("Tachiyomi: "),
                pkgName = ext.packageName,
                versionName = ext.versionName,
                versionCode = ext.versionCode,
                libVersion = libVersion,
                lang = if (languages.size == 1) languages.first() else "all",
                isNsfw = ext.contentWarning == ContentWarning.NSFW,
                sources = ext.sources.map {
                    Extension.Available.Source(
                        id = it.id,
                        lang = it.language,
                        name = it.name,
                        baseUrl = it.homeUrl,
                    )
                },
                apkName = ext.resources.apkUrl.substringAfterLast('/'),
                iconUrl = ext.resources.iconUrl,
                repoUrl = repoUrl,
                apkUrl = ext.resources.apkUrl,
            )
        }
    }

    companion object {
        private const val JSON_OBJECT_START = 0x7B.toByte() // '{'

        /**
         * Decodes an index from [source], transparently handling gzip compression and
         * both the JSON and protobuf encodings.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun decode(source: BufferedSource, json: Json, protoBuf: ProtoBuf = ProtoBuf): ExtensionIndexV2 {
            val data = source.decompressIfGzipped().use { it.readByteArray() }
            return if (data.firstOrNull() == JSON_OBJECT_START) {
                json.decodeFromString(ExtensionIndexV2.serializer(), data.decodeToString())
            } else {
                protoBuf.decodeFromByteArray(ExtensionIndexV2.serializer(), data)
            }
        }

        /**
         * Decodes a standalone extension list, referenced by [extensionListUrl], from [source].
         */
        fun decodeList(source: BufferedSource, json: Json, protoBuf: ProtoBuf = ProtoBuf): ExtensionList {
            val data = source.decompressIfGzipped().use { it.readByteArray() }
            return if (data.firstOrNull() == JSON_OBJECT_START) {
                json.decodeFromString(ExtensionList.serializer(), data.decodeToString())
            } else {
                protoBuf.decodeFromByteArray(ExtensionList.serializer(), data)
            }
        }

        private fun BufferedSource.decompressIfGzipped(): BufferedSource {
            val isGzip = try {
                peek().use { it.readShort().toInt() == 0x1F8B }
            } catch (_: Exception) {
                false
            }
            return if (isGzip) gzip().buffer() else this
        }
    }
}
