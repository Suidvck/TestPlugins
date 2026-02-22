package com.example

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

class ExampleProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://ezmovie.movie/"
    override var name = "ezmov"
    override val supportedTypes = setOf(TvType.Movie)

    override var lang = "th"

    // Enable this when your provider has a main page
    override val hasMainPage = true

    // This function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // 1. สร้าง URL สำหรับการค้นหา โดยนำ query (คำค้นหา) มาต่อท้าย
        val url = "$mainUrl/search-result?search=$query"

        // 2. ใช้คำสั่ง app.get เพื่อดึงหน้าเว็บ HTML ทั้งหมดจาก URL นั้นออกมา
        val response = app.get(url).text

        // 3. ใช้ Jsoup แปลงข้อความ HTML ให้เป็น "Document" ที่เราสามารถเลือกเฉพาะส่วนที่ต้องการได้
        val document = Jsoup.parse(response)

        // 4. สั่งให้เลือกเฉพาะ "กล่องหนัง" แต่ละกล่อง (มักใช้ CSS Selector เช่น div.result-item)
        return document.select("div.-item-wrapper animated fadeInUpShortly").mapNotNull {

            // 5. ในแต่ละกล่อง ให้ดึง 'ชื่อหนัง' จากแท็กที่กำหนด
            val title = it.selectFirst("div.-title js-text-show-more")?.text() ?: return@mapNotNull null

            // 6. ดึง 'ลิงก์หน้าหนัง' เพื่อใช้เปิดดูรายละเอียดในขั้นตอนถัดไป
            val href = it.selectFirst("div.btn btn-primary -btn-icon")?.attr("href") ?: return@mapNotNull null

            // 7. ดึง 'URL รูปภาพ' เพื่อเอามาโชว์เป็นหน้าปก
            val poster = it.selectFirst("img")?.attr("src")

            // 8. ส่งข้อมูลที่ดึงได้กลับไปในรูปแบบ MovieSearchResponse เพื่อแสดงผลในแอป
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }
}
