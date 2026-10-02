package com.bookmanager.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Reading progress values stored in the local database.
 */
@Serializable
enum class ReadingStatus(val value: Int, val label: String) {
    WANT_TO_READ(0, "想读"),
    READING(1, "在读"),
    READ(2, "已读"),
    IDLE(3, "闲置"),
    PLAN_TO_BUY(4, "计划购买");

    companion object {
        val visibleEntries: List<ReadingStatus>
            get() = listOf(WANT_TO_READ, READING, READ, IDLE)

        /**
         * Converts a stored integer into a reading status.
         */
        fun fromValue(value: Int): ReadingStatus {
            return if (value == PLAN_TO_BUY.value) WANT_TO_READ else entries.firstOrNull { it.value == value } ?: WANT_TO_READ
        }

        /**
         * Converts a Chinese display label into a reading status.
         */
        fun fromLabel(label: String?): ReadingStatus {
            return if (label == PLAN_TO_BUY.label) WANT_TO_READ else entries.firstOrNull { it.label == label } ?: WANT_TO_READ
        }
    }
}

/**
 * Whether the user already owns the book.
 */
@Serializable
enum class PurchaseStatus(val value: Int, val label: String) {
    PLAN_TO_BUY(0, "计划购买"),
    PURCHASED(1, "已购买");

    companion object {
        fun fromValue(value: Int): PurchaseStatus {
            return entries.firstOrNull { it.value == value } ?: PURCHASED
        }

        fun fromLabel(label: String?): PurchaseStatus {
            return entries.firstOrNull { it.label == label } ?: PURCHASED
        }
    }
}

/**
 * Physical condition level for a local book record.
 */
@Serializable
enum class BookCondition(val value: Int, val label: String) {
    BRAND_NEW(100, "全新"),
    NEW_99(99, "99新"),
    NEW_95(95, "95新"),
    NEW_85(85, "85新"),
    NEW_75(75, "75新"),
    BELOW_75(70, "75新以下");

    companion object {
        /**
         * Converts a stored integer into a book condition.
         */
        fun fromValue(value: Int): BookCondition {
            return entries.firstOrNull { it.value == value } ?: NEW_95
        }
    }
}

/**
 * A local book record owned by the current device user.
 */
@Serializable
data class Book(
    val id: Long = 0,
    val isbn: String = "",
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val publishDate: String = "",
    val price: Double? = null,
    val purchasePrice: Double? = null,
    val purchaseDate: String = "",
    val purchaseSource: String = "",
    val category: String = "",
    val readingStatus: ReadingStatus = ReadingStatus.WANT_TO_READ,
    val purchaseStatus: PurchaseStatus = PurchaseStatus.PURCHASED,
    val condition: BookCondition = BookCondition.NEW_95,
    val notes: String = "",
    val coverUrl: String = "",
    val coverImagePath: String = "",
    val backImagePath: String = "",
    val bookshelves: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * User-created bookshelf.
 */
@Serializable
data class Bookshelf(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * User-created category.
 */
@Serializable
data class Category(
    val id: Long = 0,
    val name: String,
)

/**
 * User-created or default purchase source.
 */
@Serializable
data class PurchaseSource(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Candidate returned by a mainland catalog source.
 */
@Serializable
data class BookCandidate(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val publishDate: String = "",
    val category: String = "",
    val coverUrl: String = "",
    val price: Double? = null,
    val sourceName: String = "",
    val sourceUrl: String = "",
) {
    /**
     * Converts catalog metadata into an editable local book draft.
     */
    fun toBook(): Book {
        return Book(
            isbn = isbn,
            title = title,
            author = author,
            publisher = publisher,
            publishDate = publishDate,
            category = category,
            coverUrl = coverUrl,
            price = price,
        )
    }
}

/**
 * JSON backup payload for device-local migration.
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val books: List<Book> = emptyList(),
    val bookshelves: List<Bookshelf> = emptyList(),
    val categories: List<Category> = emptyList(),
    val purchaseSources: List<PurchaseSource> = emptyList(),
)

/**
 * Mode used when restoring a JSON backup.
 */
enum class ImportMode {
    OVERWRITE,
    MERGE,
}

/**
 * Aggregate counts for the statistics screen.
 */
data class LibraryStats(
    val totalCount: Int = 0,
    val totalSpent: Double = 0.0,
    val categoryDistribution: Map<String, Int> = emptyMap(),
    val readingStatusDistribution: Map<ReadingStatus, Int> = emptyMap(),
)
