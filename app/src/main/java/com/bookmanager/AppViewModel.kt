package com.bookmanager

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bookmanager.core.data.BookRepository
import com.bookmanager.core.domain.model.Book
import com.bookmanager.core.domain.model.BookCandidate
import com.bookmanager.core.domain.model.Bookshelf
import com.bookmanager.core.domain.model.Category
import com.bookmanager.core.domain.model.ImportMode
import com.bookmanager.core.domain.model.LibraryStats
import com.bookmanager.core.domain.model.PurchaseStatus
import com.bookmanager.core.domain.model.PurchaseSource
import com.bookmanager.core.domain.model.ReadingStatus
import com.bookmanager.core.security.PasswordStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Application-level state holder for the lightweight local-only app.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BookRepository(application)
    private val passwordStore = PasswordStore(application)
    private val filters = MutableStateFlow(FilterState())
    private val extras = MutableStateFlow(
        ScreenExtras(
            hasPassword = passwordStore.hasPassword(),
            isUnlocked = !passwordStore.hasPassword() && passwordStore.isSetupSkipped(),
            needsPasswordSetup = !passwordStore.hasPassword() && !passwordStore.isSetupSkipped(),
            needsIntro = !passwordStore.isIntroAccepted(),
        ),
    )

    /**
     * Screen state for the library list.
     */
    private val libraryData = combine(
        repository.books,
        repository.categories,
        repository.bookshelves,
        repository.purchaseSources,
    ) { books, categories, shelves, purchaseSources ->
        LibraryData(
            books = books,
            categories = categories,
            shelves = shelves,
            purchaseSources = purchaseSources,
        )
    }

    val uiState: StateFlow<AppUiState> = combine(
        libraryData,
        filters,
        extras,
    ) { library, filterState, extrasState ->
        val filtered = library.books.filter { book ->
            val queryMatched = filterState.query.isBlank() ||
                book.title.contains(filterState.query, ignoreCase = true) ||
                book.author.contains(filterState.query, ignoreCase = true) ||
                book.isbn.contains(filterState.query, ignoreCase = true)
            val categoryMatched = filterState.selectedCategory.isBlank() || book.category == filterState.selectedCategory
            val shelfMatched = filterState.selectedShelf.isBlank() || filterState.selectedShelf in book.bookshelves
            val statusMatched = filterState.selectedStatus == null || book.readingStatus == filterState.selectedStatus
            val purchaseMatched = filterState.selectedPurchaseStatus == null || book.purchaseStatus == filterState.selectedPurchaseStatus
            queryMatched && categoryMatched && shelfMatched && statusMatched && purchaseMatched
        }
        AppUiState(
            allBooks = library.books,
            visibleBooks = filtered,
            categories = library.categories,
            bookshelves = library.shelves,
            purchaseSources = library.purchaseSources,
            query = filterState.query,
            selectedCategory = filterState.selectedCategory,
            selectedShelf = filterState.selectedShelf,
            selectedStatus = filterState.selectedStatus,
            selectedPurchaseStatus = filterState.selectedPurchaseStatus,
            stats = repository.getStats(),
            message = extrasState.message,
            isBusy = extrasState.isBusy,
            catalogCandidates = extrasState.catalogCandidates,
            catalogQuery = extrasState.catalogQuery,
            isCatalogLoading = extrasState.isCatalogLoading,
            draftBook = extrasState.draftBook,
            hasPassword = extrasState.hasPassword,
            isUnlocked = extrasState.isUnlocked,
            needsPasswordSetup = extrasState.needsPasswordSetup,
            needsIntro = extrasState.needsIntro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    /**
     * Updates the search query.
     */
    fun setQuery(value: String) {
        filters.update { it.copy(query = value) }
    }

    /**
     * Updates category filter.
     */
    fun setCategoryFilter(value: String) {
        filters.update { it.copy(selectedCategory = if (it.selectedCategory == value) "" else value) }
    }

    /**
     * Updates bookshelf filter.
     */
    fun setShelfFilter(value: String) {
        filters.update { it.copy(selectedShelf = if (it.selectedShelf == value) "" else value) }
    }

    /**
     * Updates reading status filter.
     */
    fun setStatusFilter(value: ReadingStatus?) {
        filters.update { it.copy(selectedStatus = if (it.selectedStatus == value) null else value) }
    }

    /**
     * Updates purchase status filter.
     */
    fun setPurchaseStatusFilter(value: PurchaseStatus?) {
        filters.update { it.copy(selectedPurchaseStatus = if (it.selectedPurchaseStatus == value) null else value) }
    }

    /**
     * Clears all active filters.
     */
    fun clearFilters() {
        filters.value = FilterState()
    }

    /**
     * Finds a book for detail and edit screens.
     */
    fun findBook(id: Long): Book? = repository.findBook(id)

    /**
     * Saves a book.
     */
    fun saveBook(book: Book, onSaved: (Long) -> Unit = {}) {
        if (book.title.isBlank()) {
            extras.update { it.copy(message = "书名不能为空") }
            return
        }
        val normalizedIsbn = book.isbn.normalizedIsbn()
        if (book.id == 0L && normalizedIsbn.isNotBlank()) {
            val existing = repository.books.value.firstOrNull { it.isbn.normalizedIsbn() == normalizedIsbn }
            if (existing != null) {
                extras.update { it.copy(message = "已存在相同 ISBN 的书籍，未重复添加", draftBook = null) }
                onSaved(existing.id)
                return
            }
        }
        viewModelScope.launch {
            extras.update { it.copy(isBusy = true) }
            runCatching { repository.saveBook(book) }
                .onSuccess {
                    extras.update { it.copy(message = "已保存", draftBook = null) }
                    onSaved(it)
                }
                .onFailure { error -> extras.update { it.copy(message = "保存失败：${error.message.orEmpty()}") } }
            extras.update { it.copy(isBusy = false) }
        }
    }

    /**
     * Deletes one book.
     */
    fun deleteBook(bookId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            extras.update { it.copy(message = "已删除") }
            onDeleted()
        }
    }

    /**
     * Adds a category.
     */
    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCategory(name)
            extras.update { it.copy(message = "分类已添加") }
        }
    }

    /**
     * Deletes a category.
     */
    fun deleteCategory(name: String) {
        viewModelScope.launch {
            repository.deleteCategory(name)
            extras.update { it.copy(message = "分类已删除") }
        }
    }

    /**
     * Adds a bookshelf.
     */
    fun addBookshelf(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addBookshelf(name)
            extras.update { it.copy(message = "书架已添加") }
        }
    }

    /**
     * Deletes a bookshelf.
     */
    fun deleteBookshelf(name: String) {
        viewModelScope.launch {
            repository.deleteBookshelf(name)
            extras.update { it.copy(message = "书架已删除") }
        }
    }

    /**
     * Adds a purchase source.
     */
    fun addPurchaseSource(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addPurchaseSource(name)
            extras.update { it.copy(message = "来源已添加") }
        }
    }

    /**
     * Deletes a purchase source.
     */
    fun deletePurchaseSource(name: String) {
        viewModelScope.launch {
            repository.deletePurchaseSource(name)
            extras.update { it.copy(message = "来源已删除") }
        }
    }

    /**
     * Clears all app data.
     */
    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            extras.update { it.copy(message = "数据已清空") }
        }
    }

    /**
     * Exports JSON to a selected document.
     */
    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            extras.update { it.copy(isBusy = true) }
            val exportMessage = repository.exportJson(uri).fold({ it }, { "导出失败：${it.message.orEmpty()}" })
            extras.update { it.copy(message = exportMessage, isBusy = false) }
        }
    }

    /**
     * Imports JSON from a selected document.
     */
    fun importJson(uri: Uri, mode: ImportMode) {
        viewModelScope.launch {
            extras.update { it.copy(isBusy = true) }
            val importMessage = repository.importJson(uri, mode).fold({ "已导入 $it 本书" }, { "导入失败：${it.message.orEmpty()}" })
            extras.update { it.copy(message = importMessage, isBusy = false) }
        }
    }

    /**
     * Exports CSV to a selected document.
     */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            extras.update { it.copy(isBusy = true) }
            val exportMessage = repository.exportCsv(uri).fold({ it }, { "导出失败：${it.message.orEmpty()}" })
            extras.update { it.copy(message = exportMessage, isBusy = false) }
        }
    }

    /**
     * Exports the standard CSV import template.
     */
    fun exportCsvTemplate(uri: Uri) {
        viewModelScope.launch {
            extras.update { it.copy(isBusy = true) }
            val exportMessage = repository.exportCsvTemplate(uri).fold({ it }, { "模板导出失败：${it.message.orEmpty()}" })
            extras.update { it.copy(message = exportMessage, isBusy = false) }
        }
    }

    /**
     * Imports CSV from a selected document.
     */
    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            extras.update { it.copy(isBusy = true) }
            val importMessage = repository.importCsv(uri).fold({ "已导入 $it 本书" }, { "导入失败：${it.message.orEmpty()}" })
            extras.update { it.copy(message = importMessage, isBusy = false) }
        }
    }

    /**
     * Searches Douban catalog by ISBN or title.
     */
    fun searchCatalog(query: String, isIsbn: Boolean) {
        if (query.isBlank()) {
            extras.update { it.copy(message = "请输入 ISBN 或书名") }
            return
        }
        viewModelScope.launch {
            extras.update {
                it.copy(
                    catalogQuery = query.trim(),
                    catalogCandidates = emptyList(),
                    isCatalogLoading = true,
                    message = null,
                )
            }
            repository.searchCatalog(query.trim(), isIsbn)
                .onSuccess { candidates ->
                    extras.update {
                        it.copy(
                            catalogCandidates = candidates,
                            isCatalogLoading = false,
                            message = if (candidates.isEmpty()) "没有找到相关书籍，可手动录入" else null,
                        )
                    }
                }
                .onFailure { error ->
                    extras.update {
                        it.copy(
                            isCatalogLoading = false,
                            message = "联网搜索失败：${error.message.orEmpty()}",
                        )
                    }
                }
        }
    }

    /**
     * Uses selected catalog metadata as the next add-book draft.
     */
    fun useCandidate(candidate: BookCandidate, onReady: () -> Unit) {
        viewModelScope.launch {
            extras.update { it.copy(isCatalogLoading = true) }
            val selected = repository.enrichCandidate(candidate).getOrElse { candidate }
            extras.update {
                it.copy(
                    draftBook = selected.toBook(),
                    isCatalogLoading = false,
                    message = "已回填书籍信息",
                )
            }
            onReady()
        }
    }

    /**
     * Clears draft metadata when entering manual add.
     */
    fun clearDraft() {
        extras.update { it.copy(draftBook = null) }
    }

    /**
     * Enables the app-open password for the first time.
     */
    fun setInitialPassword(password: String) {
        if (password.length < 4) {
            extras.update { it.copy(message = "密码至少 4 位") }
            return
        }
        passwordStore.setPassword(password)
        extras.update { it.copy(hasPassword = true, isUnlocked = true, needsPasswordSetup = false, message = "已设置打开密码") }
    }

    /**
     * Changes an existing app-open password after verifying the old password.
     */
    fun changePassword(oldPassword: String, newPassword: String) {
        if (!passwordStore.hasPassword()) {
            setInitialPassword(newPassword)
            return
        }
        if (!passwordStore.verify(oldPassword)) {
            extras.update { it.copy(message = "旧密码不正确") }
            return
        }
        if (newPassword.length < 4) {
            extras.update { it.copy(message = "新密码至少 4 位") }
            return
        }
        passwordStore.setPassword(newPassword)
        extras.update { it.copy(hasPassword = true, isUnlocked = true, needsPasswordSetup = false, message = "密码已更新") }
    }

    /**
     * Unlocks the app with the app-open password.
     */
    fun unlock(password: String) {
        if (passwordStore.verify(password)) {
            extras.update { it.copy(isUnlocked = true, message = null) }
        } else {
            extras.update { it.copy(message = "密码不正确") }
        }
    }

    /**
     * Removes the app-open password.
     */
    fun clearPassword(oldPassword: String) {
        if (passwordStore.hasPassword() && !passwordStore.verify(oldPassword)) {
            extras.update { it.copy(message = "旧密码不正确") }
            return
        }
        passwordStore.clearPassword()
        extras.update { it.copy(hasPassword = false, isUnlocked = true, needsPasswordSetup = false, message = "已关闭打开密码") }
    }

    /**
     * Skips first-run password setup.
     */
    fun skipPasswordSetup() {
        passwordStore.skipSetup()
        extras.update { it.copy(isUnlocked = true, needsPasswordSetup = false) }
    }

    /**
     * Acknowledges the first-run notice.
     */
    fun acceptIntro() {
        passwordStore.acceptIntro()
        extras.update { it.copy(needsIntro = false) }
    }

    /**
     * Clears transient user feedback.
     */
    fun clearMessage() {
        extras.update { it.copy(message = null) }
    }
}

private fun String.normalizedIsbn(): String {
    return filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
}

private data class FilterState(
    val query: String = "",
    val selectedCategory: String = "",
    val selectedShelf: String = "",
    val selectedStatus: ReadingStatus? = null,
    val selectedPurchaseStatus: PurchaseStatus? = null,
)

private data class LibraryData(
    val books: List<Book> = emptyList(),
    val categories: List<Category> = emptyList(),
    val shelves: List<Bookshelf> = emptyList(),
    val purchaseSources: List<PurchaseSource> = emptyList(),
)

private data class ScreenExtras(
    val message: String? = null,
    val isBusy: Boolean = false,
    val catalogCandidates: List<BookCandidate> = emptyList(),
    val catalogQuery: String = "",
    val isCatalogLoading: Boolean = false,
    val draftBook: Book? = null,
    val hasPassword: Boolean = false,
    val isUnlocked: Boolean = true,
    val needsPasswordSetup: Boolean = false,
    val needsIntro: Boolean = false,
)

/**
 * State consumed by Compose screens.
 */
data class AppUiState(
    val allBooks: List<Book> = emptyList(),
    val visibleBooks: List<Book> = emptyList(),
    val categories: List<Category> = emptyList(),
    val bookshelves: List<Bookshelf> = emptyList(),
    val purchaseSources: List<PurchaseSource> = emptyList(),
    val query: String = "",
    val selectedCategory: String = "",
    val selectedShelf: String = "",
    val selectedStatus: ReadingStatus? = null,
    val selectedPurchaseStatus: PurchaseStatus? = null,
    val stats: LibraryStats = LibraryStats(),
    val message: String? = null,
    val isBusy: Boolean = false,
    val catalogCandidates: List<BookCandidate> = emptyList(),
    val catalogQuery: String = "",
    val isCatalogLoading: Boolean = false,
    val draftBook: Book? = null,
    val hasPassword: Boolean = false,
    val isUnlocked: Boolean = true,
    val needsPasswordSetup: Boolean = false,
    val needsIntro: Boolean = false,
)
