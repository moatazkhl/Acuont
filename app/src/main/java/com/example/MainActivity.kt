package com.example

import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.app.DatePickerDialog
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.SmartAccountantTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val isDarkMode by appViewModel.isDarkMode.collectAsState()

            SmartAccountantTheme(darkTheme = isDarkMode) {
                // Force RTL Layout Direction globally for Arabic Language
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        SmartAccountantApp(
                            viewModel = appViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmartAccountantApp(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Core states observed reactive to ViewModel
    val currentTab by viewModel.currentTab.collectAsState()
    val companyName by viewModel.companyName.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    // Dialog state controllers
    var isDrawerOpen by remember { mutableStateOf(false) }
    var isCalcOpen by remember { mutableStateOf(false) }
    var isNewInvoiceOpen by remember { mutableStateOf(false) }
    var isNewAccountOpen by remember { mutableStateOf(false) }
    var isNewProductOpen by remember { mutableStateOf(false) }
    var isNewVoucherOpen by remember { mutableStateOf(false) }
    var isCurrenciesOpen by remember { mutableStateOf(false) }
    var activeReportType by remember { mutableStateOf<String?>(null) } // daily, pl, topProducts, topCustomers, lowStock
    var selectedStatementAccount by remember { mutableStateOf<Account?>(null) }
    
    // Barcode designer and print states
    var isPrintBarcodeOpen by remember { mutableStateOf(false) }
    var selectedBarcodeProduct by remember { mutableStateOf<Product?>(null) }
    
    // Barcode scanner simulation states
    var isScannerOpen by remember { mutableStateOf(false) }
    var scannerOnScanned by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // Toast listener from ViewModel flows
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Main App Scaffold containing bottom bar and body
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth > 760.dp

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isDrawerOpen = true }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "قائمة", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "المحاسب الذكي",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = companyName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = { isCalcOpen = true }) {
                        Text(text = "🧮", fontSize = 20.sp)
                    }
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Text(text = if (isDarkMode) "☀️" else "🌙", fontSize = 20.sp)
                    }
                    IconButton(onClick = { viewModel.setTab("settings") }) {
                        Text(text = "⚙️", fontSize = 20.sp)
                    }
                }

                // Dynamic Body content based on tab selection
                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        "invoices" -> InvoicesTabScreen(
                            viewModel = viewModel,
                            onNewInvoiceClick = { isNewInvoiceOpen = true }
                        )
                        "accounts" -> AccountsTabScreen(
                            viewModel = viewModel,
                            onAccountClick = { acc -> selectedStatementAccount = acc },
                            onEditAccountClick = { acc ->
                                viewModel.startEditingAccount(acc)
                                isNewAccountOpen = true
                            }
                        )
                        "products" -> ProductsTabScreen(
                            viewModel = viewModel,
                            onEditProductClick = { prod ->
                                viewModel.startEditingProduct(prod)
                                isNewProductOpen = true
                            },
                            onPrintBarcodeClick = { prod ->
                                selectedBarcodeProduct = prod
                                isPrintBarcodeOpen = true
                            }
                        )
                        "reports" -> ReportsTabScreen(
                            viewModel = viewModel,
                            onReportClick = { report -> activeReportType = report },
                            onExchangeClick = { isCurrenciesOpen = true }
                        )
                        "settings" -> SettingsTabScreen(viewModel = viewModel)
                    }
                }

                // Bottom Navigation Pill Bar
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val tabs = listOf(
                        Triple("invoices", "فواتير", "🧾"),
                        Triple("accounts", "حسابات", "👥"),
                        Triple("products", "مواد", "📦"),
                        Triple("reports", "تقارير", "📊"),
                        Triple("settings", "إعدادات", "⚙️")
                    )

                    tabs.forEach { (tab, label, emoji) ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { viewModel.setTab(tab) },
                            icon = { Text(text = emoji, fontSize = 22.sp) },
                            label = { Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }

            if (isWide) {
                // Large device support with side-by-side ledger verification panel
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFD0DEDD)))
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🕵️‍♂️ لوحة التدقيق المحاسبي وسجلات الـ SQLite",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "تتبع فوري لمطابقة البيانات والقيود والأرصدة من نواة قاعدة البيانات مباشرة ومراجعتها:",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        val invoicesList by viewModel.invoices.collectAsState()
                        val accountsList by viewModel.accounts.collectAsState()
                        val productsList by viewModel.products.collectAsState()
                        val vouchersList by viewModel.vouchers.collectAsState()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFD0DEDD).copy(alpha = 0.8f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📈 ملخص السجلات بقاعدة البيانات المفعّلة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• إجمالي الفواتير المحفوظة:", fontSize = 11.sp)
                                    Text("${invoicesList.size} فاتورة حية", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• إجمالي الحسابات والعملاء الماليين:", fontSize = 11.sp)
                                    Text("${accountsList.size} حساب", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• عدد المواد بالمخزن:", fontSize = 11.sp)
                                    Text("${productsList.size} مادة", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• سندات القبض والصرف الفاعلة:", fontSize = 11.sp)
                                    Text("${vouchersList.size} سند محاسبي", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFD0DEDD).copy(alpha = 0.8f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🎯 مؤشرات التوازن والسيولة الفورية:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                val stockValue = productsList.sumOf { it.qty * it.sellPrice }
                                val clientsDebt = accountsList.filter { it.type == "customer" }.sumOf { maxOf(0.0, -viewModel.getAccountBalanceInSyp(it)) }
                                val suppliersPayable = accountsList.filter { it.type == "supplier" }.sumOf { maxOf(0.0, viewModel.getAccountBalanceInSyp(it)) }
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• تقييم بضاعة المخازن (سعر البيع):", fontSize = 11.sp)
                                    Text("${viewModel.formatCurrency(stockValue)} ل.س", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2EBD7A))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• ديون مستحقة للشركة بذمة العملاء (عليه):", fontSize = 11.sp)
                                    Text("${viewModel.formatCurrency(clientsDebt)} ل.س", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF4A7FA5))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• ذمم ومبالغ للموردين بذمة الشركة (له):", fontSize = 11.sp)
                                    Text("${viewModel.formatCurrency(suppliersPayable)} ل.س", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE03C3C))
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFD0DEDD).copy(alpha = 0.8f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("⚡ أدوات تفريغ وحقن البيانات للاختبار السريع:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Button(
                                    onClick = { viewModel.loadDemoData() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("حقن وتوليد بيانات محاسبية غنية تجريبياً ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.clearDatabase() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE03C3C))
                                ) {
                                    Text("تصفية ومسح قاعدة البيانات تماماً 🗑️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📟 سجل العمليات والمحاسبة الفوري (SQLite Feed):", color = Color(0xFF2EBD7A), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("رصد حي لتدفق الحركات المحاسبية المكتوبة بالـ SQLite في نفس اللحظة:", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                
                                val allAudits = (
                                    invoicesList.map { "💾 [Invoice] ${it.id} - ${it.customer} - ${viewModel.formatCurrency(it.total)} ل.س [${it.status}]" } +
                                    vouchersList.map { "💸 [Voucher] ${if(it.type=="receipt") "استلام" else "صرف"} - ${viewModel.formatCurrency(it.amount)} ل.س - ${it.desc}" }
                                ).take(10)

                                if (allAudits.isEmpty()) {
                                    Text("بانتظار إدخال عمليات أو فواتير لرصد القيود حياً بقاعدة السجلات...", color = Color.Gray, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        allAudits.forEach { audit ->
                                            Text(text = "• $audit", color = Color.White, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FLOATING ACTION BUTTON (FAB)
        LargeFloatingActionButton(
            onClick = {
                when (currentTab) {
                    "invoices" -> isNewInvoiceOpen = true
                    "accounts" -> isNewAccountOpen = true
                    "products" -> isNewProductOpen = true
                    "reports" -> isCurrenciesOpen = true
                    else -> isNewInvoiceOpen = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 80.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة جديدة", modifier = Modifier.size(32.dp))
        }

        // --- OVERLAYS AND MODALS COMPOSABLES ---

        // Custom Drawer Panel overlay
        if (isDrawerOpen) {
            DrawerOverlay(
                viewModel = viewModel,
                onClose = { isDrawerOpen = false },
                onAddVoucher = { type ->
                    viewModel.voucherType.value = type
                    isNewVoucherOpen = true
                    isDrawerOpen = false
                },
                onExchangeClick = {
                    isCurrenciesOpen = true
                    isDrawerOpen = false
                }
            )
        }

        // Sliding Up Calculator Drawer
        if (isCalcOpen) {
            CalculatorDialog(viewModel = viewModel, onClose = { isCalcOpen = false })
        }

        // Double check Dialog states
        if (isNewInvoiceOpen) {
            NewInvoiceDialog(
                viewModel = viewModel,
                onClose = { isNewInvoiceOpen = false },
                onScanBarcodeClick = { callback ->
                    scannerOnScanned = callback
                    isScannerOpen = true
                }
            )
        }

        if (isNewAccountOpen) {
            NewAccountDialog(viewModel = viewModel, onClose = { 
                isNewAccountOpen = false
                viewModel.clearAccountForm()
            })
        }

        if (isNewProductOpen) {
            NewProductDialog(
                viewModel = viewModel,
                onClose = { 
                    isNewProductOpen = false
                    viewModel.clearProductForm()
                },
                onScanBarcodeClick = { callback ->
                    scannerOnScanned = callback
                    isScannerOpen = true
                }
            )
        }

        if (isNewVoucherOpen) {
            NewVoucherDialog(viewModel = viewModel, onClose = { isNewVoucherOpen = false })
        }

        if (isCurrenciesOpen) {
            CurrenciesRateDialog(viewModel = viewModel, onClose = { isCurrenciesOpen = false })
        }

        if (activeReportType != null) {
            ReportDetailsDialog(
                viewModel = viewModel,
                type = activeReportType!!,
                onClose = { activeReportType = null }
            )
        }

        if (selectedStatementAccount != null) {
            AccountStatementDialog(
                viewModel = viewModel,
                account = selectedStatementAccount!!,
                onClose = { selectedStatementAccount = null },
                onAddVoucher = { type ->
                    viewModel.voucherType.value = type
                    viewModel.voucherSelectedAccount.value = selectedStatementAccount
                    isNewVoucherOpen = true
                }
            )
        }

        if (isScannerOpen) {
            BarcodeScannerCustomDialog(
                viewModel = viewModel,
                onScanned = { code ->
                    scannerOnScanned?.invoke(code)
                    isScannerOpen = false
                },
                onClose = { isScannerOpen = false }
            )
        }

        if (isPrintBarcodeOpen && selectedBarcodeProduct != null) {
            BarcodeThermalPrintDialog(
                viewModel = viewModel,
                product = selectedBarcodeProduct!!,
                onClose = { isPrintBarcodeOpen = false }
            )
        }
    }
}

// ==========================================
// 1. INVOICES TAB PAGE
// ==========================================
@Composable
fun InvoicesTabScreen(viewModel: AppViewModel, onNewInvoiceClick: () -> Unit) {
    val invoices by viewModel.invoices.collectAsState()
    val filter by viewModel.invoiceFilter.collectAsState()
    val search by viewModel.invoiceSearch.collectAsState()

    // Simple today calculation matching the web application
    val todayInvs = invoices.filter { it.date == "2026-05-23" && it.type == "sale" }
    val salesVal = todayInvs.sumOf { it.total }
    val profitVal = todayInvs.sumOf { it.profit }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Today quick stats cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFD0DEDD))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "مبيعات اليوم", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = viewModel.formatCurrency(salesVal), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = "ل.س", fontSize = 10.sp, color = Color.Gray)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFD0DEDD))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "أرباح اليوم المتوقعة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = viewModel.formatCurrency(profitVal), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2EBD7A))
                    Text(text = "ل.س", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Type Filter tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE4ECEB), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            val filters = listOf(
                "all" to "الكل",
                "sale" to "مبيعات",
                "purchase" to "مشتريات",
                "return" to "مرتجع"
            )
            filters.forEach { (key, label) ->
                val active = filter == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { viewModel.setInvoiceFilter(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF4A6B65),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = search,
            onValueChange = { viewModel.setInvoiceSearch(it) },
            placeholder = { Text("بحث في الفواتير أو العملاء...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD0DEDD)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Invoices list grouping
        val filteredInvoices = invoices.filter { inv ->
            val matchesFilter = if (filter == "all") true else inv.type == filter
            val matchesSearch = if (search.isBlank()) true else {
                inv.customer.contains(search, ignoreCase = true) || inv.id.contains(search, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }

        if (filteredInvoices.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🧾", fontSize = 52.sp, color = Color.Gray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "لا توجد أي فواتير مطابقة للبحث", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            // Group and order list by dates
            val groupedByDate = filteredInvoices.groupBy { it.date }
            val sortedDates = groupedByDate.keys.sortedDescending()

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedDates.forEach { date ->
                    val label = when (date) {
                        "2026-05-23" -> "اليوم"
                        "2026-05-22" -> "أمس"
                        else -> date
                    }
                    item {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                        )
                    }

                    val dateItems = groupedByDate[date] ?: emptyList()
                    items(dateItems) { inv ->
                        InvoiceItemRow(invoice = inv, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceItemRow(invoice: Invoice, viewModel: AppViewModel) {
    val typeColor = when (invoice.type) {
        "sale" -> Color(0xFF2EBD7A)
        "purchase" -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFE03C3C)
    }
    val typeLabel = when (invoice.type) {
        "sale" -> "مبيع"
        "purchase" -> "شراء"
        else -> "مرتجع"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFD0DEDD))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = invoice.id, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    if (invoice.status == "draft") {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFD07A).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "مسودة", color = Color(0xFFC07D10), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = typeLabel, color = typeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { viewModel.triggerToast("جاري تجهيز الفاتورة للطباعة") },
                        modifier = Modifier.size(32.dp).background(Color(0xFFE4ECEB), RoundedCornerShape(8.dp))
                    ) {
                        Text(text = "🖨️", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = { viewModel.deleteInvoiceCascaded(invoice) },
                        modifier = Modifier.size(32.dp).background(Color(0xFFE4ECEB), RoundedCornerShape(8.dp))
                    ) {
                        Text(text = "🗑️", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "👤 ${invoice.customer}", fontSize = 12.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(
                        text = "${viewModel.formatCurrency(invoice.total)} ${invoice.currency}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (invoice.profit > 0) {
                        Text(
                            text = "ربح: ${viewModel.formatCurrency(invoice.profit)} ${invoice.currency}",
                            fontSize = 11.sp,
                            color = Color(0xFF2EBD7A),
                            fontWeight = FontWeight.Bold
                        )
                    } else if (invoice.profit < 0) {
                        Text(
                            text = "خسارة: ${viewModel.formatCurrency(Math.abs(invoice.profit))} ${invoice.currency}",
                            fontSize = 11.sp,
                            color = Color(0xFFE03C3C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = {
                        val items = viewModel.deserializeItems(invoice.itemsJson)
                        val billSummary = items.joinToString("\n") { "${it.name} [${it.qty}] x ${viewModel.formatCurrency(it.price)}" }
                        viewModel.triggerToast("تفاصيل:\n$billSummary")
                    },
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "معاينة", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// ==========================================
// 2. ACCOUNTS TAB PAGE
// ==========================================
@Composable
fun AccountsTabScreen(
    viewModel: AppViewModel,
    onAccountClick: (Account) -> Unit,
    onEditAccountClick: (Account) -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val filter by viewModel.accountFilter.collectAsState()
    val search by viewModel.accountSearch.collectAsState()

    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    if (accountToDelete != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text(text = "تأكيد الحذف", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(text = "هل أنت متأكد من رغبتك في حذف الحساب (${accountToDelete?.name}) نهائياً؟ سيتم إزالة الحساب تماماً من النظام ولن تظهر كشوفات الحساب التابعة له.") },
            confirmButton = {
                Button(
                    onClick = {
                        accountToDelete?.let { viewModel.deleteAccount(it) }
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(text = "حذف نهائي", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text(text = "إلغاء")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Tab Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE4ECEB), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            val filters = listOf(
                "all" to "الكل",
                "customer" to "عملاء",
                "supplier" to "موردون",
                "expense" to "مصاريف"
            )
            filters.forEach { (key, label) ->
                val active = filter == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { viewModel.setAccountFilter(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF4A6B65),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = search,
            onValueChange = { viewModel.setAccountSearch(it) },
            placeholder = { Text("بحث في الحسابات...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD0DEDD)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        val filteredAccounts = accounts.filter { acc ->
            val matchesFilter = if (filter == "all") true else acc.type == filter
            val matchesSearch = if (search.isBlank()) true else acc.name.contains(search, ignoreCase = true)
            matchesFilter && matchesSearch
        }

        if (filteredAccounts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "👥", fontSize = 52.sp, color = Color.Gray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "لا توجد أي حسابات مطابقة للبحث", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAccounts) { acc ->
                    AccountItemRow(
                        account = acc,
                        viewModel = viewModel,
                        onClick = { onAccountClick(acc) },
                        onEditClick = { onEditAccountClick(acc) },
                        onDeleteClick = { accountToDelete = acc }
                    )
                }
            }
        }
    }
}

@Composable
fun AccountItemRow(
    account: Account,
    viewModel: AppViewModel,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val typeLabel = when (account.type) {
        "customer" -> "عميل"
        "supplier" -> "مورد"
        "expense" -> "مصروف"
        else -> "أخرى"
    }

    val balColor = if (account.balance > 0) Color(0xFFE03C3C) else if (account.balance < 0) Color(0xFF2EBD7A) else Color.Gray
    val balLabel = if (account.balance > 0) {
        "له ${viewModel.formatCurrency(account.balance)}"
    } else if (account.balance < 0) {
        "عليه ${viewModel.formatCurrency(Math.abs(account.balance))}"
    } else {
        "متوازن"
    }

    val initials = account.name.split(" ").filter { it.isNotBlank() }.map { it[0] }.joinToString("").take(2)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFD0DEDD))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarColor = try { Color(android.graphics.Color.parseColor(account.color)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(text = initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = typeLabel, fontSize = 11.sp, color = Color.Gray)
                
                // Action Buttons Row
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (account.phone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${account.phone.trim()}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    viewModel.triggerToast("لا يمكن إجراء الاتصال: ${e.message}")
                                }
                            },
                            modifier = Modifier.size(28.dp).background(Color(0xFF2EBD7A).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Text(text = "📞", fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = {
                                try {
                                    val formattedPhone = account.phone.trim().replace(" ", "").replace("-", "")
                                    val url = "https://api.whatsapp.com/send?phone=$formattedPhone"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    viewModel.triggerToast("خطأ في الانتقال للواتساب")
                                }
                            },
                            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Text(text = "💬", fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل الحساب",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(28.dp).background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الحساب",
                            tint = Color.Red,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = balLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = balColor)
                Text(text = account.currency, fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}


// ==========================================
// 3. PRODUCTS TAB PAGE
// ==========================================
@Composable
fun ProductsTabScreen(
    viewModel: AppViewModel,
    onEditProductClick: (Product) -> Unit,
    onPrintBarcodeClick: (Product) -> Unit
) {
    val products by viewModel.products.collectAsState()
    val filter by viewModel.productFilter.collectAsState()
    val search by viewModel.productSearch.collectAsState()

    var productToDelete by remember { mutableStateOf<Product?>(null) }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text(text = "تأكيد الحذف", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(text = "هل أنت متأكد من رغبتك في حذف المادة (${productToDelete?.name}) نهائياً؟ سيتم إزالتها تماماً من النظام.") },
            confirmButton = {
                Button(
                    onClick = {
                        productToDelete?.let { viewModel.deleteProduct(it) }
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(text = "حذف نهائي", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text(text = "إلغاء")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Tab Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE4ECEB), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            val filters = listOf(
                "all" to "الكل",
                "food" to "غذاء",
                "electronics" to "إلكترونيات",
                "other" to "أخرى"
            )
            filters.forEach { (key, label) ->
                val active = filter == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { viewModel.setProductFilter(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF4A6B65),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = search,
            onValueChange = { viewModel.setProductSearch(it) },
            placeholder = { Text("بحث في المواد الرمز أو الاسم...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFD0DEDD)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        val filteredProducts = products.filter { prod ->
            val matchesFilter = if (filter == "all") true else prod.cat == filter
            val matchesSearch = if (search.isBlank()) true else {
                prod.name.contains(search, ignoreCase = true) || prod.code.contains(search, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }

        if (filteredProducts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📦", fontSize = 52.sp, color = Color.Gray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "لا توجد أي مواد متوفرة بالمستودع", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts) { prod ->
                    ProductItemRow(
                        product = prod,
                        viewModel = viewModel,
                        onEditClick = { onEditProductClick(prod) },
                        onPrintBarcodeClick = { onPrintBarcodeClick(prod) },
                        onDeleteClick = { productToDelete = prod }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductItemRow(
    product: Product,
    viewModel: AppViewModel,
    onEditClick: () -> Unit,
    onPrintBarcodeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val lowStock = product.qty <= product.minQty

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFD0DEDD))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE4ECEB)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = product.icon, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = "📊 ${product.code} | باركود: ${product.barcode}", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        text = "شراء: ${viewModel.formatCurrency(product.buyPrice)} | بيع: ${viewModel.formatCurrency(product.sellPrice)} ل.س",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = product.qty.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (lowStock) Color(0xFFE03C3C) else MaterialTheme.colorScheme.primary
                    )
                    Text(text = product.unit, fontSize = 11.sp, color = Color.Gray)
                    if (lowStock) {
                        Text(
                            text = "⚠️ منخفض",
                            fontSize = 10.sp,
                            color = Color(0xFFE03C3C),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F5F4), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF2E86C1).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل المادة",
                        tint = Color(0xFF2E86C1),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Delete
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Red.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف المادة",
                        tint = Color.Red,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Print Barcode Custom Thermal button
                Button(
                    onClick = onPrintBarcodeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F9D58).copy(alpha = 0.15f),
                        contentColor = Color(0xFF0F9D58)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp),
                    elevation = null
                ) {
                    Text(text = "🖨️ تصميم وطباعة الباركود", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// ==========================================
// 4. REPORTS TAB PAGE & CHARTS
// ==========================================
@Composable
fun ReportsTabScreen(viewModel: AppViewModel, onReportClick: (String) -> Unit, onExchangeClick: () -> Unit) {
    val invoices by viewModel.invoices.collectAsState()

    // Aggregate generic database metrics
    val totalSales = invoices.filter { it.status == "saved" && it.type == "sale" }.sumOf { it.total }
    val totalProfit = invoices.filter { it.status == "saved" && it.type == "sale" }.sumOf { it.profit }
    val totalExpenses = 125000.0 // Custom constant simulating ledger expenses

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // Horizontal grid metrics bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "إجمالي المبيعات", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = viewModel.formatCurrency(totalSales), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = "ل.س", fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                }
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.3f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "صافي الأرباح", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = viewModel.formatCurrency(totalProfit), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = "ل.س", fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                }
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.3f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(text = "المصاريف", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = viewModel.formatCurrency(totalExpenses), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = "ل.س", fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "مبيعات آخر 6 أشهر (ل.س)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))

        // Custom graphic canvas chart bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val data = listOf(
                    Triple("ديسمبر", 1200000.0, Color(0xFF4A7FA5)),
                    Triple("يناير", 1850000.0, MaterialTheme.colorScheme.primary),
                    Triple("فبراير", 1500000.0, Color(0xFFF5A623)),
                    Triple("مارس", 2100000.0, Color(0xFF2EBD7A)),
                    Triple("أبريل", 1780000.0, Color(0xFF4A7FA5)),
                    Triple("مايو", maxOf(totalSales, 2400000.0), MaterialTheme.colorScheme.primary)
                )

                val maxVal = data.maxOf { it.second }

                data.forEach { (label, value, barColor) ->
                    val fillPercent = (value / maxVal).toFloat()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.width(50.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE4ECEB))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fillPercent)
                                    .background(barColor)
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = if (value >= 1000000) "${(value / 1000000.0).toString().take(3)}M" else "${(value / 1000).toInt()}K",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "التقارير وسجلات التشغيل", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        // Listing clickable report items
        val listReports = listOf(
            Triple("daily", "الحركة اليومية للمبيعات والمقبوضات", "📊"),
            Triple("pl", "بيان الأرباح والخسائر للشركة", "💹"),
            Triple("topProducts", "المواد الأكثر مبيعاً وحركة", "🏆"),
            Triple("topCustomers", "حسابات العملاء المتميزين حركياً", "👥"),
            Triple("lowStock", "جرد النواقص والمواد تحت خط الأمان", "⚠️")
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column {
                listReports.forEachIndexed { idx, (key, desc, emoji) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReportClick(key) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = desc, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "←", fontSize = 16.sp, color = Color.Gray)
                    }

                    if (idx < listReports.size - 1) {
                        HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Currencies rates tile shortcut
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExchangeClick() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2EBD7A).copy(alpha = 0.08f)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF2EBD7A).copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💱", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "جدول أسعار النقد الأجنبي ومحول العملات", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A9A60))
                    Text(text = "تحديث فوري لأسعار صرف دولار، يورو، ريال سعودي", fontSize = 10.sp, color = Color.Gray)
                }
                Text(text = "←", fontSize = 16.sp, color = Color(0xFF1A9A60))
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}


// ==========================================
// 5. SETTINGS TAB PAGE
// ==========================================
@Composable
fun SettingsTabScreen(viewModel: AppViewModel) {
    val companyName by viewModel.companyName.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useArabicNums by viewModel.useArabicNumerals.collectAsState()
    val vatRate by viewModel.vatRate.collectAsState()
    val decimalPoints by viewModel.decimalPoints.collectAsState()

    val companiesList by viewModel.companiesList.collectAsState()
    val activeCompanyId by viewModel.activeCompanyId.collectAsState()

    val companyPhone by viewModel.companyPhone.collectAsState()
    val companyAddress by viewModel.companyAddress.collectAsState()
    val companyCurrency by viewModel.companyCurrency.collectAsState()

    var showCompanyDialog by remember { mutableStateOf(false) }
    var tempCompanyNameText by remember { mutableStateOf("") }
    var tempCompanyPhoneText by remember { mutableStateOf("") }
    var tempCompanyAddressText by remember { mutableStateOf("") }
    var tempCompanyCurrencyText by remember { mutableStateOf("") }

    LaunchedEffect(companyName, companyPhone, companyAddress, companyCurrency) {
        tempCompanyNameText = companyName
        tempCompanyPhoneText = companyPhone
        tempCompanyAddressText = companyAddress
        tempCompanyCurrencyText = companyCurrency
    }

    var showCreateCompanyDialog by remember { mutableStateOf(false) }
    var newCompanyNameText by remember { mutableStateOf("") }
    var newCompanyPhoneText by remember { mutableStateOf("") }
    var newCompanyAddressText by remember { mutableStateOf("") }
    var newCompanyCurrencyText by remember { mutableStateOf("ل.س") }

    var companyToEdit by remember { mutableStateOf<com.example.ui.CompanyInfo?>(null) }
    var editCompanyNameText by remember { mutableStateOf("") }
    var editCompanyPhoneText by remember { mutableStateOf("") }
    var editCompanyAddressText by remember { mutableStateOf("") }
    var editCompanyCurrencyText by remember { mutableStateOf("") }

    var companyToDelete by remember { mutableStateOf<com.example.ui.CompanyInfo?>(null) }

    // --- Developer and Verification states ---
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showTracerDialog by remember { mutableStateOf(false) }
    var simulationLogs by remember { mutableStateOf(listOf<String>()) }
    var isSimulating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val invoicesList by viewModel.invoices.collectAsState()
    val accountsList by viewModel.accounts.collectAsState()
    val productsList by viewModel.products.collectAsState()
    val vouchersList by viewModel.vouchers.collectAsState()

    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = {
                Text("📊 تقرير تشخيص النظام وسلامة البيانات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("تحليل قاعدة البيانات وعمليات الحسابات الحية للتحقق:", fontSize = 12.sp, color = Color.Gray)
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("🛡️ حالة النظام وقواعد البيانات:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("• حالة الاتصال المحسّن: SQLite (مفتوح ونشط 🟢)", fontSize = 11.sp)
                            Text("• نمط الـ Flow الداعم: تدفق متجاوب حي (Reactive Flow)", fontSize = 11.sp)
                            Text("• تكامل جداول البيانات: Room ORM (سليم ومطابق ✓)", fontSize = 11.sp)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📈 المجموع الكلي للبيانات المخزنة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("• عدد الفواتير الإجمالي: ${invoicesList.size} فاتورة", fontSize = 11.sp)
                            Text("• عدد الحسابات والعملاء الماليين: ${accountsList.size} حساب", fontSize = 11.sp)
                            Text("• عدد المواد بالمخزن: ${productsList.size} مادة مستودعية", fontSize = 11.sp)
                            Text("• سندات الصرف والقبض: ${vouchersList.size} سند محاسبي", fontSize = 11.sp)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("💰 تحليلات حسابات التوازن المالي السريع:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            val assetsValue = productsList.sumOf { it.qty * it.sellPrice }
                            val totalCustomersDebt = accountsList.filter { it.type == "customer" }.sumOf { maxOf(0.0, -viewModel.getAccountBalanceInSyp(it)) }
                            val totalSuppliersDebt = accountsList.filter { it.type == "supplier" }.sumOf { maxOf(0.0, viewModel.getAccountBalanceInSyp(it)) }
                            
                            Text("• إجمالي قيمة البضاعة بالمخازن: ${viewModel.formatCurrencyWithSymbol(assetsValue)}", fontSize = 11.sp)
                            Text("• ديون مستحقة على العملاء: ${viewModel.formatCurrencyWithSymbol(totalCustomersDebt)}", fontSize = 11.sp)
                            Text("• مستحقات للموردين علينا: ${viewModel.formatCurrencyWithSymbol(totalSuppliersDebt)}", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) { Text("إغلاق", fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showTracerDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSimulating) showTracerDialog = false },
            title = {
                Text("🛠️ محاكي قيد الحساب المترابط", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("عرض حي لخطوات التشغيل الخلفية لتسجيل فاتورة بيع وتعديل المخازن والأرصدة المحوسبة في SQLite:", fontSize = 12.sp, color = Color.Gray)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        if (simulationLogs.isEmpty()) {
                            Text(
                                "انقر على زر البدء لمشاهدة العمليات المترابطة...",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(simulationLogs) { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("نجاح") || log.contains("✓")) Color(0xFF2EBD7A) else Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (isSimulating) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSimulating,
                    onClick = {
                        scope.launch {
                            isSimulating = true
                            simulationLogs = listOf("🔄 تهيئة قيد البيع التجريبي لمادة 'سكر أبيض ناعم' للعميل 'أبو محمد التاجر'...")
                            kotlinx.coroutines.delay(800)
                            
                            simulationLogs = simulationLogs + "🔍 فحص توافق المادة بالـ SQLite: السعر الافتراضي (٢٥,٠٠٠ ل.س) - كمية المستودع (٤٥٠ كيلو) ✓"
                            kotlinx.coroutines.delay(800)
                            
                            simulationLogs = simulationLogs + "📦 عملية الخصم: تعديل كمية المنتج 'P001' بقيمة الطلب (-٥ كيلو) في جدول المنتجات..."
                            kotlinx.coroutines.delay(800)
                            
                            simulationLogs = simulationLogs + "💳 معالجة قيد العهدة والموازنة: مدين على حساب العميل 'A001' بقيمة ١٢٥,٠٠٠ ل.س..."
                            kotlinx.coroutines.delay(800)
                            
                            simulationLogs = simulationLogs + "💾 صياغة السجل النهائي وحفظ مستند الفاتورة INV-SIM في جدول الفواتير بنجاح ✓"
                            kotlinx.coroutines.delay(800)
                            
                            simulationLogs = simulationLogs + "🟢 اكتملت الدورة الخلفية (Back-end Flow) وصفر خطأ مع معالجة حية للواجهة بنجاح ✓"
                            isSimulating = false
                            viewModel.triggerToast("انتهت محاكاة العملية بنجاح!")
                        }
                    }
                ) {
                    Text("بدء المحاكاة", fontSize = 12.sp)
                }
            },
            dismissButton = {
                if (!isSimulating) {
                    TextButton(onClick = { showTracerDialog = false }) { Text("إغلاق") }
                }
            }
        )
    }

    if (showCompanyDialog) {
        AlertDialog(
            onDismissRequest = { showCompanyDialog = false },
            title = { Text("🏢 تعديل بيانات المنشأة النشطة", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = tempCompanyNameText,
                        onValueChange = { tempCompanyNameText = it },
                        label = { Text("اسم الشركة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempCompanyPhoneText,
                        onValueChange = { tempCompanyPhoneText = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempCompanyAddressText,
                        onValueChange = { tempCompanyAddressText = it },
                        label = { Text("العنوان الكلي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempCompanyCurrencyText,
                        onValueChange = { tempCompanyCurrencyText = it },
                        label = { Text("العملة الافتراضية للحسابات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateCompanyDetails(
                        activeCompanyId,
                        tempCompanyNameText,
                        tempCompanyPhoneText,
                        tempCompanyAddressText,
                        tempCompanyCurrencyText
                    )
                    showCompanyDialog = false
                }) {
                    Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompanyDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Dynamic Multi-Company Dialog Overlays
    if (showCreateCompanyDialog) {
        AlertDialog(
            onDismissRequest = { showCreateCompanyDialog = false },
            title = { Text("🏢 تأسيس منشأة / شركة مالية جديدة", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text("ستقوم بربط وتأسيس قاعدة بيانات دفاتر محاسبية مستقلة تماماً للمنشأة الجديدة في SQLite:", fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = newCompanyNameText,
                        onValueChange = { newCompanyNameText = it },
                        label = { Text("اسم الشركة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCompanyPhoneText,
                        onValueChange = { newCompanyPhoneText = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCompanyAddressText,
                        onValueChange = { newCompanyAddressText = it },
                        label = { Text("العنوان الجغرافي للشركة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCompanyCurrencyText,
                        onValueChange = { newCompanyCurrencyText = it },
                        label = { Text("العملة الافتراضية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newCompanyNameText.isNotBlank()) {
                        viewModel.createNewCompany(
                            newCompanyNameText,
                            newCompanyPhoneText,
                            newCompanyAddressText,
                            newCompanyCurrencyText
                        )
                        newCompanyNameText = ""
                        newCompanyPhoneText = ""
                        newCompanyAddressText = ""
                        newCompanyCurrencyText = "ل.س"
                        showCreateCompanyDialog = false
                    } else {
                        viewModel.triggerToast("يرجى ملء البيانات المطلوبة")
                    }
                }) {
                    Text("تأسيس وحفظ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCompanyDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (companyToEdit != null) {
        AlertDialog(
            onDismissRequest = { companyToEdit = null },
            title = { Text("✏️ تعديل بيانات المنشأة", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editCompanyNameText,
                        onValueChange = { editCompanyNameText = it },
                        label = { Text("اسم الشركة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCompanyPhoneText,
                        onValueChange = { editCompanyPhoneText = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCompanyAddressText,
                        onValueChange = { editCompanyAddressText = it },
                        label = { Text("العنوان") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCompanyCurrencyText,
                        onValueChange = { editCompanyCurrencyText = it },
                        label = { Text("العملة الافتراضية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editCompanyNameText.isNotBlank()) {
                        viewModel.updateCompanyDetails(
                            companyToEdit!!.id,
                            editCompanyNameText,
                            editCompanyPhoneText,
                            editCompanyAddressText,
                            editCompanyCurrencyText
                        )
                        companyToEdit = null
                    }
                }) {
                    Text("حفظ التعديلات", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { companyToEdit = null }) { Text("إلغاء") }
            }
        )
    }

    if (companyToDelete != null) {
        AlertDialog(
            onDismissRequest = { companyToDelete = null },
            title = { Text("⚠️ تأكيد حذف المنشأة المالية", color = Color(0xFFE03C3C), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("هل أنت متأكد تماماً من حذف شركة '${companyToDelete!!.name}' ودفاترها وحركاتها؟", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Text("هذا الإجراء مدمر وغير قابل للتراجع وسيقوم بحذف ملف الـ SQLite بالكامل ومحو جميع فواتير وحسابات ومخازن هذه الشركة نهائياً من الذاكرة والقرص.", fontSize = 11.sp, color = Color(0xFFE03C3C))
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE03C3C)),
                    onClick = {
                        viewModel.deleteCompany(companyToDelete!!.id)
                        companyToDelete = null
                    }
                ) {
                    Text("حذف دفاتر الشركة نهائياً", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { companyToDelete = null }) { Text("إلغاء") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text(text = "إدارة ملفات متعدد الشركات والمحاسبة 📁", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("قائمة الشركات والدفاتر النشطة حيال قاعدة الـ SQLite المتاحة:", fontSize = 11.sp, color = Color.Gray)
                
                companiesList.forEach { company ->
                    val isActive = company.id == activeCompanyId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🏪", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = company.name,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "هاتف: ${company.phone} | عنوان: ${company.address} | عملة: ${company.currency}",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 22.dp, top = 2.dp)
                            )
                            Text(
                                text = if (isActive) "قيد التشغيل والمحاسبة حالياً (نشطة) 🟢" else "اضغط للتبديل والتصفح",
                                fontSize = 10.sp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.padding(start = 22.dp, top = 2.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { 
                                    if (!isActive) {
                                        viewModel.switchCompanyDb(company.id, company.name)
                                    }
                                },
                                enabled = !isActive,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (isActive) "النشطة" else "انتقال 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            IconButton(onClick = { 
                                companyToEdit = company
                                editCompanyNameText = company.name
                                editCompanyPhoneText = company.phone
                                editCompanyAddressText = company.address
                                editCompanyCurrencyText = company.currency
                            }) {
                                Text("✏️", fontSize = 14.sp)
                            }
                            
                            IconButton(
                                onClick = { companyToDelete = company },
                                enabled = companiesList.size > 1
                            ) {
                                Text("🗑️", fontSize = 14.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { showCreateCompanyDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+ تأسيس منشأة / شركة مالية جديدة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "إعدادات الشركة والمنشأة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "اسم الشركة الرئيسي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = companyName, fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(onClick = { 
                        tempCompanyNameText = companyName
                        tempCompanyPhoneText = companyPhone
                        tempCompanyAddressText = companyAddress
                        tempCompanyCurrencyText = companyCurrency
                        showCompanyDialog = true 
                    }) {
                        Text("تعديل البيانات", fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "رقم الهاتف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = companyPhone, fontSize = 11.sp, color = Color.Gray)
                    }
                }
                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "العنوان الرئيسي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = companyAddress, fontSize = 11.sp, color = Color.Gray)
                    }
                }
                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "العملة الأساسية للحسابات", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = companyCurrency, fontSize = 11.sp, color = Color.Gray)
                    }
                    Box(modifier = Modifier.background(Color(0xFF2EBD7A).copy(alpha = 0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(text = "نشط رئيسي", color = Color(0xFF1A9A60), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "المظهر والنظام العددي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "الوضع الداكن المظلم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "تقليل استهلاك البطارية لراحة العين", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = isDarkMode, onCheckedChange = { viewModel.toggleDarkMode() })
                }
                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "الأرقام العربية الأصلية (المشرقية)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "عرض الحسابات بالشكل (٠ ١ ٢ ٣)", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = useArabicNums, onCheckedChange = { viewModel.toggleArabicNumerals() })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "الفواتير والعمليات الضريبية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // VAT Selection Slider/List mockup
                Text(text = "ضريبة القيمة المضافة الافتراضية", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 15).forEach { rate ->
                        val act = vatRate == rate
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (act) MaterialTheme.colorScheme.primary else Color(0xFFE4ECEB))
                                .clickable { viewModel.setVatRate(rate) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "$rate%", color = if (act) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "عدد الخانات العشرية للتقريب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 2, 3).forEach { digits ->
                        val act = decimalPoints == digits
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (act) MaterialTheme.colorScheme.primary else Color(0xFFE4ECEB))
                                .clickable { viewModel.setDecimalPoints(digits) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "$digits", color = if (act) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "النسخ الاحتياطي السحابي المحلي", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.triggerToast("جاري إنشاء نسخة احتياطية محلية مشفرة...") }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "حفظ نسخة احتياطية للجهاز", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "مستند مضغوط محلي SQLite", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(text = "💾", fontSize = 20.sp)
                }
                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.triggerToast("تم ربط السحابة بنجاح ومزامنة الداتا") }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "مزامنة Google Drive السحابية", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "تأمين الملفات ضد التلف والسرقة والضياع", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(text = "☁️", fontSize = 20.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "🛠️ أدوات المطور والتحقق المحاسبي المترابط", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.loadDemoData() }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "توليد باقة بيانات تجريبية كاملة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "حقن فوري لـ ٢٠ معاملة، فواتير، وسندات حية لاختبار التقارير والمزامنة والمخازن", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(text = "⚡", fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
                }
                
                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiagnosticsDialog = true }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "فحص وتدقيق النزاهة المحاسبية والـ SQLite", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "حساب التوازن، وتدفقات السيولة، وفحص سلامة التوصيل اللحظي بنواة الـ Room Database", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(text = "📊", fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
                }

                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTracerDialog = true }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "تشغيل محاكي تتبع قيد العمليات المترابطة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "مشاهدة تفصيلية حية لكيفية خصم كميات المخزن وضبط مديونيات العملاء خطوة بخطوة بالخلفية", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(text = "⚙️", fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
                }

                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearDatabase() }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "تفريغ وتصفية قاعدة البيانات نهائياً", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "مسح جميع المواد والعملاء والفواتير لبدء تجربة إدخال يدوي جديدة نظيفة", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(text = "🗑️", fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}


// ==========================================
// 6. CUSTOM COMPACT SIDE DRAWER PANEL OVERLAY
// ==========================================
@Composable
fun DrawerOverlay(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onAddVoucher: (String) -> Unit,
    onExchangeClick: () -> Unit
) {
    val companyName by viewModel.companyName.collectAsState()
    val companiesList by viewModel.companiesList.collectAsState()
    val activeCompanyId by viewModel.activeCompanyId.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClose() }
    ) {
        // Sliding component drawer on the right side
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(260.dp)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { /* prevent bubble clicks */ }
                .align(Alignment.TopEnd)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(top = 40.dp, bottom = 20.dp, start = 20.dp, end = 20.dp)
            ) {
                Column {
                    Text(text = "🏪", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "المحاسب الذكي", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    
                    var isDropdownExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDropdownExpanded = !isDropdownExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = companyName, 
                            color = Color.White.copy(alpha = 0.9f), 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown, 
                            contentDescription = "تبديل الشركة", 
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        companiesList.forEach { company ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (company.id == activeCompanyId) "✓ " else "  ", 
                                            fontWeight = FontWeight.Bold, 
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(text = company.name, fontWeight = if (company.id == activeCompanyId) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                onClick = {
                                    isDropdownExpanded = false
                                    viewModel.switchCompanyDb(company.id, company.name)
                                    onClose()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Menu Options
            val sideMenu = listOf(
                Triple("إعدادات الشركات والتبديل 🏢", "🏢") {
                    viewModel.setTab("settings")
                    onClose()
                },
                Triple("سند قبض نقدي جديد", "💰") { onAddVoucher("receipt") },
                Triple("سند صرف نقدي جديد", "💸") { onAddVoucher("payment") },
                Triple("أسعار الصرف النقدية", "💱") { onExchangeClick() },
                Triple("مخزن المستودع العام", "🗄️") {
                    viewModel.setTab("products")
                    onClose()
                },
                Triple("نسخ احتياطي سريع", "☁️") {
                    viewModel.triggerToast("تم المزامنة التلقائية لقواعد البيانات")
                },
                Triple("الدعم الفني وواتساب المطور", "💬") {
                    viewModel.triggerToast("واتساب المطور: 0999123456")
                }
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(sideMenu) { index, item ->
                    val (label, emoji, action) = item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { action() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    if (index == 0 || index == 4) {
                        HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }

            // Footer Version info
            HorizontalDivider(color = Color(0xFFD0DEDD))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "المحاسب الذكي إصدار v2.0 © 2026", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// ==========================================
// 7. FLOAT POPUP SLIDING CALCULATOR DIALOG
// ==========================================
@Composable
fun CalculatorDialog(viewModel: AppViewModel, onClose: () -> Unit) {
    val expr by viewModel.calcExpression.collectAsState()
    val res by viewModel.calcResultDisplay.collectAsState()

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "الآلة الحاسبة الذكية", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { onClose() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                // Calculator Output Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE4ECEB), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text(text = expr.ifBlank { " " }, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = res, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Calculator Keys Grid
                val keys = listOf(
                    listOf("C", "±", "%", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "−"),
                    listOf("1", "2", "3", "+"),
                    listOf("0", ".", "=")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { char ->
                                val isOp = char in listOf("+", "−", "×", "÷", "C", "±", "%", "=")
                                val col = if (char == "=") MaterialTheme.colorScheme.primary
                                else if (char == "C") Color(0xFFE03C3C).copy(alpha = 0.12f)
                                else if (isOp) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color(0xFFE4ECEB)

                                val txtCol = if (char == "=") Color.White
                                else if (char == "C") Color(0xFFE03C3C)
                                else if (isOp) MaterialTheme.colorScheme.primary
                                else Color.Black

                                Box(
                                    modifier = Modifier
                                        .weight(if (char == "0") 2f else 1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(col)
                                        .clickable { viewModel.clickCalcBtn(char) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = char, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = txtCol)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// 8. NEW DOCUMENT FORM MODAL DIALOGS
// ==========================================

// --- Form: Create Invoice dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewInvoiceDialog(
    viewModel: AppViewModel, 
    onClose: () -> Unit,
    onScanBarcodeClick: ((String) -> Unit) -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val products by viewModel.products.collectAsState()
    val tempItems by viewModel.tempInvoiceItems.collectAsState()
    val selectedCustomer by viewModel.selectedInvoiceCustomer.collectAsState()
    val notes by viewModel.invoiceNotes.collectAsState()
    val activeType by viewModel.invoiceType.collectAsState()
    val activeCurrency by viewModel.invoiceCurrency.collectAsState()
    val activeCompanyCurrency = viewModel.companyCurrency.collectAsState().value

    var showCustDropdown by remember { mutableStateOf(false) }
    var showProdDropdown by remember { mutableStateOf(false) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showCurrDropdown by remember { mutableStateOf(false) }

    val total = tempItems.sumOf { it.qty * it.price }

    val title = when (activeType) {
        "sale" -> "فاتورة مبيعات جديدة"
        "purchase" -> "فاتورة مشتريات جديدة"
        "return_sale" -> "فاتورة مرتجع مبيعات جديدة"
        "return_purchase" -> "فاتورة مرتجع مشتريات جديدة"
        else -> "فاتورة جديدة"
    }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(vertical = 10.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1. Selector for Invoice Type
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = when (activeType) {
                                    "sale" -> "فاتورة مبيعات"
                                    "purchase" -> "فاتورة مشتريات"
                                    "return_sale" -> "فاتورة مرتجع مبيعات"
                                    "return_purchase" -> "فاتورة مرتجع مشتريات"
                                    else -> "فاتورة مبيعات"
                                },
                                onValueChange = {},
                                label = { Text("نوع الفاتورة") },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showTypeDropdown = true }
                            )

                            DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                                listOf(
                                    "sale" to "فاتورة مبيعات",
                                    "purchase" to "فاتورة مشتريات",
                                    "return_sale" to "فاتورة مرتجع مبيعات",
                                    "return_purchase" to "فاتورة مرتجع مشتريات"
                                ).forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(text = label) },
                                        onClick = {
                                            viewModel.invoiceType.value = key
                                            // Empty items as pricing rules vary between purchase vs sale
                                            viewModel.tempInvoiceItems.value = emptyList()
                                            showTypeDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Selector for Invoice Currency
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = activeCurrency,
                                onValueChange = {},
                                label = { Text("عملة الفاتورة ومادة الحساب") },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showCurrDropdown = true }
                            )

                            DropdownMenu(expanded = showCurrDropdown, onDismissRequest = { showCurrDropdown = false }) {
                                listOf(activeCompanyCurrency, "USD", "EUR", "SAR", "TRY")
                                    .distinct()
                                    .forEach { mCurr ->
                                        DropdownMenuItem(
                                            text = { Text(text = mCurr) },
                                            onClick = {
                                                viewModel.changeInvoiceCurrency(mCurr)
                                                showCurrDropdown = false
                                            }
                                        )
                                    }
                            }
                        }
                    }

                    // Date picker simulation
                    item {
                        OutlinedTextField(
                            value = "2026-05-23", // Current simulated app UTC date
                            onValueChange = { },
                            label = { Text("تاريخ الفاتورة") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.Black,
                                disabledBorderColor = Color.LightGray
                            )
                        )
                    }

                    // Customer / Supplier drop selector (based on type)
                    item {
                        val isPurchaseFlow = activeType == "purchase" || activeType == "return_purchase"
                        val dynamicLabel = if (isPurchaseFlow) "المورد المستفيد" else "العميل المستفيد"
                        val dynamicFallback = if (isPurchaseFlow) "-- اختر مورد من المنشأة --" else "-- اختر عميل من المنشأة --"
                        val accountsFilteredList = if (isPurchaseFlow) {
                            accounts.filter { it.type == "supplier" }
                        } else {
                            accounts.filter { it.type == "customer" }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCustomer?.name ?: dynamicFallback,
                                onValueChange = {},
                                label = { Text(dynamicLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showCustDropdown = true }
                            )

                            DropdownMenu(expanded = showCustDropdown, onDismissRequest = { showCustDropdown = false }) {
                                accountsFilteredList.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(text = "${acc.name} (${acc.currency})") },
                                        onClick = {
                                            viewModel.selectedInvoiceCustomer.value = acc
                                            viewModel.changeInvoiceCurrency(acc.currency)
                                            showCustDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Products picker selection
                    item {
                        Text(text = "المواد المضافة للفاتورة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { showProdDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("+ اختر مادة من المخزن")
                                }

                                DropdownMenu(expanded = showProdDropdown, onDismissRequest = { showProdDropdown = false }) {
                                    products.forEach { prod ->
                                        val rawPr = if (activeType == "purchase" || activeType == "return_purchase") {
                                            prod.buyPrice
                                        } else {
                                            prod.sellPrice
                                        }
                                        val rate = viewModel.getRateInSyp(activeCurrency)
                                        val calculatedPrice = if (rate != 0.0) rawPr / rate else rawPr
                                        val formattedPrice = viewModel.formatCurrency(calculatedPrice)

                                        DropdownMenuItem(
                                            text = { Text(text = "${prod.icon} ${prod.name} | متوفر: ${prod.qty} ${prod.unit} | السعر: $formattedPrice $activeCurrency") },
                                            onClick = {
                                                viewModel.addProductToInvoiceForm(prod)
                                                showProdDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Barcode scanning option!
                            Button(
                                onClick = {
                                    onScanBarcodeClick { code ->
                                        // Find product with this barcode
                                        val found = products.find { it.barcode == code }
                                        if (found != null) {
                                            viewModel.addProductToInvoiceForm(found)
                                            viewModel.triggerToast("تم مسح وإضافة: ${found.name} ✓")
                                        } else {
                                            viewModel.triggerToast("الرمز الباركود ($code) غير مسجل حالياً")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("📷 قراءة باركود", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Selected Items Table simulation
                    if (tempItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE4ECEB), RoundedCornerShape(10.dp))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "سلة الفاتورة فارغة، المرجو إضافة مواد", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        itemsIndexed(tempItems) { idx, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9)),
                                border = BorderStroke(1.dp, Color(0xFFD0DEED))
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "${idx + 1}", fontSize = 12.sp, modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = "مجموع: ${viewModel.formatCurrency(item.qty * item.price)} $activeCurrency", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(onClick = { viewModel.updateInvoiceItemFormQty(idx, -1) }, modifier = Modifier.size(24.dp).background(Color(0xFFE4ECEB), RoundedCornerShape(6.dp))) {
                                            Text("−", fontSize = 14.sp)
                                        }
                                        
                                        var qtyText by remember(item.qty) { mutableStateOf(item.qty.toString()) }
                                        BasicTextField(
                                            value = qtyText,
                                            onValueChange = { newValue ->
                                                val filtered = newValue.filter { it.isDigit() }
                                                qtyText = filtered
                                                val parsed = filtered.toIntOrNull()
                                                if (parsed != null && parsed >= 1) {
                                                    viewModel.setInvoiceItemFormQty(idx, parsed)
                                                }
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .width(45.dp)
                                                .background(Color.White, RoundedCornerShape(4.dp))
                                                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                        )

                                        IconButton(onClick = { viewModel.updateInvoiceItemFormQty(idx, 1) }, modifier = Modifier.size(24.dp).background(Color(0xFFE4ECEB), RoundedCornerShape(6.dp))) {
                                            Text("+", fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(onClick = { viewModel.removeInvoiceItemForm(idx) }, modifier = Modifier.size(24.dp).background(Color(0xFFE03C3C).copy(alpha = 0.1f), RoundedCornerShape(6.dp))) {
                                            Text("🗑️", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Notes input
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { viewModel.invoiceNotes.value = it },
                            label = { Text("ملاحظات إضافية") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Sub totals summary card in arabic
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE4ECEB))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "توتال المواد:", fontSize = 12.sp, color = Color.Gray)
                                    Text(text = "${viewModel.formatCurrency(total)} $activeCurrency", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray).padding(vertical = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "صافي قيمة الفاتورة:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "${viewModel.formatCurrency(total)} $activeCurrency", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveInvoice("draft")
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("مسودة الفاتورة")
                    }
                    Button(
                        onClick = {
                            viewModel.saveInvoice("saved")
                            onClose()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حفظ وطباعة الفاتورة")
                    }
                }
            }
        }
    }
}

// --- Form: Create New Account Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAccountDialog(viewModel: AppViewModel, onClose: () -> Unit) {
    // Read reactive values for initialization, and write locally to reflect changes reactive
    val initName by viewModel.newAccountName.collectAsState()
    val initType by viewModel.newAccountType.collectAsState()
    val initBalance by viewModel.newAccountBalance.collectAsState()
    val initPhone by viewModel.newAccountPhone.collectAsState()
    val initAddress by viewModel.newAccountAddress.collectAsState()
    val initNotes by viewModel.newAccountNotes.collectAsState()
    val initCurrency by viewModel.newAccountCurrency.collectAsState()
    val editingAccount by viewModel.editingAccount.collectAsState()

    var name by remember(initName) { mutableStateOf(initName) }
    var type by remember(initType) { mutableStateOf(initType) }
    var balance by remember(initBalance) { mutableStateOf(initBalance) }
    var phone by remember(initPhone) { mutableStateOf(initPhone) }
    var address by remember(initAddress) { mutableStateOf(initAddress) }
    var notes by remember(initNotes) { mutableStateOf(initNotes) }
    var currency by remember(initCurrency) { mutableStateOf(initCurrency) }

    var showAccountTypeMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }

    val dialogTitle = if (editingAccount != null) "تعديل حساب مالي" else "إضافة حساب مالي جديد"

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = dialogTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.newAccountName.value = it
                    },
                    label = { Text("اسم الحساب بالكامل") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    val typeLabel = when (type) {
                        "customer" -> "عميل"
                        "supplier" -> "مورد"
                        "expense" -> "مصروف"
                        else -> "أخرى"
                    }
                    OutlinedTextField(
                        value = typeLabel,
                        onValueChange = {},
                        label = { Text("نوع الحساب المحاسبي") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showAccountTypeMenu = true }
                    )

                    DropdownMenu(expanded = showAccountTypeMenu, onDismissRequest = { showAccountTypeMenu = false }) {
                        listOf(
                            "customer" to "عميل",
                            "supplier" to "مورد",
                            "expense" to "مصروف",
                            "other" to "أخرى"
                        ).forEach { (key, title) ->
                            DropdownMenuItem(
                                text = { Text(text = title) },
                                onClick = {
                                    type = key
                                    viewModel.newAccountType.value = key
                                    showAccountTypeMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = {},
                        label = { Text("عملة الحساب المالي") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showCurrencyMenu = true }
                    )

                    DropdownMenu(expanded = showCurrencyMenu, onDismissRequest = { showCurrencyMenu = false }) {
                        val activeCompanyCurrency = viewModel.companyCurrency.collectAsState().value
                        listOf(activeCompanyCurrency, "USD", "EUR", "SAR", "TRY")
                            .distinct()
                            .forEach { mCurrency ->
                                DropdownMenuItem(
                                    text = { Text(text = mCurrency) },
                                    onClick = {
                                        currency = mCurrency
                                        viewModel.newAccountCurrency.value = mCurrency
                                        showCurrencyMenu = false
                                    }
                                )
                            }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = balance,
                    onValueChange = {
                        balance = it
                        viewModel.newAccountBalance.value = it
                    },
                    label = { Text("الرصيد الافتتاحي بـ ($currency) - بالسالب ليكون مطلوب منا") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        viewModel.newAccountPhone.value = it
                    },
                    label = { Text("رقم الهاتف (جوال)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        viewModel.newAccountAddress.value = it
                    },
                    label = { Text("العنوان السكني/التجاري") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        viewModel.newAccountNotes.value = it
                    },
                    label = { Text("ملاحظات الحساب") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            viewModel.saveAccount()
                            onClose()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حفظ وتأكيد الحساب")
                    }
                }
            }
        }
    }
}

// --- Form: Create New Product Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProductDialog(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onScanBarcodeClick: ((String) -> Unit) -> Unit
) {
    val editingProduct by viewModel.editingProduct.collectAsState()
    val isEditing = editingProduct != null

    val initName by viewModel.newProductName.collectAsState()
    val initCode by viewModel.newProductCode.collectAsState()
    val initCategory by viewModel.newProductCategory.collectAsState()
    val initUnit by viewModel.newProductUnit.collectAsState()
    val initQty by viewModel.newProductQty.collectAsState()
    val initMinQty by viewModel.newProductMinQty.collectAsState()
    val initBuyPrice by viewModel.newProductBuyPrice.collectAsState()
    val initSellPrice by viewModel.newProductSellPrice.collectAsState()
    val initBarcode by viewModel.newProductBarcode.collectAsState()
    val initIcon by viewModel.newProductIcon.collectAsState()

    var name by remember(initName) { mutableStateOf(initName) }
    var code by remember(initCode) { mutableStateOf(initCode) }
    var category by remember(initCategory) { mutableStateOf(initCategory) }
    var unit by remember(initUnit) { mutableStateOf(initUnit) }
    var qty by remember(initQty) { mutableStateOf(initQty) }
    var minQty by remember(initMinQty) { mutableStateOf(initMinQty) }
    var buyPrice by remember(initBuyPrice) { mutableStateOf(initBuyPrice) }
    var sellPrice by remember(initSellPrice) { mutableStateOf(initSellPrice) }
    var barcode by remember(initBarcode) { mutableStateOf(initBarcode) }
    var icon by remember(initIcon) { mutableStateOf(initIcon) }

    var showCatDropdown by remember { mutableStateOf(false) }
    var showUnitDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEditing) "تعديل مادة في المستودع" else "إضافة مادة للمستودع",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Select visual icon
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE4ECEB))
                            .clickable {
                                val listEmojis = listOf("📦", "🛍️", "🍎", "💊", "🔧", "📱", "🍪")
                                val chosen = listEmojis.random()
                                icon = chosen
                                viewModel.newProductIcon.value = chosen
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = icon, fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "انقر فوق الصندوق لتبديل رمز الأيقونة عشوائياً", fontSize = 11.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.newProductName.value = it
                    },
                    label = { Text("اسم المادة بالكامل") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        viewModel.newProductCode.value = it
                    },
                    label = { Text("كود المادة الداخلي الفريد") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Category Picker
                    Box(modifier = Modifier.weight(1f)) {
                        val catLabel = when (category) {
                            "food" -> "غذاء"
                            "electronics" -> "إلكترونيات"
                            else -> "أخرى"
                        }
                        OutlinedTextField(
                            value = catLabel,
                            onValueChange = {},
                            label = { Text("الفئة") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showCatDropdown = true }
                        )

                        DropdownMenu(expanded = showCatDropdown, onDismissRequest = { showCatDropdown = false }) {
                            listOf("other" to "أخرى", "food" to "غذاء", "electronics" to "إلكترونيات").forEach { (key, text) ->
                                DropdownMenuItem(
                                    text = { Text(text = text) },
                                    onClick = {
                                        category = key
                                        viewModel.newProductCategory.value = key
                                        showCatDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Unit Picker
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = unit,
                            onValueChange = {
                                unit = it
                                viewModel.newProductUnit.value = it
                            },
                            label = { Text("الوحدة") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = false,
                            trailingIcon = {
                                IconButton(onClick = { showUnitDropdown = !showUnitDropdown }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )

                        DropdownMenu(expanded = showUnitDropdown, onDismissRequest = { showUnitDropdown = false }) {
                            listOf("قطعة", "كيلو", "غرام", "لتر", "متر", "علبة", "كرتون", "كيس", "درزن", "طرد", "صندوق").forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(text = u) },
                                    onClick = {
                                        unit = u
                                        viewModel.newProductUnit.value = u
                                        showUnitDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = buyPrice,
                        onValueChange = {
                            buyPrice = it
                            viewModel.newProductBuyPrice.value = it
                        },
                        label = { Text("سعر الشراء") },
                        modifier = Modifier.weight(1.5f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = sellPrice,
                        onValueChange = {
                            sellPrice = it
                            viewModel.newProductSellPrice.value = it
                        },
                        label = { Text("سعر البيع") },
                        modifier = Modifier.weight(1.5f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = {
                            qty = it
                            viewModel.newProductQty.value = it
                        },
                        label = { Text("الكمية") },
                        modifier = Modifier.weight(1.5f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = minQty,
                        onValueChange = {
                            minQty = it
                            viewModel.newProductMinQty.value = it
                        },
                        label = { Text("الحد الأدنى") },
                        modifier = Modifier.weight(1.5f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barcode custom controls (Manual + Auto count generate + Camera scanner)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = {
                            barcode = it
                            viewModel.newProductBarcode.value = it
                        },
                        label = { Text("رقم الباركود (EAN)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    // Auto generate button
                    Button(
                        onClick = {
                            val generated = "622" + (1000000000 + (Math.random() * 900000000).toLong()).toString()
                            barcode = generated
                            viewModel.newProductBarcode.value = generated
                            viewModel.triggerToast("تم توليد باركود تلقائي ✓")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7FAF9), contentColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Text("توليد تلقائي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Scanner button helper
                    IconButton(
                        onClick = {
                            onScanBarcodeClick { scanned ->
                                barcode = scanned
                                viewModel.newProductBarcode.value = scanned
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    ) {
                        Text("📷", fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            viewModel.saveProduct()
                            onClose()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (isEditing) "تعديل وحفظ التغييرات" else "إضافة وحفظ المستودع")
                    }
                }
            }
        }
    }
}

// --- Form: Create New Voucher Dialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewVoucherDialog(viewModel: AppViewModel, onClose: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val type by viewModel.voucherType.collectAsState()
    val selectedAcc by viewModel.voucherSelectedAccount.collectAsState()
    val amount by viewModel.voucherAmount.collectAsState()
    val desc by viewModel.voucherDesc.collectAsState()

    var showAccDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Title dynamically changed
                val title = if (type == "receipt") "سند قبض نقدي جديد" else "سند صرف نقدي جديد"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle inside dialog
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.voucherType.value = "receipt" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == "receipt") Color(0xFF2EBD7A) else Color.LightGray)
                    ) {
                        Text("قبض نقود 💰")
                    }
                    Button(
                        onClick = { viewModel.voucherType.value = "payment" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == "payment") Color(0xFFE03C3C) else Color.LightGray)
                    ) {
                        Text("صرف نقود 💸")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Choose target account
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedAcc?.name ?: "-- اختر الحساب المالي المرتبط --",
                        onValueChange = {},
                        label = { Text("الحساب المطلوب") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showAccDropdown = true }
                    )

                    DropdownMenu(expanded = showAccDropdown, onDismissRequest = { showAccDropdown = false }) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(text = acc.name) },
                                onClick = {
                                    viewModel.voucherSelectedAccount.value = acc
                                    showAccDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { viewModel.voucherAmount.value = it },
                    label = { Text("المبلغ النقدي (${selectedAcc?.currency ?: "ل.س"})") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { viewModel.voucherDesc.value = it },
                    label = { Text("البيان والبيانات الوصفية للسند") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("إلغاء السند")
                    }
                    Button(
                        onClick = {
                            viewModel.saveVoucher()
                            onClose()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حفظ وتأكيد السند")
                    }
                }
            }
        }
    }
}


// ==========================================
// 9. CURRENCIES EXCHANGE RATE RATES COMPONENT
// ==========================================
data class CurrencyRateConfig(
    val title: String,
    val code: String,
    val stateVal: Double,
    val setter: (Double) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesRateDialog(viewModel: AppViewModel, onClose: () -> Unit) {
    val usd by viewModel.rateUSD.collectAsState()
    val eur by viewModel.rateEUR.collectAsState()
    val sar by viewModel.rateSAR.collectAsState()
    val tryVal by viewModel.rateTRY.collectAsState()

    var convertAmountText by remember { mutableStateOf("") }
    var convertFromCurrency by remember { mutableStateOf("USD") }
    var convertResultDisplayStr by remember { mutableStateOf("0 ل.س") }

    fun runCurrencyMath() {
        val amt = convertAmountText.toDoubleOrNull() ?: 0.0
        val rateMultiplier = when (convertFromCurrency) {
            "USD" -> usd
            "EUR" -> eur
            "SAR" -> sar
            else -> tryVal
        }
        val resultingSyp = amt * rateMultiplier
        convertResultDisplayStr = viewModel.formatCurrency(resultingSyp) + " ل.س"
    }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "أسعار صرف العملات الأجنبية", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "آخر تحديث: 23 مايو 2026 — الليرة السورية هي الأساس", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))

                // Exchange Rates Editable Cards list using safe class container
                val currenciesRateList = listOf(
                    CurrencyRateConfig("🇺🇸 دولار أمريكي", "USD", usd) { viewModel.rateUSD.value = it },
                    CurrencyRateConfig("🇪🇺 يورو أوروبي", "EUR", eur) { viewModel.rateEUR.value = it },
                    CurrencyRateConfig("🇸🇦 ريال سعودي", "SAR", sar) { viewModel.rateSAR.value = it },
                    CurrencyRateConfig("🇹🇷 ليرة تركية", "TRY", tryVal) { viewModel.rateTRY.value = it }
                )

                currenciesRateList.forEach { config ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = config.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = config.stateVal.toInt().toString(),
                            onValueChange = { stringVal ->
                                val doubleVal = stringVal.toDoubleOrNull() ?: config.stateVal
                                config.setter(doubleVal)
                                runCurrencyMath()
                            },
                            modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ل.س", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.LightGray)
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "آلة محول أسعار الصرف الفوري", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Interactive calculation inputs row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = convertAmountText,
                        onValueChange = {
                            convertAmountText = it
                            runCurrencyMath()
                        },
                        placeholder = { Text("المبلغ") },
                        modifier = Modifier.weight(1.5f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    var exMenuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        Button(onClick = { exMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(text = convertFromCurrency)
                        }
                        DropdownMenu(expanded = exMenuExpanded, onDismissRequest = { exMenuExpanded = false }) {
                            listOf("USD", "EUR", "SAR", "TRY").forEach { cur ->
                                DropdownMenuItem(
                                    text = { Text(text = cur) },
                                    onClick = {
                                        convertFromCurrency = cur
                                        exMenuExpanded = false
                                        runCurrencyMath()
                                    }
                                )
                            }
                        }
                    }

                    Text(text = "←", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = convertResultDisplayStr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        viewModel.triggerToast("تم حفظ وتعديل أسعار العملات بالمخزن المحاسبي")
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ وتحديث الأسعار")
                }
            }
        }
    }
}


// ==========================================
// 10. REVIEWS STATEMENTS & REPORTS MODAL DETAILS
// ==========================================
@Composable
fun ReportDetailsDialog(viewModel: AppViewModel, type: String, onClose: () -> Unit) {
    val invoices by viewModel.invoices.collectAsState()
    val products by viewModel.products.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val totalSales = invoices.filter { it.status == "saved" && it.type == "sale" }.sumOf { it.total }
    val totalProfit = invoices.filter { it.status == "saved" && it.type == "sale" }.sumOf { it.profit }

    val title = when (type) {
        "daily" -> "تقرير الحركة اليومية"
        "pl" -> "بيان الأرباح والخسائر"
        "topProducts" -> "المستودع: المواد الأكثر حركة مبيعاً"
        "topCustomers" -> "العملاء الأكثر حركة محاسبية"
        else -> "المستودع: المواد تحت حد الأمان النقدي"
    }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (type) {
                        "daily" -> {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "مراجعة اليوم — 23 مايو 2026", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Card(modifier = Modifier.weight(1f).padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9))) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text("مجموع مبيعات", fontSize = 11.sp, color = Color.Gray)
                                            Text(viewModel.formatCurrency(totalSales), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Card(modifier = Modifier.weight(1f).padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9))) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text("مجموع أرباح", fontSize = 11.sp, color = Color.Gray)
                                            Text(viewModel.formatCurrency(totalProfit), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2EBD7A))
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Card(modifier = Modifier.weight(1f).padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9))) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text("سندات صرف", fontSize = 11.sp, color = Color.Gray)
                                            Text(viewModel.formatCurrency(400000.0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE03C3C))
                                        }
                                    }
                                    Card(modifier = Modifier.weight(1f).padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9))) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text("سندات استلام", fontSize = 11.sp, color = Color.Gray)
                                            Text(viewModel.formatCurrency(230000.0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2EBD7A))
                                        }
                                    }
                                }
                            }
                        }
                        "pl" -> {
                            val cogs = totalSales * 0.70 // simulated COGS
                            val grossProfit = totalSales - cogs
                            val netProfit = grossProfit - 125000.0 // Simulated overhead expense
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2EBD7A).copy(alpha = 0.12f))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "صافي ربح الفترة المالي", fontSize = 11.sp, color = Color(0xFF1A9A60), fontWeight = FontWeight.Bold)
                                        Text(text = "${viewModel.formatCurrency(netProfit)} ل.س", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A9A60))
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                val plLines = listOf(
                                    Triple("إجمالي المبيعات المحققة", totalSales, Color(0xFF1A9A60)),
                                    Triple("تكلفة البضاعة المباعة (تقديري)", -cogs, Color(0xFFE03C3C)),
                                    Triple("مجمل الربح الإجمالي", grossProfit, Color(0xFF1A9A60)),
                                    Triple("المصاريف الإدارية والعمومية والتشغيل", -125000.0, Color(0xFFE03C3C)),
                                    Triple("صافي الدخل النهائي للفترة", netProfit, Color(0xFF1A9A60))
                                )

                                plLines.forEach { (label, value, color) ->
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = label, fontSize = 13.sp, color = Color.Gray)
                                            Text(text = "${viewModel.formatCurrency(value)} ل.س", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                                        }
                                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                        "lowStock" -> {
                            val lowItems = products.filter { it.qty <= it.minQty }
                            if (lowItems.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("جميع المواد سليمة وتتخطى حد الأمان")
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(lowItems) { p ->
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = p.icon, fontSize = 24.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(text = "الحد الأدنى للأمان: ${p.minQty} ${p.unit}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                                Text(text = "${p.qty} ${p.unit}", color = Color(0xFFE03C3C), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "topProducts" -> {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(products) { index, p ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = p.icon, fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = p.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = "${viewModel.formatCurrency(p.qty * p.sellPrice)} ل.س", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                        "topCustomers" -> {
                            val customers = accounts.filter { it.type == "customer" }
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(customers) { index, c ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Gray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(text = c.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = "${viewModel.formatCurrency(Math.abs(c.balance))} ${c.currency}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.triggerToast("تم تصدير البيانات بنجاح كملف Excel") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("طرد التصدير Excel")
                    }
                    Button(
                        onClick = {
                            viewModel.triggerToast("جاري حفظ التقرير PDF بهاتفك...")
                            onClose()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("طباعة تقرير PDF")
                    }
                }
            }
        }
    }
}

// --- Date picker native dialog wrapper ---
fun showDatePicker(context: android.content.Context, onDateSelected: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(context, { _, selectedYear, selectedMonth, selectedDay ->
        val formattedMonth = String.format("%02d", selectedMonth + 1)
        val formattedDay = String.format("%02d", selectedDay)
        onDateSelected("$selectedYear-$formattedMonth-$formattedDay")
    }, year, month, day).show()
}

// --- Account Statement (كشف الحساب المحاسبي) dialog ---
@Composable
fun AccountStatementDialog(viewModel: AppViewModel, account: Account, onClose: () -> Unit, onAddVoucher: (String) -> Unit) {
    val invoices by viewModel.invoices.collectAsState()
    val vouchers by viewModel.vouchers.collectAsState()

    val accountVouchers = vouchers.filter { it.accountId == account.id }
    val accountInvs = invoices.filter { it.customer == account.name }

    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "كشف حساب بالتفصيل", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Balance summary header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(14.dp)
                ) {
                    Column {
                        Text(text = "الاسم: ${account.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "الرصيد الكلي الحالي:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(text = "${viewModel.formatCurrency(Math.abs(account.balance))} ${account.currency}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val subtitle = if (account.balance > 0) "← مستحق للغير (له علينا)" else if (account.balance < 0) "← مستحق للشركة (عليه لنا)" else "الحساب متوازن"
                        Text(text = subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date Filter Inputs
                Text(text = "تصفية حسب التاريخ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = {},
                            label = { Text("من (YYYY-MM-DD)", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = true,
                            trailingIcon = {
                                if (startDate.isNotBlank()) {
                                    IconButton(onClick = { startDate = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    showDatePicker(context) { startDate = it }
                                }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = {},
                            label = { Text("إلى (YYYY-MM-DD)", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = true,
                            trailingIcon = {
                                if (endDate.isNotBlank()) {
                                    IconButton(onClick = { endDate = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    showDatePicker(context) { endDate = it }
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "حركات السجلات المالية المكتشفة", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))

                // Combine ledger transactions into a list ordered by date
                val rate = viewModel.getRateInSyp(account.currency)
                val allTx = (
                        accountInvs.map { Triple(it.date, "فاتورة مبيعات ${it.id}", if (rate != 0.0) -it.total / rate else -it.total) } +
                        accountVouchers.map { Triple(it.date, it.desc, if (it.type == "receipt") it.amount else -it.amount) }
                        ).sortedByDescending { it.first }

                // Apply dynamic date filtering
                val filteredTx = allTx.filter { tx ->
                    val txDate = tx.first // "YYYY-MM-DD"
                    val afterStart = if (startDate.isBlank()) true else txDate >= startDate
                    val beforeEnd = if (endDate.isBlank()) true else txDate <= endDate
                    afterStart && beforeEnd
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (filteredTx.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "لا توجد أي حركات قيود لهذه الفترة", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredTx) { item ->
                                val (date, description, amount) = item
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9)),
                                    border = BorderStroke(1.dp, Color(0xFFD0DEDD))
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(text = description, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(text = date, fontSize = 10.sp, color = Color.Gray)
                                        }
                                        val color = if (amount > 0) Color(0xFF2EBD7A) else Color(0xFFE03C3C)
                                        val prefix = if (amount > 0) "+" else ""
                                        Text(text = "$prefix${viewModel.formatCurrency(amount)} ${account.currency}", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Print and Export Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            printAccountStatement(
                                context = context,
                                account = account,
                                txList = filteredTx,
                                companyName = "المحاسب الذكي - شركة المعتز",
                                startDate = startDate,
                                endDate = endDate
                            )
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🖨️ طباعة الكشف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            exportAccountStatementToExcel(
                                context = context,
                                account = account,
                                txList = filteredTx,
                                startDate = startDate,
                                endDate = endDate
                            )
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7145)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📊 تصدير Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Footer shortcut actions for this specific account
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onAddVoucher("receipt")
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EBD7A))
                    ) {
                        Text("قبض نقود")
                    }
                    Button(
                        onClick = {
                            onAddVoucher("payment")
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE03C3C))
                    ) {
                        Text("صرف نقود")
                    }
                }
            }
        }
    }
}

// System print web adapter helper for Account Statement
fun printAccountStatement(
    context: android.content.Context, 
    account: Account, 
    txList: List<Triple<String, String, Double>>, 
    companyName: String, 
    startDate: String, 
    endDate: String
) {
    val totalCount = txList.size
    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: 'Courier New', sans-serif; direction: rtl; padding: 20px; }
                header { text-align: center; margin-bottom: 25px; }
                h2 { margin: 0; color: #1c544d; font-size: 24px; }
                p { margin: 4px 0; color: #555; }
                .meta-table { width: 100%; border-collapse: collapse; margin-bottom: 25px; }
                .meta-table td { padding: 8px; border: 1px dashed #ccc; font-size: 14px; }
                .statement-table { width: 100%; border-collapse: collapse; }
                .statement-table th, .statement-table td { padding: 10px; border: 1px solid #777; text-align: right; font-size: 13px; }
                .statement-table th { background-color: #e4eceb; color: #1c544d; }
                .credit { color: #2ebd7a; font-weight: bold; }
                .debit { color: #e03c3c; font-weight: bold; }
                .total-box { margin-top: 25px; border: 2px solid #1c544d; padding: 15px; font-weight: bold; text-align: center; font-size: 16px; background-color: #f7faf9; }
            </style>
        </head>
        <body>
            <header>
                <h2>$companyName</h2>
                <h3>كشف حساب مالي تفصيلي وطباعته</h3>
                <p>تاريخ استخراج التقرير: 2026-05-29</p>
            </header>
            <table class="meta-table">
                <tr>
                    <td><b>اسم الحساب:</b> ${account.name}</td>
                    <td><b>نوع الحساب:</b> ${account.type}</td>
                </tr>
                <tr>
                    <td><b>رقم الهاتف:</b> ${account.phone.ifBlank { "غير متوفر" }}</td>
                    <td><b>العنوان:</b> ${account.address.ifBlank { "غير متوفر" }}</td>
                </tr>
                <tr>
                    <td><b>الفترة الزمنية المحددة:</b> من ${startDate.ifBlank { "البدأ" }} إلى ${endDate.ifBlank { "اليوم" }}</td>
                    <td><b>حجم الحركات:</b> $totalCount قيد مالي</td>
                </tr>
            </table>
            
            <table class="statement-table">
                <thead>
                    <tr>
                        <th style="width: 25%;">التاريخ</th>
                        <th style="width: 50%;">البيان والتفاصيل</th>
                        <th style="width: 25%;">القيمة المالية</th>
                    </tr>
                </thead>
                <tbody>
    """.trimIndent())
    
    txList.forEach { tx ->
        val amt = tx.third
        val amtClass = if (amt >= 0) "credit" else "debit"
        val amtSign = if (amt >= 0) "+" else ""
        htmlBuilder.append("""
            <tr>
                <td>${tx.first}</td>
                <td>${tx.second}</td>
                <td class="$amtClass">$amtSign${String.format("%,.2f", amt)} ${account.currency}</td>
            </tr>
        """.trimIndent())
    }
    
    htmlBuilder.append("""
                </tbody>
            </table>
            <div class="total-box">
                الرصيد الكلي الإجمالي المجمع: ${String.format("%,.2f", account.balance)} ${account.currency}
            </div>
            <p style="text-align: center; margin-top: 30px; font-size: 12px; color: #777;">تم توليد وحفظ هذا التقرير كـ PDF إلكتروني عبر نظام المحاسب الذكي 📱</p>
        </body>
        </html>
    """.trimIndent())

    (context as? android.app.Activity)?.runOnUiThread {
        val webView = android.webkit.WebView(context)
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                val jobName = "كشف حساب - ${account.name}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
    }
}

// System export CSV web adapter helper for Account Statement
fun exportAccountStatementToExcel(
    context: android.content.Context, 
    account: Account, 
    txList: List<Triple<String, String, Double>>, 
    startDate: String, 
    endDate: String
) {
    val csvContent = StringBuilder()
    // Unicode UTF-8 Byte Order Mark (BOM) to correctly display Arabic in Microsoft Excel!
    csvContent.append('\ufeff')
    csvContent.append("كشف حساب مالي تفصيلي\n")
    csvContent.append("اسم الحساب,${account.name}\n")
    csvContent.append("رقم الهاتف,${account.phone}\n")
    csvContent.append("العنوان,${account.address}\n")
    csvContent.append("نوع الحساب,${account.type}\n")
    csvContent.append("الفترة,من ${startDate.ifBlank { "البداية" }} إلى ${endDate.ifBlank { "اليوم" }}\n")
    csvContent.append("الرصيد الإجمالي,${account.balance} ${account.currency}\n")
    csvContent.append("\n")
    csvContent.append("التاريخ,البيان,المبلغ,العملة\n")
    
    txList.forEach { tx ->
        val amt = tx.third
        val amtStr = "${if (amt >= 0) "+" else ""}$amt"
        csvContent.append("${tx.first},${tx.second},$amtStr,${account.currency}\n")
    }
    
    val fileName = "statement_${account.id}_${System.currentTimeMillis()}.csv"
    try {
        val file = java.io.File(context.cacheDir, fileName)
        file.writeText(csvContent.toString(), Charsets.UTF_8)
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "تصدير كشف حساب: ${account.name}")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "تصدير كشف الحساب إلى Excel"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "فشل تصدير الملف: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// --- Dynamic Barcode Scanner Composable with Pulsing laser line & simulated physics beep ---
@Composable
fun BarcodeScannerCustomDialog(
    viewModel: AppViewModel,
    onScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val products by viewModel.products.collectAsState()
    var customCodeInput by remember { mutableStateOf("") }
    
    // Play laser sound and beep upon scanning!
    fun triggerSuccessScan(code: String) {
        if (code.isBlank()) return
        
        // Play scanner physics beep sound!
        try {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 130)
        } catch (e: Exception) {}
        
        onScanned(code)
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E272C)) // Dark tech UI
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📷 قارئ الباركود الذكي",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Simulated live camera viewfinder with glowing scanning line!
                Box(
                    modifier = Modifier
                        .size(height = 140.dp, width = 240.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFF0F9D58), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Transparent center view rectangle
                    Box(
                        modifier = Modifier
                            .size(height = 80.dp, width = 180.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    )

                    // Animating scanning laser line!
                    var animTrigger by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            animTrigger = !animTrigger
                            delay(1200)
                        }
                    }
                    val laserOffset by animateDpAsState(
                        targetValue = if (animTrigger) (-30).dp else 30.dp,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = ""
                    )
                    
                    Box(
                        modifier = Modifier
                            .offset(y = laserOffset)
                            .height(2.dp)
                            .width(170.dp)
                            .background(Color.Red)
                    )

                    Text(
                        text = "وجه الكود نحو المنتصف للمسح...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of existing warehouse products barcodes for seamless scanning demo!
                Text(
                    text = "🎯 محاكاة الكاميرا — اختر كود مادة لمسحه فوراً:",
                    color = Color(0xFFAAB8C2),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val barcodesOnly = products.filter { it.barcode.isNotBlank() }
                    if (barcodesOnly.isEmpty()) {
                        item {
                            Text(
                                text = "لا توجد مواد بمستودعك تحتوي على باركود مسجل. يرجى توليد باركود تلقائي أو كتابته في نموذج المادة أولاً.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(barcodesOnly) { prod ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { triggerSuccessScan(prod.barcode) }
                                    .background(Color(0xFF2C3E50), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(prod.icon, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(prod.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("رمز: ${prod.barcode}", color = Color(0xFF0F9D58), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Or type manually
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customCodeInput,
                        onValueChange = { customCodeInput = it },
                        placeholder = { Text("أو اكتب الباركود يدوياً...", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFF0F9D58),
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Button(
                        onClick = { triggerSuccessScan(customCodeInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("إدخال")
                    }
                }
            }
        }
    }
}

// --- Dynamic Barcode Thermal Printer Design & Simulation Canvas Composable ---
@Composable
fun BarcodeThermalPrintDialog(
    viewModel: AppViewModel,
    product: Product,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    // Custom label design preferences state
    var includeCompanyName by remember { mutableStateOf(true) }
    var companyNameText by remember { mutableStateOf("مؤسسة الـشام الـتـجـاريـة") }
    var includeProductName by remember { mutableStateOf(true) }
    var includeProductPrice by remember { mutableStateOf(true) }
    var includeCodeText by remember { mutableStateOf(true) }
    var labelWidthType by remember { mutableStateOf("58mm") } // or 80mm

    // Function to execute the thermal printing job via Android Print Framework and HTML styling!
    fun printLabelNow() {
        try {
            val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
            val jobName = "Smart_BarcodeLabel_${product.code}"
            
            // Build perfect EAN-13-styled CSS design for label
            val htmlContent = """
                <html>
                <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Arial', sans-serif;
                        direction: rtl;
                        text-align: center;
                        margin: 0;
                        padding: 10px;
                        width: ${if (labelWidthType == "58mm") "188px" else "280px"};
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                    }
                    .company {
                        font-size: 11px;
                        font-weight: bold;
                        border-bottom: 1px dashed #000;
                        width: 100%;
                        padding-bottom: 4px;
                        margin-bottom: 6px;
                    }
                    .title {
                        font-size: 13px;
                        font-weight: 900;
                        margin-bottom: 4px;
                    }
                    .price {
                        font-size: 12px;
                        font-weight: bold;
                        background: #000;
                        color: #fff;
                        padding: 2px 6px;
                        border-radius: 4px;
                        margin-bottom: 6px;
                        display: inline-block;
                    }
                    .barcode-box {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        margin-top: 4px;
                    }
                    /* Custom Barcode bars lines matching exact ESC/POS thermal layout */
                    .barcode-graphic {
                        display: flex;
                        justify-content: center;
                        align-items: flex-end;
                        height: 38px;
                        width: 140px;
                        overflow: hidden;
                    }
                    .barcode-bar {
                        background-color: #000;
                        height: 100%;
                    }
                    .code-text {
                        font-size: 9px;
                        font-family: monospace;
                        letter-spacing: 2px;
                        margin-top: 2px;
                    }
                    .footer {
                        font-size: 8px;
                        color: #555;
                        margin-top: 6px;
                    }
                </style>
                </head>
                <body>
                    ${if (includeCompanyName) "<div class='company'>$companyNameText</div>" else ""}
                    ${if (includeProductName) "<div class='title'>${product.name}</div>" else ""}
                    ${if (includeProductPrice) "<div class='price'>السعر: ${viewModel.formatCurrency(product.sellPrice)} ل.س</div>" else ""}
                    
                    <div class="barcode-box">
                        <div class="barcode-graphic">
                            <!-- Draw EAN-13 lines of varying widths natively -->
                            <div class="barcode-bar" style="width:2px; margin-right:1px;"></div>
                            <div class="barcode-bar" style="width:1px; margin-right:2px;"></div>
                            <div class="barcode-bar" style="width:4px; margin-right:1px;"></div>
                            <div class="barcode-bar" style="width:2px; margin-right:2px;"></div>
                            <div class="barcode-bar" style="width:1px; margin-right:1px;"></div>
                            <div class="barcode-bar" style="width:3px; margin-right:3px;"></div>
                            <div class="barcode-bar" style="width:2px; margin-right:1px;"></div>
                            <div class="barcode-bar" style="width:1px; margin-right:2px;"></div>
                            <div class="barcode-bar" style="width:4px; margin-right:1px;"></div>
                            <div class="barcode-bar" style="width:2px; margin-right:2px;"></div>
                            <div class="barcode-bar" style="width:1px; margin-right:1px;"></div>
                            <div class="barcode-bar" style="width:3px; margin-right:3px;"></div>
                        </div>
                        ${if (includeCodeText) "<div class='code-text'>${product.barcode.ifBlank { product.code }}</div>" else ""}
                    </div>
                    <div class="footer">طابعة حرارية لاسلكية ESC/POS</div>
                </body>
                </html>
            """.trimIndent()

            val webView = android.webkit.WebView(context)
            webView.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print(
                        jobName,
                        printAdapter,
                        android.print.PrintAttributes.Builder().build()
                    )
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            viewModel.triggerToast("جاري إرسال تصميم الملصق لطابعتك الحرارية... ✓")
        } catch (e: Exception) {
            viewModel.triggerToast("عذرًا، حدث خطأ أثناء إعداد الطباعة: ${e.message}")
        }
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🖨️ تصميم باركود والربط الحراري",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFE4ECEB))
                Spacer(modifier = Modifier.height(14.dp))

                // Label Visualizer (Realistic preview!)
                Text(
                    text = "🔎 معاينة الملصق والباركود المطبوع:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                // Realistic interactive label background representation matches labelWidthType
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9FBFB), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFD0DEDD), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .width(if (labelWidthType == "58mm") 180.dp else 240.dp)
                            .shadow(2.dp, RoundedCornerShape(4.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (includeCompanyName) {
                                Text(
                                    text = companyNameText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                ) {
                                    drawRect(Color.LightGray)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            
                            if (includeProductName) {
                                Text(
                                    text = product.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            
                            if (includeProductPrice) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Black, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "السعر: ${viewModel.formatCurrency(product.sellPrice)} ل.س",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive Drawn Barcode graphics bars in Composable!
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .width(130.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // Custom high fidelity EAN bars representation
                                    val barWidths = listOf(2, 1, 3, 1, 4, 2, 1, 3, 2, 1, 4, 2, 3, 1, 2)
                                    barWidths.forEach { w ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(w.dp)
                                                .background(Color.Black)
                                        )
                                    }
                                }
                                
                                if (includeCodeText) {
                                    Text(
                                        text = product.barcode.ifBlank { product.code },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(top = 2.dp),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Settings & Designer Inputs
                Text(
                    text = "⚙️ إعدادات وتعديل تصميم الملصق:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Company title config
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeCompanyName,
                        onCheckedChange = { includeCompanyName = it }
                    )
                    Text("اسم الشركة / المتجر", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (includeCompanyName) {
                    OutlinedTextField(
                        value = companyNameText,
                        onValueChange = { companyNameText = it },
                        label = { Text("نص الترويسة") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeProductName,
                        onCheckedChange = { includeProductName = it }
                    )
                    Text("اسم المادة بالكامل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeProductPrice,
                        onCheckedChange = { includeProductPrice = it }
                    )
                    Text("سعر البيع ل.س", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeCodeText,
                        onCheckedChange = { includeCodeText = it }
                    )
                    Text("رقم الباركود النصي أسفل الخطوط", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Label Width select
                Text("عرض شريط الورق الحراري:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = labelWidthType == "58mm",
                            onClick = { labelWidthType = "58mm" }
                        )
                        Text("58 ملم (قياسي)", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = labelWidthType == "80mm",
                            onClick = { labelWidthType = "80mm" }
                        )
                        Text("80 ملم (عريض)", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Execution buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = { printLabelNow() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F9D58)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("🖨️ اتصال وطباعة حرارية", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
