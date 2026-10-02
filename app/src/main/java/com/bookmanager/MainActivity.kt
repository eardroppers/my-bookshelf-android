package com.bookmanager

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bookmanager.core.domain.model.BookCandidate
import com.bookmanager.core.domain.model.Book
import com.bookmanager.core.domain.model.BookCondition
import com.bookmanager.core.domain.model.ImportMode
import com.bookmanager.core.domain.model.LibraryStats
import com.bookmanager.core.domain.model.PurchaseStatus
import com.bookmanager.core.domain.model.ReadingStatus
import com.bookmanager.ui.theme.BookManagerTheme
import com.bookmanager.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Main Android entry point.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BookManagerTheme {
                val viewModel: AppViewModel = viewModel()
                BookManagerApp(viewModel)
            }
        }
    }
}

@Composable
private fun BookManagerApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingJsonMode by remember { mutableStateOf(ImportMode.MERGE) }
    val returnToLibrary: () -> Unit = {
        navController.navigate(Route.Library.path) {
            popUpTo(Route.Library.path) { inclusive = false }
            launchSingleTop = true
        }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportJson(uri)
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importJson(uri, pendingJsonMode)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }
    val exportCsvTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsvTemplate(uri)
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCsv(uri)
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    if (state.needsIntro) {
        FirstRunNoticeScreen(onAccept = viewModel::acceptIntro)
        return
    }

    if (!state.isUnlocked || state.needsPasswordSetup) {
        PasswordGateScreen(
            needsSetup = state.needsPasswordSetup,
            onUnlock = viewModel::unlock,
            onSetPassword = viewModel::setInitialPassword,
            onSkipSetup = viewModel::skipPasswordSetup,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { BottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Library.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(Route.Library.path) {
                LibraryScreen(
                    state = state,
                    onQueryChange = viewModel::setQuery,
                    onCategoryFilter = viewModel::setCategoryFilter,
                    onShelfFilter = viewModel::setShelfFilter,
                    onStatusFilter = viewModel::setStatusFilter,
                    onPurchaseStatusFilter = viewModel::setPurchaseStatusFilter,
                    onClearFilters = viewModel::clearFilters,
                    onBookClick = { navController.navigate("detail/$it") },
                    onAdd = {
                        viewModel.clearDraft()
                        navController.navigate(Route.Add.path)
                    },
                    onScan = { navController.navigate(Route.Scan.path) },
                    onCatalogOpen = { navController.navigate(Route.CatalogSearch.path) },
                    onCatalogSearch = { query, isIsbn ->
                        viewModel.searchCatalog(query, isIsbn)
                        navController.navigate(Route.CatalogSearch.path)
                    },
                )
            }
            composable(Route.Stats.path) { StatsScreen(state.stats) }
            composable(Route.CatalogSearch.path) {
                CatalogSearchScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSearch = viewModel::searchCatalog,
                    onUseCandidate = { candidate ->
                        viewModel.useCandidate(candidate) {
                            navController.navigate(Route.Add.path)
                        }
                    },
                )
            }
            composable(Route.Settings.path) {
                SettingsScreen(
                    state = state,
                    onManageCategory = { navController.navigate(Route.Categories.path) },
                    onManageShelf = { navController.navigate(Route.Shelves.path) },
                    onManagePurchaseSource = { navController.navigate(Route.PurchaseSources.path) },
                    onClearAll = viewModel::clearAll,
                    onSetInitialPassword = viewModel::setInitialPassword,
                    onChangePassword = viewModel::changePassword,
                    onClearPassword = viewModel::clearPassword,
                    isBusy = state.isBusy,
                    onExportJson = { exportJson.launch("backup_${timestamp()}.json") },
                    onImportJson = {
                        pendingJsonMode = it
                        importJson.launch(arrayOf("application/json", "text/*"))
                    },
                    onExportCsv = { exportCsv.launch("books_${timestamp()}.csv") },
                    onExportCsvTemplate = { exportCsvTemplate.launch("books_import_template.csv") },
                    onImportCsv = { importCsv.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                )
            }
            composable(Route.Add.path) {
                BookFormScreen(
                    title = "添加书籍",
                    state = state,
                    initialBook = state.draftBook ?: Book(),
                    onBack = { navController.popBackStack() },
                    onSearch = { query, isIsbn ->
                        viewModel.searchCatalog(query, isIsbn)
                        navController.navigate(Route.CatalogSearch.path)
                    },
                    onSave = { book -> viewModel.saveBook(book) { returnToLibrary() } },
                )
            }
            composable(
                route = "add_isbn/{isbn}",
                arguments = listOf(navArgument("isbn") { type = NavType.StringType }),
            ) { entry ->
                BookFormScreen(
                    title = "添加书籍",
                    state = state,
                    initialBook = Book(isbn = entry.arguments?.getString("isbn").orEmpty()),
                    onBack = { navController.popBackStack() },
                    onSearch = { query, isIsbn ->
                        viewModel.searchCatalog(query, isIsbn)
                        navController.navigate(Route.CatalogSearch.path)
                    },
                    onSave = { book -> viewModel.saveBook(book) { returnToLibrary() } },
                )
            }
            composable(
                route = "detail/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: 0L
                DetailScreen(
                    book = viewModel.findBook(bookId),
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("edit/$bookId") },
                    onDelete = { viewModel.deleteBook(bookId) { navController.popBackStack() } },
                )
            }
            composable(
                route = "edit/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) { entry ->
                val book = viewModel.findBook(entry.arguments?.getLong("bookId") ?: 0L)
                BookFormScreen(
                    title = "编辑书籍",
                    state = state,
                    initialBook = book ?: Book(),
                    onBack = { navController.popBackStack() },
                    onSearch = { query, isIsbn ->
                        viewModel.searchCatalog(query, isIsbn)
                        navController.navigate(Route.CatalogSearch.path)
                    },
                    onSave = { updated -> viewModel.saveBook(updated) { navController.popBackStack() } },
                )
            }
            composable(Route.Scan.path) {
                ScanScreen(
                    onBack = { navController.popBackStack() },
                    onIsbnDetected = { isbn ->
                        viewModel.searchCatalog(isbn, true)
                        navController.navigate(Route.CatalogSearch.path)
                    },
                    onManualIsbn = { isbn ->
                        viewModel.searchCatalog(isbn, true)
                        navController.navigate(Route.CatalogSearch.path)
                    },
                )
            }
            composable(Route.Categories.path) {
                ManageNamesScreen(
                    title = "分类管理",
                    label = "分类名称",
                    names = state.categories.map { it.name },
                    onBack = { navController.popBackStack() },
                    onAdd = viewModel::addCategory,
                    onDelete = viewModel::deleteCategory,
                )
            }
            composable(Route.Shelves.path) {
                ManageNamesScreen(
                    title = "书架管理",
                    label = "书架名称",
                    names = state.bookshelves.map { it.name },
                    onBack = { navController.popBackStack() },
                    onAdd = viewModel::addBookshelf,
                    onDelete = viewModel::deleteBookshelf,
                )
            }
            composable(Route.PurchaseSources.path) {
                ManageNamesScreen(
                    title = "购入来源管理",
                    label = "来源名称",
                    names = state.purchaseSources.map { it.name },
                    onBack = { navController.popBackStack() },
                    onAdd = viewModel::addPurchaseSource,
                    onDelete = viewModel::deletePurchaseSource,
                )
            }
        }
    }
}

@Composable
private fun FirstRunNoticeScreen(onAccept: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("使用说明", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("请先了解本应用的数据边界。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    NoticeLine("无需登录", "本应用不需要注册账号，也没有管理员登录。")
                    NoticeLine("本机优先", "你的书籍信息保存在手机本地；除你主动联网查书外，不会上传书籍清单。")
                    NoticeLine("请做好备份", "换机、卸载、清空数据前，请在设置中导出 CSV 或 JSON 备份。")
                    Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("我已了解，继续")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeLine(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordGateScreen(
    needsSetup: Boolean,
    onUnlock: (String) -> Unit,
    onSetPassword: (String) -> Unit,
    onSkipSetup: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Text(if (needsSetup) "设置打开密码" else "输入打开密码", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (needsSetup) "密码只保存在本机，用于防止他人打开应用查看藏书。"
                    else "请输入本机打开密码。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (needsSetup) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("确认密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        if (needsSetup) {
                            if (password == confirm) onSetPassword(password)
                        } else {
                            onUnlock(password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (needsSetup) "保存密码" else "解锁")
                }
                if (needsSetup) {
                    TextButton(onClick = onSkipSetup, modifier = Modifier.align(Alignment.End)) {
                        Text("暂不设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: AppUiState,
    onQueryChange: (String) -> Unit,
    onCategoryFilter: (String) -> Unit,
    onShelfFilter: (String) -> Unit,
    onStatusFilter: (ReadingStatus?) -> Unit,
    onPurchaseStatusFilter: (PurchaseStatus?) -> Unit,
    onClearFilters: () -> Unit,
    onBookClick: (Long) -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onCatalogOpen: () -> Unit,
    onCatalogSearch: (String, Boolean) -> Unit,
) {
    var quickEntryExpanded by remember { mutableStateOf(false) }
    ScreenScaffold(title = "我的书库") {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    LibraryHeroCard(state = state)
                }
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("搜索书名、作者、ISBN") },
                        singleLine = true,
                    )
                }
                item {
                    SectionHeader("筛选与定位", "当前 ${state.visibleBooks.size} / ${state.allBooks.size} 本")
                    FilterArea(
                        state = state,
                        onCategoryFilter = onCategoryFilter,
                        onShelfFilter = onShelfFilter,
                        onStatusFilter = onStatusFilter,
                        onPurchaseStatusFilter = onPurchaseStatusFilter,
                        onClearFilters = onClearFilters,
                    )
                }
                if (state.visibleBooks.isEmpty()) {
                    item {
                        EmptyLibraryView(onAdd = onAdd, onScan = onScan, onCatalogOpen = onCatalogOpen)
                    }
                } else {
                    items(state.visibleBooks, key = { it.id }) { book ->
                        BookListItem(book = book, onClick = { onBookClick(book.id) })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                if (quickEntryExpanded) {
                    QuickEntryCard(
                        onScan = onScan,
                        onCatalogSearch = onCatalogSearch,
                        onManualAdd = onAdd,
                    )
                } else {
                    Button(
                        onClick = { quickEntryExpanded = true },
                        modifier = Modifier.size(64.dp).align(Alignment.CenterEnd),
                        shape = RoundedCornerShape(999.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    ) {
                        Text("录入")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeroCard(state: AppUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.LocalLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("本机书库", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                    Text("${state.allBooks.size} 本藏书", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
                LocalOnlyBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                ReadingStatus.visibleEntries.forEach { status ->
                    val count = state.stats.readingStatusDistribution[status] ?: 0
                    HeroMetricPill(status.label, "$count", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LocalOnlyBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        Text("本机", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HeroMetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickEntryCard(
    modifier: Modifier = Modifier,
    onScan: () -> Unit,
    onCatalogSearch: (String, Boolean) -> Unit,
    onManualAdd: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("扫码录入，或者输入书名 / ISBN 搜索录入。", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("书名或 ISBN") },
                    singleLine = true,
                )
                Button(onClick = { onCatalogSearch(query, query.looksLikeIsbn()) }, enabled = query.isNotBlank()) { Text("搜索") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onManualAdd,
                    modifier = Modifier.weight(1f),
                ) { Text("手动录") }
                Button(
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                ) { Text("扫码录") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterArea(
    state: AppUiState,
    onCategoryFilter: (String) -> Unit,
    onShelfFilter: (String) -> Unit,
    onStatusFilter: (ReadingStatus?) -> Unit,
    onPurchaseStatusFilter: (PurchaseStatus?) -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            state.categories.forEach { category ->
                AssistChip(
                    onClick = { onCategoryFilter(category.name) },
                    label = { Text(category.name) },
                    leadingIcon = if (state.selectedCategory == category.name) {
                        { Text("✓") }
                    } else null,
                )
            }
            state.bookshelves.forEach { shelf ->
                AssistChip(
                    onClick = { onShelfFilter(shelf.name) },
                    label = { Text(shelf.name) },
                    leadingIcon = if (state.selectedShelf == shelf.name) {
                        { Text("✓") }
                    } else null,
                )
            }
            ReadingStatus.visibleEntries.forEach { status ->
                AssistChip(
                    onClick = { onStatusFilter(status) },
                    label = { Text(status.label) },
                    leadingIcon = if (state.selectedStatus == status) {
                        { Text("✓") }
                    } else null,
                )
            }
            PurchaseStatus.entries.forEach { status ->
                AssistChip(
                    onClick = { onPurchaseStatusFilter(status) },
                    label = { Text(status.label) },
                    leadingIcon = if (state.selectedPurchaseStatus == status) {
                        { Text("✓") }
                    } else null,
                )
            }
            AssistChip(onClick = onClearFilters, label = { Text("清除筛选") })
        }
    }
}

@Composable
private fun EmptyLibraryView(onAdd: () -> Unit, onScan: () -> Unit, onCatalogOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Text("还没有书籍", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("可以扫码录、联网录或手动录。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onScan) { Text("扫码录") }
                OutlinedButton(onClick = onCatalogOpen) { Text("联网录") }
                Button(onClick = onAdd) { Text("手动录") }
            }
        }
    }
}

@Composable
private fun BookListItem(book: Book, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(width = 58.dp, height = 78.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    book.coverImagePath.isNotBlank() -> LocalPhoto(path = book.coverImagePath, modifier = Modifier.fillMaxSize())
                    book.coverUrl.isNotBlank() -> RemoteCover(url = book.coverUrl, modifier = Modifier.fillMaxSize())
                    else -> Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(book.author, book.publisher).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未填写作者/出版社" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    MiniLabel(book.readingStatus.label, prominent = true)
                    MiniLabel(book.purchaseStatus.label)
                    MiniLabel(book.condition.label)
                    MiniLabel(book.category.ifBlank { "未分类" })
                }
                if (book.bookshelves.isNotEmpty()) {
                    Text(book.bookshelves.joinToString(" / "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MiniLabel(text: String, prominent: Boolean = false) {
    val background = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        color = foreground,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BookFormScreen(
    title: String,
    state: AppUiState,
    initialBook: Book,
    onBack: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onSave: (Book) -> Unit,
) {
    var book by remember(initialBook) { mutableStateOf(initialBook) }
    val context = LocalContext.current
    var pendingPhotoPath by remember { mutableStateOf("") }
    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoPath.isNotBlank()) {
            book = book.copy(coverImagePath = pendingPhotoPath)
        }
    }
    val scrollState = rememberScrollState()

    ScreenScaffold(title = title, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(book.title, { book = book.copy(title = it) }, Modifier.fillMaxWidth(), label = { Text("书名 *") })
            OutlinedTextField(book.isbn, { book = book.copy(isbn = it) }, Modifier.fillMaxWidth(), label = { Text("ISBN") })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = { onSearch(book.isbn, true) },
                    modifier = Modifier.weight(1f),
                    enabled = book.isbn.isNotBlank(),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("按 ISBN 查")
                }
                OutlinedButton(
                    onClick = { onSearch(book.title, false) },
                    modifier = Modifier.weight(1f),
                    enabled = book.title.isNotBlank(),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("按书名查")
                }
            }
            OutlinedTextField(book.author, { book = book.copy(author = it) }, Modifier.fillMaxWidth(), label = { Text("作者") })
            OutlinedTextField(book.publisher, { book = book.copy(publisher = it) }, Modifier.fillMaxWidth(), label = { Text("出版社") })
            OutlinedTextField(book.publishDate, { book = book.copy(publishDate = it) }, Modifier.fillMaxWidth(), label = { Text("出版日期") })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = book.price?.toString().orEmpty(),
                    onValueChange = { book = book.copy(price = it.toDoubleOrNull()) },
                    modifier = Modifier.weight(1f),
                    label = { Text("原价") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = book.purchasePrice?.toString().orEmpty(),
                    onValueChange = { book = book.copy(purchasePrice = it.toDoubleOrNull()) },
                    modifier = Modifier.weight(1f),
                    label = { Text("购买价") },
                    singleLine = true,
                )
            }
            OutlinedTextField(book.purchaseDate, { book = book.copy(purchaseDate = it) }, Modifier.fillMaxWidth(), label = { Text("购买日期") })
            Text("购入来源", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.purchaseSources.forEach { source ->
                    AssistChip(
                        onClick = {
                            book = book.copy(purchaseSource = if (book.purchaseSource == source.name) "" else source.name)
                        },
                        label = { Text(source.name) },
                        leadingIcon = if (book.purchaseSource == source.name) {
                            { Text("✓") }
                        } else null,
                    )
                }
            }
            OutlinedTextField(
                value = book.category,
                onValueChange = { book = book.copy(category = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("书籍类型") },
                supportingText = { Text("优先使用检索结果；没有获取到时可手动填写或选择。") },
                singleLine = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.categories.forEach { category ->
                    AssistChip(
                        onClick = { book = book.copy(category = category.name) },
                        label = { Text(category.name) },
                        leadingIcon = if (book.category == category.name) {
                            { Text("✓") }
                        } else null,
                    )
                }
            }
            Text("阅读状态", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ReadingStatus.visibleEntries.forEach { status ->
                    AssistChip(
                        onClick = { book = book.copy(readingStatus = status) },
                        label = { Text(status.label) },
                        leadingIcon = if (book.readingStatus == status) {
                            { Text("✓") }
                        } else null,
                    )
                }
            }
            Text("购买状态", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PurchaseStatus.entries.forEach { status ->
                    AssistChip(
                        onClick = { book = book.copy(purchaseStatus = status) },
                        label = { Text(status.label) },
                        leadingIcon = if (book.purchaseStatus == status) {
                            { Text("✓") }
                        } else null,
                    )
                }
            }
            Text("书籍成色", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BookCondition.entries.forEach { condition ->
                    AssistChip(
                        onClick = { book = book.copy(condition = condition) },
                        label = { Text(condition.label) },
                        leadingIcon = if (book.condition == condition) {
                            { Text("✓") }
                        } else null,
                    )
                }
            }
            Text("所属书架", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.bookshelves.forEach { shelf ->
                    val selected = shelf.name in book.bookshelves
                    AssistChip(
                        onClick = {
                            book = book.copy(
                                bookshelves = if (selected) book.bookshelves - shelf.name else book.bookshelves + shelf.name,
                            )
                        },
                        label = { Text(shelf.name) },
                        leadingIcon = if (selected) {
                            { Text("✓") }
                        } else null,
                    )
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("书籍状态照片", style = MaterialTheme.typography.titleSmall)
                    if (book.coverImagePath.isNotBlank()) {
                        LocalPhoto(path = book.coverImagePath, modifier = Modifier.fillMaxWidth().height(180.dp))
                    } else {
                        Text("可拍摄封面、书脊、磨损处等，用于记录当前品相。", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = {
                            val (uri, path) = createBookPhotoUri(context)
                            pendingPhotoPath = path
                            takePhotoLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(if (book.coverImagePath.isBlank()) "拍状态照片" else "重新拍照")
                    }
                }
            }
            OutlinedTextField(book.notes, { book = book.copy(notes = it) }, Modifier.fillMaxWidth().height(120.dp), label = { Text("备注") })
            Button(onClick = { onSave(book) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("保存")
            }
        }
    }
}

@Composable
private fun DetailScreen(book: Book?, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = "书籍详情",
        onBack = onBack,
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
        },
    ) {
        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有找到这本书") }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                BookListItem(book = book, onClick = {})
                InfoRow("ISBN", book.isbn)
                InfoRow("作者", book.author)
                InfoRow("出版社", book.publisher)
                InfoRow("出版日期", book.publishDate)
                InfoRow("原价", book.price?.let { "¥%.2f".format(it) }.orEmpty())
                InfoRow("购买价", book.purchasePrice?.let { "¥%.2f".format(it) }.orEmpty())
                InfoRow("购买日期", book.purchaseDate)
                InfoRow("购入来源", book.purchaseSource)
                InfoRow("分类", book.category)
                InfoRow("书架", book.bookshelves.joinToString(" / "))
                InfoRow("阅读状态", book.readingStatus.label)
                InfoRow("购买状态", book.purchaseStatus.label)
                InfoRow("书籍成色", book.condition.label)
                if (book.coverImagePath.isNotBlank()) {
                    Text("状态照片", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    LocalPhoto(path = book.coverImagePath, modifier = Modifier.fillMaxWidth().height(240.dp))
                } else if (book.coverUrl.isNotBlank()) {
                    Text("联网封面", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    RemoteCover(url = book.coverUrl, modifier = Modifier.fillMaxWidth().height(240.dp))
                }
                InfoRow("备注", book.notes)
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除书籍") },
            text = { Text("确定要删除这本书吗？此操作不可撤销。") },
            confirmButton = { TextButton(onClick = onDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        Divider()
    }
}

@Composable
private fun StatsScreen(stats: LibraryStats) {
    ScreenScaffold(title = "藏书统计") {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item { StatsHeroCard(stats) }
            item { SectionHeader("核心指标") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard("藏书总量", "${stats.totalCount} 本", Modifier.weight(1f))
                    StatCard("购书总花费", "¥%.2f".format(stats.totalSpent), Modifier.weight(1f))
                }
            }
            item { SectionHeader("分布") }
            item { DistributionCard("分类分布", stats.categoryDistribution) }
            item { DistributionCard("阅读状态", stats.readingStatusDistribution.mapKeys { it.key.label }) }
        }
    }
}

@Composable
private fun StatsHeroCard(stats: LibraryStats) {
    val readCount = stats.readingStatusDistribution[ReadingStatus.READ] ?: 0
    val unreadCount = stats.totalCount - readCount
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.InsertChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("阅读概况", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("已读 $readCount 本，待处理 $unreadCount 本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("统计完全来自本机数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CatalogSearchScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onUseCandidate: (BookCandidate) -> Unit,
) {
    var query by remember(state.catalogQuery) { mutableStateOf(state.catalogQuery) }
    var isIsbn by remember { mutableStateOf(query.looksLikeIsbn()) }
    ScreenScaffold(title = "联网查书", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        isIsbn = it.looksLikeIsbn()
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("ISBN 或书名") },
                    singleLine = true,
                )
                Button(onClick = { onSearch(query, isIsbn) }) {
                    Text("搜索")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(
                    onClick = { isIsbn = true },
                    label = { Text("ISBN") },
                    leadingIcon = if (isIsbn) ({ Text("✓") }) else null,
                )
                AssistChip(
                    onClick = { isIsbn = false },
                    label = { Text("书名") },
                    leadingIcon = if (!isIsbn) ({ Text("✓") }) else null,
                )
            }
            Text("数据源：国家出版发行信息公共服务平台。仅在你主动搜索时联网。", style = MaterialTheme.typography.bodySmall)
            if (state.isCatalogLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!state.isCatalogLoading && state.catalogCandidates.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("暂无候选结果")
                        Text("可以检查 ISBN，或改用书名搜索。")
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.catalogCandidates, key = { it.sourceUrl.ifBlank { it.title + it.isbn } }) { candidate ->
                    CandidateItem(candidate = candidate, onClick = { onUseCandidate(candidate) })
                }
            }
        }
    }
}

@Composable
private fun CandidateItem(candidate: BookCandidate, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier.size(width = 48.dp, height = 66.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (candidate.coverUrl.isNotBlank()) {
                    RemoteCover(candidate.coverUrl, Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(candidate.title.ifBlank { "未命名书籍" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(candidate.author, candidate.publisher, candidate.publishDate).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "点击后尝试补全详细信息" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (candidate.category.isNotBlank()) Text("分类：${candidate.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (candidate.isbn.isNotBlank()) Text("ISBN ${candidate.isbn}", style = MaterialTheme.typography.bodySmall)
                Text(candidate.sourceName.ifBlank { "国家出版发行信息公共服务平台" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DistributionCard(title: String, distribution: Map<String, Int>) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (distribution.isEmpty()) {
                Text("暂无数据")
            } else {
                val max = distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                distribution.entries.sortedByDescending { it.value }.forEach { (name, count) ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name)
                            Text("$count")
                        }
                        LinearProgressIndicator(progress = { count.toFloat() / max }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportScreen(
    isBusy: Boolean,
    onExportJson: () -> Unit,
    onImportJson: (ImportMode) -> Unit,
    onExportCsv: () -> Unit,
    onExportCsvTemplate: () -> Unit,
    onImportCsv: () -> Unit,
) {
    ScreenScaffold(title = "导入/导出") {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SectionHeader("默认 CSV", "Excel / WPS") }
            item { ActionCard("下载导入模板", "先按统一模板整理数据，再导入书籍。", Icons.Default.FileDownload, onExportCsvTemplate) }
            item { ActionCard("导出 CSV", "默认清单，包含分类、状态、成色等字段。", Icons.Default.FileDownload, onExportCsv) }
            item { ActionCard("导入 CSV", "按默认表头批量导入，并保留书籍分类。", Icons.Default.FileUpload, onImportCsv) }
            item { SectionHeader("完整备份", "JSON") }
            item { ActionCard("导出完整备份", "包含书籍、分类和书架。", Icons.Default.FileDownload, onExportJson) }
            item { ActionCard("合并导入备份", "保留现有数据，追加备份中的书籍。", Icons.Default.FileUpload) { onImportJson(ImportMode.MERGE) } }
            item { ActionCard("覆盖导入备份", "清空本机数据后恢复备份。", Icons.Default.FileUpload) { onImportJson(ImportMode.OVERWRITE) } }
            if (isBusy) {
                item { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppUiState,
    onManageCategory: () -> Unit,
    onManageShelf: () -> Unit,
    onManagePurchaseSource: () -> Unit,
    onClearAll: () -> Unit,
    onSetInitialPassword: (String) -> Unit,
    onChangePassword: (String, String) -> Unit,
    onClearPassword: (String) -> Unit,
    isBusy: Boolean,
    onExportJson: () -> Unit,
    onImportJson: (ImportMode) -> Unit,
    onExportCsv: () -> Unit,
    onExportCsvTemplate: () -> Unit,
    onImportCsv: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    ScreenScaffold(title = "设置") {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { SectionHeader("隐私与本机数据", "${state.allBooks.size} 本") }
            item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("打开密码", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.hasPassword) "已启用，修改或关闭前需验证旧密码。" else "未启用，启用后打开应用需要密码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.hasPassword) {
                        OutlinedTextField(
                            value = oldPassword,
                            onValueChange = { oldPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("旧密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = {
                            if (state.hasPassword) {
                                onChangePassword(oldPassword, newPassword)
                            } else {
                                onSetInitialPassword(newPassword)
                            }
                            oldPassword = ""
                            newPassword = ""
                        }) { Text(if (state.hasPassword) "修改密码" else "启用密码") }
                        if (state.hasPassword) {
                            OutlinedButton(onClick = {
                                onClearPassword(oldPassword)
                                oldPassword = ""
                                newPassword = ""
                            }) { Text("关闭密码") }
                        }
                    }
                }
            }
            }
            item { SectionHeader("基础资料") }
            item { ActionCard("分类管理", "维护录入时可选的分类。", Icons.Default.InsertChart, onManageCategory) }
            item { ActionCard("书架管理", "维护书籍所在位置。", Icons.Default.LibraryBooks, onManageShelf) }
            item { ActionCard("购入来源管理", "维护京东、淘宝、孔夫子等购入来源。", Icons.Default.ShoppingCart, onManagePurchaseSource) }
            item { SectionHeader("数据备份", "CSV / JSON") }
            item { ActionCard("下载导入模板", "按统一模板整理书籍信息，减少导入混乱。", Icons.Default.FileDownload, onExportCsvTemplate) }
            item { ActionCard("导出 CSV", "默认清单，适合 Excel / WPS 查看。", Icons.Default.FileDownload, onExportCsv) }
            item { ActionCard("导入 CSV", "按模板批量导入，并保留分类、状态、成色。", Icons.Default.FileUpload, onImportCsv) }
            item { ActionCard("导出完整备份", "JSON 备份包含书籍、分类和书架。", Icons.Default.FileDownload, onExportJson) }
            item { ActionCard("合并导入备份", "保留现有数据，追加备份中的书籍。", Icons.Default.FileUpload) { onImportJson(ImportMode.MERGE) } }
            item { ActionCard("覆盖导入备份", "清空本机数据后恢复备份。", Icons.Default.FileUpload) { onImportJson(ImportMode.OVERWRITE) } }
            if (isBusy) {
                item { CircularProgressIndicator() }
            }
            item { SectionHeader("支持") }
            item { ActionCard("问题反馈", "查看反馈方式和需要提供的信息。", Icons.Default.Feedback) { showFeedback = true } }
            item { ActionCard("软件说明", "查看功能说明、数据原则和更新日志。", Icons.Default.Info) { showAbout = true } }
            item {
            OutlinedButton(onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("清空所有数据")
            }
            }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空所有数据") },
            text = { Text("建议先导出 JSON 备份。确定要清空本机所有书籍、分类和书架吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearAll()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }
    if (showFeedback) {
        AlertDialog(
            onDismissRequest = { showFeedback = false },
            title = { Text("问题反馈") },
            text = { Text("如遇到扫码、导入、备份或闪退问题，请记录操作步骤、手机型号、系统版本和当前应用版本。由于本应用不上传数据，反馈前请先导出备份，避免数据丢失。") },
            confirmButton = { TextButton(onClick = { showFeedback = false }) { Text("知道了") } },
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("软件说明") },
            text = {
                Text(
                    "我的书库是一款轻量级本机藏书管理应用。\n\n" +
                        "主要功能：扫码录入、联网检索录入、手动录入；书籍类型、阅读状态、购买状态、成色、购入来源、书架位置和状态照片管理；首页搜索与筛选；统计概览；CSV 导入导出、CSV 模板、JSON 完整备份与恢复；打开密码保护。\n\n" +
                        "数据原则：无需登录，书籍数据保存在手机本地；仅在你主动扫码或联网检索时访问图书信息源。请定期导出备份。\n\n" +
                        "更新日志：\n" +
                        "1.1.9 统一应用名，新增购入来源，优化空状态和软件说明。\n" +
                        "1.1.8 优化整体视觉风格与兼容性。\n" +
                        "1.1.7 拆分阅读状态和购买状态，优化快速录入。\n" +
                        "1.1.6 新增首次说明、反馈、关于和设置内备份。\n" +
                        "1.1.5 优化密码和导入模板。",
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun ScanScreen(
    onBack: () -> Unit,
    onIsbnDetected: (String) -> Unit,
    onManualIsbn: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    var isbn by remember { mutableStateOf("") }
    ScreenScaffold(title = "扫描 ISBN", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (hasPermission) {
                Card(Modifier.fillMaxWidth().height(360.dp), shape = RoundedCornerShape(10.dp)) {
                    IsbnCameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onIsbnDetected = onIsbnDetected,
                    )
                }
                Text("将书背面的 ISBN 条形码放入画面，识别后会自动联网查书。", style = MaterialTheme.typography.bodySmall)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("需要相机权限", style = MaterialTheme.typography.titleMedium)
                        Text("扫码只在本机完成，不会上传照片。")
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("授权相机")
                        }
                    }
                }
            }
            OutlinedTextField(isbn, { isbn = it.filter { char -> char.isDigit() || char == 'X' || char == 'x' } }, Modifier.fillMaxWidth(), label = { Text("ISBN") })
            Button(onClick = { if (isbn.isNotBlank()) onManualIsbn(isbn) }, modifier = Modifier.fillMaxWidth()) { Text("使用 ISBN 查书") }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun IsbnCameraPreview(modifier: Modifier = Modifier, onIsbnDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var handled by remember { mutableStateOf(false) }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                if (handled) {
                                    imageProxy.close()
                                } else {
                                    analyzeBarcode(imageProxy) { isbn ->
                                        handled = true
                                        onIsbnDetected(isbn)
                                    }
                                }
                            }
                        }
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer,
                        )
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            previewView
        },
    )
}

@ExperimentalGetImage
private fun analyzeBarcode(imageProxy: ImageProxy, onIsbnDetected: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    BarcodeScanning.getClient()
        .process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.asSequence()
                .mapNotNull { it.rawValue ?: it.displayValue }
                .mapNotNull { it.extractIsbn() }
                .firstOrNull()
                ?.let(onIsbnDetected)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

@Composable
private fun ManageNamesScreen(
    title: String,
    label: String,
    names: List<String>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    ScreenScaffold(title = title, onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value, { value = it }, Modifier.weight(1f), label = { Text(label) })
                Button(onClick = {
                    onAdd(value)
                    value = ""
                }) { Text("添加") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(names, key = { it }) { name ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDelete(name) }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f).padding(start = if (onBack == null) Spacing.sm else 0.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                actions()
            }
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        content()
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = NavigationBarDefaults.Elevation,
    ) {
        Route.topLevel.forEach { item ->
            NavigationBarItem(
                selected = route == item.path,
                onClick = {
                    navController.navigate(item.path) {
                        popUpTo(Route.Library.path) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private sealed class Route(
    val path: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Library : Route("library", "书库", Icons.Default.LibraryBooks)
    data object Stats : Route("stats", "统计", Icons.Default.InsertChart)
    data object Export : Route("export", "备份", Icons.Default.FileUpload)
    data object Settings : Route("settings", "设置", Icons.Default.Settings)
    data object Add : Route("add", "添加", Icons.Default.Add)
    data object Scan : Route("scan", "扫码", Icons.Default.QrCodeScanner)
    data object CatalogSearch : Route("catalog_search", "查书", Icons.Default.Search)
    data object Categories : Route("categories", "分类", Icons.Default.InsertChart)
    data object Shelves : Route("shelves", "书架", Icons.Default.LibraryBooks)
    data object PurchaseSources : Route("purchase_sources", "来源", Icons.Default.ShoppingCart)

    companion object {
        val topLevel: List<Route>
            get() = listOf(Library, Stats, Settings)
    }
}

private fun timestamp(): String {
    return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
}

@Composable
private fun LocalPhoto(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
    if (bitmap == null) {
        Box(
            modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("照片不可用")
        }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "书籍状态照片",
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun RemoteCover(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        failed = false
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        failed = bitmap == null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "书籍封面",
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            } else {
                Text("...")
            }
        }
    }
}

private fun createBookPhotoUri(context: android.content.Context): Pair<Uri, String> {
    val dir = File(context.filesDir, "book_photos").apply { mkdirs() }
    val file = File(dir, "book_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return uri to file.absolutePath
}

private fun String.looksLikeIsbn(): Boolean {
    val normalized = filter { it.isDigit() || it == 'X' || it == 'x' }
    return normalized.length == 10 || normalized.length == 13
}

private fun String.extractIsbn(): String? {
    val normalized = filter { it.isDigit() || it == 'X' || it == 'x' }
    return when {
        normalized.length == 13 && (normalized.startsWith("978") || normalized.startsWith("979")) -> normalized
        normalized.length == 10 -> normalized.uppercase(Locale.ROOT)
        else -> null
    }
}
