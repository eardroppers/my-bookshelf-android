package com.bookmanager.core.data

import com.bookmanager.core.domain.model.BookCandidate
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Mainland catalog search client that only connects to Douban book pages.
 */
class DoubanCatalogClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Searches Douban by ISBN or title.
     */
    fun search(query: String, isIsbn: Boolean): List<BookCandidate> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()
        return if (isIsbn) searchByIsbn(cleanQuery) else searchByTitle(cleanQuery)
    }

    private fun searchByIsbn(isbn: String): List<BookCandidate> {
        val url = "https://book.douban.com/isbn/$isbn/"
        val direct = runCatching {
            parseDetail(fetch(url), url).copy(isbn = isbn)
        }.getOrNull()
        if (direct != null && direct.title.isNotBlank() && !direct.title.contains("页面不存在")) {
            return listOf(direct)
        }
        return searchByTitle(isbn).map { candidate ->
            if (candidate.isbn.isBlank()) candidate.copy(isbn = isbn) else candidate
        }
    }

    private fun searchByTitle(title: String): List<BookCandidate> {
        val encoded = URLEncoder.encode(title, Charsets.UTF_8.name())
        val searchUrls = listOf(
            "https://search.douban.com/book/subject_search?search_text=$encoded",
            "https://www.douban.com/search?cat=1001&q=$encoded",
        )
        val results = searchUrls.firstNotNullOfOrNull { url ->
            runCatching { parseSearch(fetch(url)) }
                .getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
        }.orEmpty()
        return results.distinctBy { it.sourceUrl.ifBlank { it.title + it.author } }.take(20)
    }

    private fun parseSearch(html: String): List<BookCandidate> {
        val decodedHtml = html.decodeEscapes()
        val fromJsonLike = Regex(
            """"title"\s*:\s*"([^"]+)".{0,800}?"url"\s*:\s*"([^"]*book\.douban\.com/subject/\d+/?[^"]*)"""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).findAll(decodedHtml).map { match ->
            BookCandidate(
                title = match.groupValues[1].decodeEscapes().cleanHtml(),
                sourceName = "豆瓣读书",
                sourceUrl = match.groupValues[2].decodeEscapes().normalizeDoubanUrl(),
            )
        }.filter { it.title.isNotBlank() && it.sourceUrl.isNotBlank() }.toList()
        if (fromJsonLike.isNotEmpty()) return fromJsonLike

        return Regex(
            """<a[^>]+href="(https://book\.douban\.com/subject/\d+/)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).findAll(decodedHtml).mapNotNull { match ->
            val name = match.groupValues[2].cleanHtml()
            if (name.isBlank()) null else BookCandidate(
                title = name,
                sourceName = "豆瓣读书",
                sourceUrl = match.groupValues[1].normalizeDoubanUrl(),
            )
        }.toList()
    }

    /**
     * Fetches full metadata from a selected Douban subject page.
     */
    fun enrich(candidate: BookCandidate): BookCandidate {
        if (candidate.sourceUrl.isBlank()) return candidate
        return parseDetail(fetch(candidate.sourceUrl), candidate.sourceUrl).let { detail ->
            candidate.copy(
                title = detail.title.ifBlank { candidate.title },
                author = detail.author.ifBlank { candidate.author },
                publisher = detail.publisher.ifBlank { candidate.publisher },
                isbn = detail.isbn.ifBlank { candidate.isbn },
                publishDate = detail.publishDate.ifBlank { candidate.publishDate },
                price = detail.price ?: candidate.price,
            )
        }
    }

    private fun parseDetail(html: String, url: String): BookCandidate {
        val decodedHtml = html.decodeEscapes()
        val info = decodedHtml.substringAfter("""<div id="info"""", decodedHtml)
            .substringBefore("""</div>""", decodedHtml)
        return BookCandidate(
            title = Regex("""<span[^>]+property="v:itemreviewed"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                .find(decodedHtml)?.groupValues?.getOrNull(1)?.cleanHtml()
                ?: Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
                    .find(decodedHtml)?.groupValues?.getOrNull(1)?.cleanHtml()?.removeSuffix("(豆瓣)").orEmpty().trim(),
            author = extractAuthor(info),
            publisher = extractInfoValue(info, "出版社"),
            isbn = extractInfoValue(info, "ISBN"),
            publishDate = extractInfoValue(info, "出版年"),
            price = extractInfoValue(info, "定价").toPrice(),
            sourceName = "豆瓣读书",
            sourceUrl = url,
        )
    }

    private fun extractAuthor(info: String): String {
        val authorBlock = extractRawInfoValue(info, "作者")
        val linkedAuthors = Regex("""<a[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(authorBlock)
            .map { it.groupValues[1].cleanHtml() }
            .filter { it.isNotBlank() }
            .joinToString(" / ")
        return linkedAuthors.ifBlank { authorBlock.cleanHtml() }
    }

    private fun extractInfoValue(info: String, label: String): String {
        return extractRawInfoValue(info, label).cleanHtml()
    }

    private fun extractRawInfoValue(info: String, label: String): String {
        val escaped = Regex.escape(label)
        return Regex(
            """<span[^>]*class="pl"[^>]*>\s*$escaped\s*:?\s*</span>\s*(.*?)(?:<br\s*/?>|<span[^>]*class="pl"|</div>)""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(info)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun fetch(url: String): String {
        require(url.startsWith("https://book.douban.com") || url.startsWith("https://search.douban.com") || url.startsWith("https://www.douban.com")) {
            "仅允许访问中国境内豆瓣书籍源"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 Android BookManager/1.0")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("豆瓣访问失败：${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun String.cleanHtml(): String {
        return replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<.*?>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .replace("\\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.decodeEscapes(): String {
        val slashFixed = replace("\\/", "/").replace("\\\"", "\"")
        return Regex("""\\u([0-9a-fA-F]{4})""").replace(slashFixed) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
    }

    private fun String.normalizeDoubanUrl(): String {
        val clean = decodeEscapes().substringBefore("?").trim()
        val subject = Regex("""https://book\.douban\.com/subject/\d+/?""").find(clean)?.value.orEmpty()
        return subject.ifBlank { clean }
    }

    private fun String.toPrice(): Double? {
        return Regex("""\d+(\.\d+)?""").find(this)?.value?.toDoubleOrNull()
    }
}
