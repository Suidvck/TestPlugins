package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document

class AnimeYuzuProvider : MainAPI() {
    override var mainUrl = "https://www.anime-yuzu.com"
    override var name = "Anime Yuzu"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var lang = "th"  // Thai subs/dubs
    override val hasMainPage = true
    override val hasQuickSearch = false

    // Homepage rows (adjust if site has more sections like "Popular" or "Ongoing")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()

        // Latest updates or homepage list
        val homeDoc = app.get("$mainUrl").document
        val latest = homeDoc.select("div.latest-updates div.anime-item")  // Replace with actual selector, e.g., "div.post-grid .post"
            .mapNotNull { toSearchResponse(it) }
        if (latest.isNotEmpty()) items.add(HomePageList("อัพเดทล่าสุด", latest))  // "Latest Updates" in Thai

        // All anime paginated (from /anime/page/N)
        val allUrl = if (page == 1) "$mainUrl/anime" else "$mainUrl/anime/page/$page"
        val allDoc = app.get(allUrl).document
        val allAnime = allDoc.select("div.anime-list div.item")  // Replace with actual, e.g., "ul.anime-list li"
            .mapNotNull { toSearchResponse(it) }
        if (allAnime.isNotEmpty()) items.add(HomePageList("อนิเมะทั้งหมด", allAnime, isHorizontalImages = true))

        return HomePageResponse(items)
    }

    // Search functionality
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"  // Common pattern; adjust if it's /search/query
        val doc = app.get(searchUrl).document
        return doc.select("div.search-results div.result-item")  // Replace with actual selector
            .mapNotNull { toSearchResponse(it) }
    }

    // Parse search/home item to SearchResponse
    private fun toSearchResponse(element: org.jsoup.nodes.Element?): SearchResponse? {
        element ?: return null
        val title = element.selectFirst("h2.title, a.title")?.text()?.trim() ?: return null
        val href = fixUrl(element.selectFirst("a")?.attr("href") ?: return null)
        val poster = fixUrlNull(element.selectFirst("img.poster, img.thumbnail")?.attr("src"))
        val isMovie = title.contains("Movie", true)  // Detect movies vs series
        return if (isMovie) {
            MovieSearchResponse(title, href, this.name, TvType.AnimeMovie, poster)
        } else {
            AnimeSearchResponse(title, href, this.name, TvType.Anime, poster)
        }
    }

    // Load anime details
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.anime-title")?.text()?.trim() ?: throw ErrorLoadingException("No title")
        val poster = fixUrlNull(doc.selectFirst("div.poster img")?.attr("src"))
        val plot = doc.selectFirst("div.synopsis p")?.text()?.trim()
        val year = doc.selectFirst("span.year")?.text()?.toIntOrNull()
        val statusText = doc.selectFirst("span.status")?.text()?.trim()  // e.g., "ยังไม่จบ"
        val tvType = if (url.contains("movie")) TvType.AnimeMovie else TvType.Anime
        val tags = doc.select("div.genres a").map { it.text() }  // Genres

        // Episode list (assuming all on one page; reverse if newest first)
        val episodes = doc.select("ul.episodes li")  // Replace with actual, e.g., "div.ep-list ul li"
            .mapIndexedNotNull { index, ep ->
                val epHref = fixUrl(ep.selectFirst("a")?.attr("href") ?: return@mapIndexedNotNull null)
                val epName = ep.text().trim()  // e.g., "ตอนที่ 1"
                val epNum = Regex("""ตอนที่ (\d+)""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                Episode(epHref, epName, episode = epNum)
            }.reversed()  // Often lists are newest first

        return newAnimeLoadResponse(title, url, tvType) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes)  // Assume subbed; add Dubbed if site has
            showStatus = when {
                statusText?.contains("จบ") == true -> ShowStatus.Completed
                else -> ShowStatus.Ongoing
            }
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

        // Find all servers/embeds (e.g., tabs or list)
        doc.select("div.servers div.server-item, iframe")  // Replace with actual selector for players/servers
            .apmap { server ->
                val embedUrl = server.selectFirst("iframe")?.attr("src")
                    ?: server.attr("data-src")  // Or data-embed
                    ?: return@apmap

                // Load common extractors (Dood, StreamSB, Mixdrop, etc.)
                loadExtractor(embedUrl, data, subtitleCallback, callback)
            }

        // If subs are separate (rare), add here: subtitleCallback(SubtitleFile("Thai", subUrl))

        return true
    }

    private fun fixUrl(url: String) = if (url.startsWith("/")) mainUrl + url else url
    private fun fixUrlNull(url: String?) = url?.let { fixUrl(it) }
}