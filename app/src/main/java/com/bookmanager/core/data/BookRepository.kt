package com.bookmanager.core.data

import android.content.Context
import android.net.Uri
import com.bookmanager.core.domain.model.BackupData
import com.bookmanager.core.domain.model.Book
import com.bookmanager.core.domain.model.BookCandidate
import com.bookmanager.core.domain.model.BookCondition
import com.bookmanager.core.domain.model.Bookshelf
import com.bookmanager.core.domain.model.Category
import com.bookmanager.core.domain.model.ImportMode
import com.bookmanager.core.domain.model.LibraryStats
import com.bookmanager.core.domain.model.PurchaseStatus
import com.bookmanager.core.domain.model.PurchaseSource
import com.bookmanager.core.domain.model.ReadingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository that keeps the UI synchronized with the local SQLite database.
 */
class BookRepository(private val context: Context) {
    private val database = BookDatabase(context.applicationContext)
    private val catalogClient = CppInfoCatalogClient()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    private val _bookshelves = MutableStateFlow<List<Bookshelf>>(emptyList())
    private val _purchaseSources = MutableStateFlow<List<PurchaseSource>>(emptyList())

    /**
     * All local books.
     */
    val books: StateFlow<List<Book>> = _books

    /**
     * All categories.
     */
    val categories: StateFlow<List<Category>> = _categories

    /**
     * All bookshelves.
     */
    val bookshelves: StateFlow<List<Bookshelf>> = _bookshelves

    /**
     * All purchase sources.
     */
    val purchaseSources: StateFlow<List<PurchaseSource>> = _purchaseSources

    init {
        refresh()
    }

    /**
     * Reloads all cached state from SQLite.
     */
    fun refresh() {
        _books.value = database.getBooks()
        _categories.value = database.getCategories()
        _bookshelves.value = database.getBookshelves()
        _purchaseSources.value = database.getPurchaseSources()
    }

    /**
     * Returns one book from the current cache.
     */
    fun findBook(id: Long): Book? = _books.value.firstOrNull { it.id == id } ?: database.getBook(id)

    /**
     * Saves a new or existing book.
     */
    suspend fun saveBook(book: Book): Long = withContext(Dispatchers.IO) {
        val id = if (book.id == 0L) database.insertBook(book) else {
            database.updateBook(book)
            book.id
        }
        refresh()
        id
    }

    /**
     * Deletes a book by id.
     */
    suspend fun deleteBook(bookId: Long) = withContext(Dispatchers.IO) {
        database.deleteBook(bookId)
        refresh()
    }

    /**
     * Adds a category by name.
     */
    suspend fun addCategory(name: String) = withContext(Dispatchers.IO) {
        database.addCategory(name)
        refresh()
    }

    /**
     * Deletes a category by name.
     */
    suspend fun deleteCategory(name: String) = withContext(Dispatchers.IO) {
        database.deleteCategory(name)
        refresh()
    }

    /**
     * Adds a bookshelf by name.
     */
    suspend fun addBookshelf(name: String) = withContext(Dispatchers.IO) {
        database.addBookshelf(name)
        refresh()
    }

    /**
     * Deletes a bookshelf by name.
     */
    suspend fun deleteBookshelf(name: String) = withContext(Dispatchers.IO) {
        database.deleteBookshelf(name)
        refresh()
    }

    /**
     * Adds a purchase source by name.
     */
    suspend fun addPurchaseSource(name: String) = withContext(Dispatchers.IO) {
        database.addPurchaseSource(name)
        refresh()
    }

    /**
     * Deletes a purchase source by name.
     */
    suspend fun deletePurchaseSource(name: String) = withContext(Dispatchers.IO) {
        database.deletePurchaseSource(name)
        refresh()
    }

    /**
     * Clears all user data after confirmation.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.clearAll()
        refresh()
    }

    /**
     * Builds library statistics from the current cache.
     */
    fun getStats(): LibraryStats {
        val books = _books.value
        return LibraryStats(
            totalCount = books.size,
            totalSpent = books.sumOf { it.purchasePrice ?: 0.0 },
            categoryDistribution = books.groupingBy { it.category.ifBlank { "未分类" } }.eachCount(),
            readingStatusDistribution = books.groupingBy { it.readingStatus }.eachCount(),
        )
    }

    /**
     * Searches mainland catalog metadata by ISBN or title.
     */
    suspend fun searchCatalog(query: String, isIsbn: Boolean): Result<List<BookCandidate>> = withContext(Dispatchers.IO) {
        runCatching { catalogClient.search(query, isIsbn) }
    }

    /**
     * Fetches detail fields for a selected candidate.
     */
    suspend fun enrichCandidate(candidate: BookCandidate): Result<BookCandidate> = withContext(Dispatchers.IO) {
        runCatching { catalogClient.enrich(candidate) }
    }

    /**
     * Exports a complete JSON backup to a SAF uri.
     */
    suspend fun exportJson(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = BackupData(
                books = database.getBooks(),
                bookshelves = database.getBookshelves(),
                categories = database.getCategories(),
                purchaseSources = database.getPurchaseSources(),
            )
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
            } ?: error("无法打开导出文件")
            "JSON 备份已导出"
        }
    }

    /**
     * Imports a complete JSON backup from a SAF uri.
     */
    suspend fun importJson(uri: Uri, mode: ImportMode): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.reader(Charsets.UTF_8).readText()
            } ?: error("无法读取备份文件")
            val backup = json.decodeFromString<BackupData>(text)
            if (backup.version != 1) error("备份版本不兼容")
            if (mode == ImportMode.OVERWRITE) database.clearAll()
            backup.categories.forEach { database.addCategory(it.name) }
            backup.bookshelves.forEach { database.addBookshelf(it.name) }
            backup.purchaseSources.forEach { database.addPurchaseSource(it.name) }
            val knownIsbns = database.getBooks().map { it.isbn.normalizedIsbn() }.filter { it.isNotBlank() }.toMutableSet()
            var imported = 0
            backup.books.forEach { book ->
                val isbn = book.isbn.normalizedIsbn()
                if (isbn.isBlank() || knownIsbns.add(isbn)) {
                    database.insertBook(book.copy(id = 0))
                    imported++
                }
            }
            refresh()
            imported
        }
    }

    /**
     * Exports a CSV list that spreadsheet apps can open.
     */
    suspend fun exportCsv(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val header = listOf("书名", "作者", "ISBN", "出版社", "购买价", "购入来源", "阅读状态", "购买状态", "成色", "分类", "书架", "备注")
            val rows = database.getBooks().map { book ->
                listOf(
                    book.title,
                    book.author,
                    book.isbn,
                    book.publisher,
                    book.purchasePrice?.toString().orEmpty(),
                    book.purchaseSource,
                    book.readingStatus.label,
                    book.purchaseStatus.label,
                    book.condition.label,
                    book.category,
                    book.bookshelves.joinToString("/"),
                    book.notes,
                )
            }
            val csv = (listOf(header) + rows).joinToString("\n") { row ->
                row.joinToString(",") { it.toCsvCell() }
            }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(UTF8_BOM)
                output.write(csv.toByteArray(Charsets.UTF_8))
            } ?: error("无法打开导出文件")
            "CSV 清单已导出"
        }
    }

    /**
     * Exports the canonical CSV header so imports stay consistent.
     */
    suspend fun exportCsvTemplate(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val header = listOf("书名", "作者", "ISBN", "出版社", "购买价", "购入来源", "阅读状态", "购买状态", "成色", "分类", "书架", "备注")
            val csv = header.joinToString(",") { it.toCsvCell() }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(UTF8_BOM)
                output.write(csv.toByteArray(Charsets.UTF_8))
            } ?: error("无法打开模板文件")
            "CSV 导入模板已导出"
        }
    }

    /**
     * Imports books from a CSV file with the default column layout.
     */
    suspend fun importCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.reader(Charsets.UTF_8).readText()
            } ?: error("无法读取 CSV 文件")
            val rows = text.lineSequence().filter { it.isNotBlank() }.map { parseCsvLine(it) }.toList()
            if (rows.size <= 1) return@runCatching 0
            val header = rows.first().mapIndexed { index, value ->
                if (index == 0) value.removePrefix("\uFEFF").trim() else value.trim()
            }
            fun List<String>.cell(fallbackIndex: Int, vararg names: String): String {
                val index = names.asSequence().map { header.indexOf(it) }.firstOrNull { it >= 0 } ?: fallbackIndex
                return getOrNull(index).orEmpty().trim()
            }
            val knownIsbns = database.getBooks().map { it.isbn.normalizedIsbn() }.filter { it.isNotBlank() }.toMutableSet()
            var imported = 0
            rows.drop(1).forEach { columns ->
                val category = columns.cell(9, "分类", "类型", "书籍类型", "类别")
                val book = Book(
                    title = columns.cell(0, "书名", "名称", "题名"),
                    author = columns.cell(1, "作者", "著者"),
                    isbn = columns.cell(2, "ISBN", "isbn"),
                    publisher = columns.cell(3, "出版社"),
                    purchasePrice = columns.cell(4, "购买价", "购入价", "价格").toDoubleOrNull(),
                    purchaseSource = columns.cell(5, "购入来源", "购买来源", "来源"),
                    readingStatus = ReadingStatus.fromLabel(columns.cell(6, "阅读状态", "状态")),
                    purchaseStatus = PurchaseStatus.fromLabel(columns.cell(7, "购买状态", "是否购买", "购买类别")),
                    condition = BookCondition.entries.firstOrNull { it.label == columns.cell(8, "成色", "品相") } ?: BookCondition.NEW_95,
                    category = category,
                    bookshelves = columns.cell(10, "书架", "位置").split("/").map { it.trim() }.filter { it.isNotEmpty() },
                    notes = columns.cell(11, "备注", "说明"),
                )
                val isbn = book.isbn.normalizedIsbn()
                if (book.title.isNotBlank() && (isbn.isBlank() || knownIsbns.add(isbn))) {
                    database.insertBook(book)
                    imported++
                }
            }
            refresh()
            imported
        }
    }

    private fun String.toCsvCell(): String {
        val escaped = replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '\n' || it == '"' }) "\"$escaped\"" else escaped
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values.add(current.toString())
        return values
    }

    private fun String.normalizedIsbn(): String {
        return filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
    }

    companion object {
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
