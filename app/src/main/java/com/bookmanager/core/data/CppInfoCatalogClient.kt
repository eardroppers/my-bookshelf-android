package com.bookmanager.core.data

import com.bookmanager.core.domain.model.BookCandidate
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Catalog client for 国家出版发行信息公共服务平台 book.cppinfo.cn.
 */
class CppInfoCatalogClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Searches books by ISBN or title through the public search endpoint.
     */
    fun search(query: String, isIsbn: Boolean): List<BookCandidate> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()
        val html = postSearch(cleanQuery, isIsbn)
        return parseSearch(html).take(20)
    }

    /**
     * Fetches the detail page for a selected search result.
     */
    fun enrich(candidate: BookCandidate): BookCandidate {
        if (candidate.sourceUrl.isBlank()) return candidate
        val html = fetch("https://book.cppinfo.cn${candidate.sourceUrl}")
        val detail = parseDetail(html, candidate.sourceUrl)
        return candidate.copy(
            title = detail.title.ifBlank { candidate.title },
            author = detail.author.ifBlank { candidate.author },
            publisher = detail.publisher.ifBlank { candidate.publisher },
            isbn = detail.isbn.ifBlank { candidate.isbn },
            publishDate = detail.publishDate.ifBlank { candidate.publishDate },
            category = detail.category.ifBlank { candidate.category },
            coverUrl = detail.coverUrl.ifBlank { candidate.coverUrl },
            price = detail.price ?: candidate.price,
        )
    }

    private fun postSearch(query: String, isIsbn: Boolean): String {
        val body = FormBody.Builder()
            .add("key", query)
            .add("author", "")
            .add("keyword", "")
            .add("isbn", if (isIsbn) query else "")
            .add("sm", "")
            .add("publishedClassification", "")
            .add("offset", "1")
            .add("sort", "publicationdate")
            .add("order", "desc")
            .add("ids", "")
            .add("minprice", "")
            .add("maxprice", "")
            .add("languages", "")
            .add("cip", "")
            .add("hasEbook", "false")
            .add("pubyear", "")
            .add("authorsure", "")
            .add("publishersure", "")
            .add("cipsearch", "")
            .build()
        val request = Request.Builder()
            .url("https://book.cppinfo.cn/So/Search/Index")
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://book.cppinfo.cn/so/home/qhsearch?q=${URLEncoder.encode(query, Charsets.UTF_8.name())}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("出版发行平台搜索失败：${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun fetch(url: String): String {
        require(url.startsWith("https://book.cppinfo.cn/")) { "仅允许访问国家出版发行信息公共服务平台" }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("出版发行平台详情访问失败：${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parseSearch(html: String): List<BookCandidate> {
        return Regex("""<div class="pro-list">(.*?)</div>\s*</span>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .mapNotNull { match ->
                val block = match.groupValues[1]
                val url = Regex("""<a href="(/Encyclopedias/home/index\?id=\d+)"""").find(block)?.groupValues?.getOrNull(1).orEmpty()
                val title = Regex("""<div class="p-text"><a[^>]*title="([^"]+)"""").find(block)?.groupValues?.getOrNull(1)?.cleanHtml().orEmpty()
                if (url.isBlank() || title.isBlank()) return@mapNotNull null
                BookCandidate(
                    title = title,
                    author = Regex("""<div class="p-author"[^>]*title="([^"]*)"""").find(block)?.groupValues?.getOrNull(1)?.cleanHtml().orEmpty(),
                    publisher = Regex("""<div class="p-public">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.getOrNull(1)?.cleanHtml().orEmpty(),
                    isbn = Regex("""ISBN\s*:\s*([0-9Xx-]+)""").find(block)?.groupValues?.getOrNull(1).orEmpty(),
                    publishDate = Regex("""<div class="p-isbn">\s*([0-9]{4}[^<]*?)\s*</div>""").find(block)?.groupValues?.getOrNull(1)?.cleanHtml().orEmpty(),
                    category = extractAny(block, listOf("分类", "中图法分类", "出版物类型")),
                    coverUrl = extractImageUrl(block),
                    price = Regex("""<div class="p-price">.*?([0-9]+(?:\.[0-9]+)?)""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                    sourceName = "国家出版发行信息公共服务平台",
                    sourceUrl = url,
                )
            }
            .distinctBy { it.sourceUrl }
            .toList()
    }

    private fun parseDetail(html: String, sourceUrl: String): BookCandidate {
        return BookCandidate(
            title = extractDetailValue(html, "书名"),
            author = extractDetailValue(html, "著者"),
            publisher = extractDetailValue(html, "出版社"),
            isbn = extractDetailValue(html, "ISBN"),
            publishDate = extractDetailValue(html, "出版时间"),
            category = extractAny(html, listOf("分类", "中图法分类", "中图分类号", "图书分类", "出版物类型")),
            coverUrl = extractImageUrl(html),
            price = extractDetailValue(html, "定价").toPrice(),
            sourceName = "国家出版发行信息公共服务平台",
            sourceUrl = sourceUrl,
        )
    }

    private fun extractDetailValue(html: String, label: String): String {
        val pattern = Regex(
            """<span class="fl">\s*${Regex.escape(label)}：\s*</span>\s*<span class="fl val_txt[^"]*">\s*(.*?)\s*</span>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        return pattern.find(html)?.groupValues?.getOrNull(1)?.cleanHtml().orEmpty()
    }

    private fun extractAny(html: String, labels: List<String>): String {
        return labels.asSequence()
            .map { extractDetailValue(html, it) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun extractImageUrl(html: String): String {
        val candidates = Regex("""<img[^>]+(?:data-original|data-src|src)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .filter { it.isNotBlank() && !it.contains("logo", ignoreCase = true) && !it.contains("icon", ignoreCase = true) }
        return candidates.firstOrNull()?.normalizeImageUrl().orEmpty()
    }

    private fun String.normalizeImageUrl(): String {
        val value = replace("&amp;", "&").trim()
        return when {
            value.startsWith("https://book.cppinfo.cn/") -> value
            value.startsWith("http://book.cppinfo.cn/") -> value.replaceFirst("http://", "https://")
            value.startsWith("//book.cppinfo.cn/") -> "https:$value"
            value.startsWith("/") -> "https://book.cppinfo.cn$value"
            value.startsWith("http") -> ""
            else -> "https://book.cppinfo.cn/$value"
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
            .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toInt().toChar().toString() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.toPrice(): Double? = Regex("""\d+(\.\d+)?""").find(this)?.value?.toDoubleOrNull()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 Android BookManager/1.1"
    }
}
