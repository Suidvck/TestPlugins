package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://www.anime-yuzu.com"
    override var name = "Anime Yuzu"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var lang = "th"  // Thai subs/dubs
    override val hasMainPage = true
    override val hasQuickSearch = false

    // Homepage rows
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()

        // Latest updates from homepage
        val homeDoc = app.get(mainUrl).document
        val latest = homeDoc.select("article.item.tvshows, article.item.movies")  // Items on homepage
            .mapNotNull { toSearchResponse(it) }
        if (latest.isNotEmpty()) items.add(HomePageList("อัพเดทล่าสุด", latest, isHorizontalImages = true))  // Latest Updates

        // All anime paginated from /catalog/page/N
        val allUrl = if (page == 1) "$mainUrl/catalog" else "$mainUrl/catalog/page/$page"
        val allDoc = app.get(allUrl).document
        val allAnime = allDoc.select("article.item")  // Catalog items
            .mapNotNull { toSearchResponse(it) }
        if (allAnime.isNotEmpty()) items.add(HomePageList("อนิเมะทั้งหมด", allAnime, isHorizontalImages = true))

        return newHomePageResponse(items, true)
    }

    // Search functionality
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = app.get(searchUrl).document
        return doc.select("article.item.tvshows, article.item.movies")  // Search result items
            .mapNotNull { toSearchResponse(it) }
    }

    // Parse search/home item to SearchResponse
    private fun toSearchResponse(element: org.jsoup.nodes.Element?): SearchResponse? {
        element ?: return null
        val titleElem = element.selectFirst(".movie-title")
        val title = titleElem?.text()?.trim() ?: return null
        val href = fixUrl(element.selectFirst("a")?.attr("href") ?: return null)
        val poster = fixUrlNull(element.selectFirst(".poster img")?.attr("src") ?: element.selectFirst(".poster img")?.attr("data-lazy-src"))
        val type = element.selectFirst(".features-type")?.text()?.trim() ?: "ซับไทย"  // e.g., ซับไทย or พากย์ไทย
        val isMovie = title.contains("Movie", true) || title.contains("เดอะมูฟวี่", true)

        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.AnimeMovie) {
                this.posterUrl = poster
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            }
        }
    }

    // Load anime details
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: throw ErrorLoadingException("No title")
        val poster = fixUrlNull(doc.selectFirst("div.poster img, div.thumbnail img")?.attr("src"))
        val plot = doc.selectFirst("div.entry-content p, .description p")?.text()?.trim()
        val year = doc.selectFirst("span.year")?.text()?.toIntOrNull()
        val statusText = title.contains("ยังไม่จบ") || title.contains("Ongoing")  // Derive from title or add selector if available
        val tvType = if (url.contains("movie") || title.contains("Movie")) TvType.AnimeMovie else TvType.Anime
        val tags = doc.select("div.genre-info a, span.genre a").map { it.text() }  // Genres

        // Episode list (reverse as newest first)
        val episodes = doc.select("ul.episodelist li, div.eplist ul li")  // Episode list selector
            .mapIndexedNotNull { index, ep ->
                val epHref = fixUrl(ep.selectFirst("a")?.attr("href") ?: return@mapIndexedNotNull null)
                val epName = ep.text().trim()  // e.g., "ตอนที่ 1"
                val epNum = Regex("""ตอนที่ (\d+)""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                newEpisode(epHref) {
                    this.name = epName
                    this.episode = epNum
                }
            }.reversed()

        return newAnimeLoadResponse(title, url, tvType) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes)  // Assume subbed; change to Dubbed for พากย์ไทย
            showStatus = if (statusText) ShowStatus.Ongoing else ShowStatus.Completed
        }
    }

    // Extract video links from episode page
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // Find all servers/embeds
        doc.select("div.player iframe, div.servers div.server-item").forEach { server ->
            val embedUrl = server.attr("src").ifEmpty { server.attr("data-src") } ?: return@forEach
            loadExtractor(embedUrl, data, subtitleCallback, callback)
        }

        // Add subs if separate (e.g., Thai subs)
        // subtitleCallback(SubtitleFile("Thai", subUrl))  // If available

        return true
    }

    private fun fixUrl(url: String) = if (url.startsWith("/")) mainUrl + url else url
    private fun fixUrlNull(url: String?) = url?.let { fixUrl(it) }
}
