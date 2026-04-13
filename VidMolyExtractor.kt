package eu.kanade.tachiyomi.lib.vidmolyextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.internal.EMPTY_HEADERS

class VidMolyExtractor(private val client: OkHttpClient, private val headers: Headers = EMPTY_HEADERS) {

    private val playlistUtils by lazy { PlaylistUtils(client) }

    private val sourcesRegex = Regex("sources: (.*?]),")
    private val urlsRegex = Regex("""file:\s*["'](.*?)["']""")

    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        val baseUrl = "https://${java.net.URI(url).host}"
        val newHeaders = headers.newBuilder()
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .build()
            
        var response = client.newCall(
            GET(url, newHeaders.newBuilder().set("Sec-Fetch-Dest", "iframe").build())
        ).execute()

        var document = response.asJsoup()

        val redirectScript = document.selectFirst("script:containsData(window.location.replace)")
        if (redirectScript != null) {
            val redirectUrl = redirectScript.data().substringAfter("window.location.replace('").substringBefore("')")
            response = client.newCall(
                GET(redirectUrl, newHeaders.newBuilder().set("Sec-Fetch-Dest", "iframe").build())
            ).execute()
            document = response.asJsoup()
        }

        val script = document.selectFirst("script:containsData(sources)") ?: return emptyList()
        val sources = sourcesRegex.find(script.data())!!.groupValues[1]
        val urls = urlsRegex.findAll(sources).map { it.groupValues[1] }.toList()
        return urls.flatMap {
            playlistUtils.extractFromHls(it,
                videoNameGen = { quality -> "${prefix}VidMoly - $quality" },
                masterHeaders = newHeaders,
                videoHeaders = newHeaders,
            )
        }
    }
}
