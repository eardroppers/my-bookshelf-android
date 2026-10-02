package com.bookmanager.core.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.bookmanager.core.domain.model.Book
import com.bookmanager.core.domain.model.BookCondition
import com.bookmanager.core.domain.model.Bookshelf
import com.bookmanager.core.domain.model.Category
import com.bookmanager.core.domain.model.PurchaseStatus
import com.bookmanager.core.domain.model.PurchaseSource
import com.bookmanager.core.domain.model.ReadingStatus

/**
 * Lightweight SQLite database for all device-local application data.
 */
class BookDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                isbn TEXT,
                title TEXT NOT NULL,
                author TEXT,
                publisher TEXT,
                publishDate TEXT,
                price REAL,
                purchasePrice REAL,
                purchaseDate TEXT,
                purchaseSource TEXT,
                category TEXT,
                readingStatus INTEGER NOT NULL,
                purchaseStatus INTEGER NOT NULL DEFAULT 1,
                conditionLevel INTEGER NOT NULL DEFAULT 95,
                notes TEXT,
                coverUrl TEXT,
                coverImagePath TEXT,
                backImagePath TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_books_title ON books(title)")
        db.execSQL("CREATE INDEX idx_books_author ON books(author)")
        db.execSQL("CREATE INDEX idx_books_isbn ON books(isbn)")
        db.execSQL("CREATE INDEX idx_books_status ON books(readingStatus)")
        db.execSQL("CREATE INDEX idx_books_updated ON books(updatedAt)")
        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE bookshelves (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE purchase_sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE book_shelves (
                bookId INTEGER NOT NULL,
                shelfName TEXT NOT NULL,
                PRIMARY KEY(bookId, shelfName),
                FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        seedDefaults(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching {
                db.execSQL("ALTER TABLE books ADD COLUMN conditionLevel INTEGER NOT NULL DEFAULT 95")
            }
        }
        if (oldVersion < 3) {
            runCatching {
                db.execSQL("ALTER TABLE books ADD COLUMN purchaseStatus INTEGER NOT NULL DEFAULT 1")
            }
            runCatching {
                db.execSQL("UPDATE books SET purchaseStatus = 0, readingStatus = 0 WHERE readingStatus = 4")
            }
            seedDefaults(db)
        }
        if (oldVersion < 4) {
            runCatching {
                db.execSQL("ALTER TABLE books ADD COLUMN purchaseSource TEXT")
            }
            runCatching {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS purchase_sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
            seedDefaults(db)
        }
        if (oldVersion < 5) {
            runCatching {
                db.execSQL("ALTER TABLE books ADD COLUMN coverUrl TEXT")
            }
        }
    }

    /**
     * Returns all books sorted with the most recently edited first.
     */
    fun getBooks(): List<Book> {
        return readableDatabase.rawQuery("SELECT * FROM books ORDER BY updatedAt DESC", emptyArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBook(getShelvesForBook(cursor.getLong("id"))))
                }
            }
        }
    }

    /**
     * Returns a book by local id.
     */
    fun getBook(id: Long): Book? {
        return readableDatabase.rawQuery("SELECT * FROM books WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBook(getShelvesForBook(id)) else null
        }
    }

    /**
     * Inserts a book and returns the generated id.
     */
    fun insertBook(book: Book): Long {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        return db.transaction {
            ensureCategory(db, book.category)
            ensurePurchaseSource(db, book.purchaseSource)
            book.bookshelves.forEach { ensureShelf(db, it) }
            val id = insert("books", null, book.toValues(createdAt = now, updatedAt = now))
            replaceShelves(db, id, book.bookshelves)
            id
        }
    }

    /**
     * Updates an existing book.
     */
    fun updateBook(book: Book) {
        val db = writableDatabase
        db.transaction {
            ensureCategory(db, book.category)
            ensurePurchaseSource(db, book.purchaseSource)
            book.bookshelves.forEach { ensureShelf(db, it) }
            update("books", book.toValues(updatedAt = System.currentTimeMillis()), "id = ?", arrayOf(book.id.toString()))
            replaceShelves(db, book.id, book.bookshelves)
        }
    }

    /**
     * Deletes one book and its bookshelf links.
     */
    fun deleteBook(bookId: Long) {
        writableDatabase.delete("books", "id = ?", arrayOf(bookId.toString()))
    }

    /**
     * Removes every local record.
     */
    fun clearAll() {
        writableDatabase.transaction {
            delete("book_shelves", null, null)
            delete("books", null, null)
            delete("bookshelves", null, null)
            delete("categories", null, null)
            delete("purchase_sources", null, null)
            seedDefaults(this)
        }
    }

    /**
     * Returns all categories.
     */
    fun getCategories(): List<Category> {
        return readableDatabase.rawQuery("SELECT * FROM categories ORDER BY name", emptyArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Category(id = cursor.getLong("id"), name = cursor.getString("name")))
                }
            }
        }
    }

    /**
     * Creates a category when it does not already exist.
     */
    fun addCategory(name: String) {
        ensureCategory(writableDatabase, name.trim())
    }

    /**
     * Deletes a category without deleting books.
     */
    fun deleteCategory(name: String) {
        writableDatabase.transaction {
            execSQL("UPDATE books SET category = '' WHERE category = ?", arrayOf(name))
            delete("categories", "name = ?", arrayOf(name))
        }
    }

    /**
     * Returns all bookshelves.
     */
    fun getBookshelves(): List<Bookshelf> {
        return readableDatabase.rawQuery("SELECT * FROM bookshelves ORDER BY name", emptyArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Bookshelf(
                            id = cursor.getLong("id"),
                            name = cursor.getString("name"),
                            createdAt = cursor.getLong("createdAt"),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Creates a bookshelf when it does not already exist.
     */
    fun addBookshelf(name: String) {
        ensureShelf(writableDatabase, name.trim())
    }

    /**
     * Deletes a bookshelf and unlinks it from books.
     */
    fun deleteBookshelf(name: String) {
        writableDatabase.transaction {
            delete("book_shelves", "shelfName = ?", arrayOf(name))
            delete("bookshelves", "name = ?", arrayOf(name))
        }
    }

    /**
     * Returns all purchase sources.
     */
    fun getPurchaseSources(): List<PurchaseSource> {
        return readableDatabase.rawQuery("SELECT * FROM purchase_sources ORDER BY name", emptyArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PurchaseSource(
                            id = cursor.getLong("id"),
                            name = cursor.getString("name"),
                            createdAt = cursor.getLong("createdAt"),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Creates a purchase source when it does not already exist.
     */
    fun addPurchaseSource(name: String) {
        ensurePurchaseSource(writableDatabase, name.trim())
    }

    /**
     * Deletes a purchase source without deleting books.
     */
    fun deletePurchaseSource(name: String) {
        writableDatabase.transaction {
            execSQL("UPDATE books SET purchaseSource = '' WHERE purchaseSource = ?", arrayOf(name))
            delete("purchase_sources", "name = ?", arrayOf(name))
        }
    }

    private fun getShelvesForBook(bookId: Long): List<String> {
        return readableDatabase.rawQuery(
            "SELECT shelfName FROM book_shelves WHERE bookId = ? ORDER BY shelfName",
            arrayOf(bookId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString("shelfName"))
            }
        }
    }

    private fun replaceShelves(db: SQLiteDatabase, bookId: Long, shelves: List<String>) {
        db.delete("book_shelves", "bookId = ?", arrayOf(bookId.toString()))
        shelves.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { shelf ->
            db.insertWithOnConflict(
                "book_shelves",
                null,
                ContentValues().apply {
                    put("bookId", bookId)
                    put("shelfName", shelf)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    private fun ensureCategory(db: SQLiteDatabase, name: String) {
        val value = name.trim()
        if (value.isEmpty()) return
        db.insertWithOnConflict(
            "categories",
            null,
            ContentValues().apply { put("name", value) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun ensureShelf(db: SQLiteDatabase, name: String) {
        val value = name.trim()
        if (value.isEmpty()) return
        db.insertWithOnConflict(
            "bookshelves",
            null,
            ContentValues().apply {
                put("name", value)
                put("createdAt", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun ensurePurchaseSource(db: SQLiteDatabase, name: String) {
        val value = name.trim()
        if (value.isEmpty()) return
        db.insertWithOnConflict(
            "purchase_sources",
            null,
            ContentValues().apply {
                put("name", value)
                put("createdAt", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun seedDefaults(db: SQLiteDatabase) {
        listOf("文学", "小说", "名著", "科幻", "历史", "经济", "计算机").forEach { ensureCategory(db, it) }
        listOf("卧室书架", "办公室", "客厅书架").forEach { ensureShelf(db, it) }
        listOf("京东", "淘宝", "拼多多", "孔夫子", "八爪鱼").forEach { ensurePurchaseSource(db, it) }
    }

    private fun Book.toValues(
        createdAt: Long = this.createdAt,
        updatedAt: Long = this.updatedAt,
    ): ContentValues {
        return ContentValues().apply {
            put("isbn", isbn)
            put("title", title.trim())
            put("author", author.trim())
            put("publisher", publisher.trim())
            put("publishDate", publishDate.trim())
            putNullableDouble("price", price)
            putNullableDouble("purchasePrice", purchasePrice)
            put("purchaseDate", purchaseDate.trim())
            put("purchaseSource", purchaseSource.trim())
            put("category", category.trim())
            put("readingStatus", readingStatus.value)
            put("purchaseStatus", purchaseStatus.value)
            put("conditionLevel", condition.value)
            put("notes", notes)
            put("coverUrl", coverUrl)
            put("coverImagePath", coverImagePath)
            put("backImagePath", backImagePath)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
        }
    }

    private fun Cursor.toBook(shelves: List<String>): Book {
        return Book(
            id = getLong("id"),
            isbn = getString("isbn"),
            title = getString("title"),
            author = getString("author"),
            publisher = getString("publisher"),
            publishDate = getString("publishDate"),
            price = getNullableDouble("price"),
            purchasePrice = getNullableDouble("purchasePrice"),
            purchaseDate = getString("purchaseDate"),
            purchaseSource = getString("purchaseSource"),
            category = getString("category"),
            readingStatus = ReadingStatus.fromValue(getInt("readingStatus")),
            purchaseStatus = PurchaseStatus.fromValue(getInt("purchaseStatus")),
            condition = BookCondition.fromValue(getInt("conditionLevel")),
            notes = getString("notes"),
            coverUrl = getString("coverUrl"),
            coverImagePath = getString("coverImagePath"),
            backImagePath = getString("backImagePath"),
            bookshelves = shelves,
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
        )
    }

    private fun Cursor.getString(columnName: String): String {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) "" else getString(index)
    }

    private fun Cursor.getLong(columnName: String): Long = getLong(getColumnIndexOrThrow(columnName))

    private fun Cursor.getInt(columnName: String): Int = getInt(getColumnIndexOrThrow(columnName))

    private fun Cursor.getNullableDouble(columnName: String): Double? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun ContentValues.putNullableDouble(key: String, value: Double?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            val result = block()
            setTransactionSuccessful()
            result
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val DATABASE_NAME = "book_manager.db"
        private const val DATABASE_VERSION = 5
    }
}
