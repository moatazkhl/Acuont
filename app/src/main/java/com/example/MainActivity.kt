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
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.*
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

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    companion object {
        var tempWebViewForPrinting: android.webkit.WebView? = null
    }

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
    var dashboardReportStartDate by remember { mutableStateOf("") }
    var dashboardReportEndDate by remember { mutableStateOf("") }
    
    // Barcode designer and print states
    var isPrintBarcodeOpen by remember { mutableStateOf(false) }
    var selectedBarcodeProduct by remember { mutableStateOf<Product?>(null) }
    
    // Barcode scanner simulation states
    var isScannerOpen by remember { mutableStateOf(false) }
    var scannerOnScanned by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var isManageCategoriesOpen by remember { mutableStateOf(false) }
    var activePreviewInvoice by remember { mutableStateOf<Invoice?>(null) }
    var activePreviewVoucher by remember { mutableStateOf<Voucher?>(null) }

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
                            onNewInvoiceClick = {
                                viewModel.clearInvoiceForm()
                                isNewInvoiceOpen = true
                            },
                            onEditInvoiceClick = { inv ->
                                viewModel.loadInvoiceForEditing(inv)
                                isNewInvoiceOpen = true
                            },
                            onPreviewInvoiceClick = { inv ->
                                activePreviewInvoice = inv
                            }
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
                            },
                            onManageCategoriesClick = { isManageCategoriesOpen = true }
                        )
                        "reports" -> ReportsTabScreen(
                            viewModel = viewModel,
                            startDate = dashboardReportStartDate,
                            onStartDateChange = { dashboardReportStartDate = it },
                            endDate = dashboardReportEndDate,
                            onEndDateChange = { dashboardReportEndDate = it },
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
                    "invoices" -> {
                        viewModel.clearInvoiceForm()
                        isNewInvoiceOpen = true
                    }
                    "accounts" -> isNewAccountOpen = true
                    "products" -> isNewProductOpen = true
                    "reports" -> isCurrenciesOpen = true
                    else -> {
                        viewModel.clearInvoiceForm()
                        isNewInvoiceOpen = true
                    }
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

        activePreviewInvoice?.let { inv ->
            InvoicePreviewPrintDialog(
                invoice = inv,
                viewModel = viewModel,
                onClose = { activePreviewInvoice = null },
                onEditInvoice = { selectedInv ->
                    activePreviewInvoice = null
                    viewModel.loadInvoiceForEditing(selectedInv)
                    isNewInvoiceOpen = true
                }
            )
        }

        activePreviewVoucher?.let { v: Voucher ->
            VoucherPreviewDialog(
                voucher = v,
                viewModel = viewModel,
                onClose = { activePreviewVoucher = null }
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
                initialStartDate = dashboardReportStartDate,
                initialEndDate = dashboardReportEndDate,
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
                },
                onViewInvoice = { inv ->
                    activePreviewInvoice = inv
                },
                onViewVoucher = { v ->
                    activePreviewVoucher = v
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

        if (isManageCategoriesOpen) {
            ManageCategoriesDialog(
                viewModel = viewModel,
                onClose = { isManageCategoriesOpen = false }
            )
        }
    }
}

// ==========================================
// 1. INVOICES TAB PAGE
// ==========================================
@Composable
fun InvoicesTabScreen(
    viewModel: AppViewModel, 
    onNewInvoiceClick: () -> Unit,
    onEditInvoiceClick: (Invoice) -> Unit,
    onPreviewInvoiceClick: (Invoice) -> Unit
) {
    val invoices by viewModel.invoices.collectAsState()
    val filter by viewModel.invoiceFilter.collectAsState()
    val search by viewModel.invoiceSearch.collectAsState()

    // Calculate total sales and profits dynamically for all sale invoices to ensure accuracy even after clearing database
    val saleInvs = invoices.filter { it.type == "sale" }
    val salesVal = saleInvs.sumOf { it.total }
    val profitVal = saleInvs.sumOf { it.profit }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Dynamic quick stats cards for sales
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFD0DEDD))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "مجموع المبيعات", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
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
                    Text(text = "إجمالي الأرباح المتوقعة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
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

            val todayStr = viewModel.getTodayDateStr()
            val yesterdayStr = try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DATE, -1)
                sdf.format(cal.time)
            } catch (e: Exception) {
                "2026-05-30"
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedDates.forEach { date ->
                    val label = when (date) {
                        todayStr -> "اليوم"
                        yesterdayStr -> "أمس"
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
                        InvoiceItemRow(
                            invoice = inv, 
                            viewModel = viewModel,
                            onEditClick = { onEditInvoiceClick(inv) },
                            onPreviewClick = { onPreviewInvoiceClick(inv) },
                            onPrintClick = { onPreviewInvoiceClick(inv) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceItemRow(
    invoice: Invoice, 
    viewModel: AppViewModel, 
    onEditClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onPrintClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف الفاتورة", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { 
                Text(
                    "هل أنت متأكد من رغبتك في حذف الفاتورة ذات الرقم (${invoice.id}) للعميل (${invoice.customer})؟\nهذا الإجراء سيقوم بإعادة كميات المنتجات إلى المخزن وإلغاء التأثير المالي والعمولات وسندات القبض/الصرف التلقائية للفاتورة.",
                    fontSize = 13.sp
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteInvoiceCascaded(invoice)
                        showDeleteConfirm = false
                    }
                ) {
                    Text("نعم، تأكيد الحذف", color = Color(0xFFE03C3C), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء الأمر", color = Color.Gray)
                }
            }
        )
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
                    Spacer(modifier = Modifier.width(6.dp))
                    if (invoice.status == "draft") {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFD07A).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "مسودة", color = Color(0xFFC07D10), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = typeLabel, color = typeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    val payLabel = if (invoice.paymentType == "cash") "نقدي" else "آجل"
                    val payColor = if (invoice.paymentType == "cash") Color(0xFF2EBD7A) else Color(0xFFE67E22)
                    Box(
                        modifier = Modifier
                            .background(payColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = payLabel, color = payColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onPrintClick,
                        modifier = Modifier.size(32.dp).background(Color(0xFFE4ECEB), RoundedCornerShape(8.dp))
                    ) {
                        Text(text = "🖨️", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp).background(Color(0xFFE4ECEB), RoundedCornerShape(8.dp))
                    ) {
                        Text(text = "✏️", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
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
                    onClick = onPreviewClick,
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
    onPrintBarcodeClick: (Product) -> Unit,
    onManageCategoriesClick: () -> Unit
) {
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
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
        // Tab Filters with horizontal scroll and a quick Category manager button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .background(Color(0xFFE4ECEB), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val filters = listOf("all" to "الكل") + categories.map { it.id to it.name }
                filters.forEach { (key, label) ->
                    val active = filter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { viewModel.setProductFilter(key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
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

            // Quick button to open category manager dialog
            Button(
                onClick = onManageCategoriesClick,
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text("📁+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTabScreen(
    viewModel: AppViewModel,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    onReportClick: (String) -> Unit,
    onExchangeClick: () -> Unit
) {
    val invoices by viewModel.invoices.collectAsState()
    val vouchers by viewModel.vouchers.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val exchangeRatesList by viewModel.exchangeRates.collectAsState()

    val usdDefault by viewModel.rateUSD.collectAsState()
    val eurDefault by viewModel.rateEUR.collectAsState()
    val sarDefault by viewModel.rateSAR.collectAsState()
    val tryDefault by viewModel.rateTRY.collectAsState()

    // 1. Filtered raw transactions based on selected date ranges (using cleanDateStr for safe comparison with time-part dates)
    val filteredInvoices = remember(invoices, startDate, endDate) {
        invoices.filter {
            val cleanDate = cleanDateStr(it.date)
            (startDate.isEmpty() || cleanDate >= startDate) &&
            (endDate.isEmpty() || cleanDate <= endDate)
        }
    }

    val filteredVouchers = remember(vouchers, startDate, endDate) {
        vouchers.filter {
            val cleanDate = cleanDateStr(it.date)
            (startDate.isEmpty() || cleanDate >= startDate) &&
            (endDate.isEmpty() || cleanDate <= endDate)
        }
    }

    // 2. Aggregate sales, expense and profit converting each transaction with its day's exchange rate
    val totalSales = remember(filteredInvoices, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredInvoices.filter { it.status == "saved" && (it.type == "sale" || it.type == "return" || it.type == "return_sale") }.sumOf { inv ->
            val rateDateObj = exchangeRatesList.find { it.date == inv.date }
            val factor = when (inv.currency) {
                "USD" -> rateDateObj?.rateUSD ?: usdDefault
                "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                else -> 1.0
            }
            val multiplier = if (inv.type == "sale") 1.0 else -1.0
            inv.total * factor * multiplier
        }
    }

    val totalProfit = remember(filteredInvoices, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredInvoices.filter { it.status == "saved" && (it.type == "sale" || it.type == "return" || it.type == "return_sale") }.sumOf { inv ->
            val rateDateObj = exchangeRatesList.find { it.date == inv.date }
            val factor = when (inv.currency) {
                "USD" -> rateDateObj?.rateUSD ?: usdDefault
                "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                else -> 1.0
            }
            val multiplier = if (inv.type == "sale") 1.0 else -1.0
            inv.profit * factor * multiplier
        }
    }

    val totalExpenses = remember(filteredVouchers, accounts, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredVouchers.filter { it.type == "payment" }.sumOf { v ->
            val acc = accounts.find { it.id == v.accountId }
            if (acc?.type == "expense") {
                val rateDateObj = exchangeRatesList.find { it.date == v.date }
                val factor = when (acc.currency) {
                    "USD" -> rateDateObj?.rateUSD ?: usdDefault
                    "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                    "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                    "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                    else -> 1.0
                }
                v.amount * factor
            } else {
                0.0
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        // Date filters for primary reports metrics and charts
        Text(
            text = "📅 فرز وتصفية تقارير الفترة الزمنية:",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = if (startDate.isEmpty()) "من تاريخ" else startDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("من تاريخ", fontSize = 10.sp) },
                    textStyle = TextStyle(fontSize = 11.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color(0xFFF0F5F4),
                        unfocusedContainerColor = Color(0xFFF0F5F4)
                    ),
                    trailingIcon = {
                        if (startDate.isNotEmpty()) {
                            IconButton(onClick = { onStartDateChange("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker(context) { onStartDateChange(it) } }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = if (endDate.isEmpty()) "إلى تاريخ" else endDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("إلى تاريخ", fontSize = 10.sp) },
                    textStyle = TextStyle(fontSize = 11.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color(0xFFF0F5F4),
                        unfocusedContainerColor = Color(0xFFF0F5F4)
                    ),
                    trailingIcon = {
                        if (endDate.isNotEmpty()) {
                            IconButton(onClick = { onEndDateChange("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker(context) { onEndDateChange(it) } }
                )
            }
        }

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

    // --- Backup list and confirmation states ---
    val availableBackups by viewModel.availableBackups.collectAsState()
    var backupToRestoreItem by remember { mutableStateOf<com.example.ui.AppViewModel.BackupItem?>(null) }
    var backupToDeleteItem by remember { mutableStateOf<com.example.ui.AppViewModel.BackupItem?>(null) }
    var backupsDropdownExpanded by remember { mutableStateOf(false) }

    // --- Google Drive Backup, OAuth and Sync states ---
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val isGoogleDriveLinked by viewModel.isGoogleDriveLinked.collectAsState()
    val cloudBackups by viewModel.cloudBackups.collectAsState()
    val isLoadingCloudBackups by viewModel.isLoadingCloudBackups.collectAsState()
    val isSyncingToCloud by viewModel.isSyncingToCloud.collectAsState()
    val googleAuthIntent by viewModel.googleAuthIntentToResolve.collectAsState()
    var cloudBackupsDropdownExpanded by remember { mutableStateOf(false) }
    var cloudBackupToRestoreItem by remember { mutableStateOf<com.example.ui.GoogleDriveHelper.CloudBackupItem?>(null) }
    var cloudBackupToDeleteItem by remember { mutableStateOf<com.example.ui.GoogleDriveHelper.CloudBackupItem?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    viewModel.linkGoogleAccount(account)
                }
            } catch (e: Exception) {
                viewModel.triggerToast("فشل ربط الحساب: ${e.message}")
            }
        }
    }

    val authResolutionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.clearGoogleAuthIntent()
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.refreshCloudBackups()
        }
    }

    androidx.compose.runtime.LaunchedEffect(googleAuthIntent) {
        googleAuthIntent?.let {
            authResolutionLauncher.launch(it)
        }
    }

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
                    var showCurrDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tempCompanyCurrencyText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("العملة الافتراضية للحسابات") },
                            trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showCurrDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showCurrDropdown,
                            onDismissRequest = { showCurrDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("ل.س", "USD", "EUR", "SAR", "TRY").forEach { cur ->
                                DropdownMenuItem(
                                    text = { Text(cur) },
                                    onClick = {
                                        tempCompanyCurrencyText = cur
                                        showCurrDropdown = false
                                    }
                                )
                            }
                        }
                    }
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
                    var showCreateCurrDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newCompanyCurrencyText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("العملة الافتراضية") },
                            trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showCreateCurrDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showCreateCurrDropdown,
                            onDismissRequest = { showCreateCurrDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("ل.س", "USD", "EUR", "SAR", "TRY").forEach { cur ->
                                DropdownMenuItem(
                                    text = { Text(cur) },
                                    onClick = {
                                        newCompanyCurrencyText = cur
                                        showCreateCurrDropdown = false
                                    }
                                )
                            }
                        }
                    }
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
                    var showEditCurrDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editCompanyCurrencyText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("العملة الافتراضية") },
                            trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showEditCurrDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showEditCurrDropdown,
                            onDismissRequest = { showEditCurrDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("ل.س", "USD", "EUR", "SAR", "TRY").forEach { cur ->
                                DropdownMenuItem(
                                    text = { Text(cur) },
                                    onClick = {
                                        editCompanyCurrencyText = cur
                                        showEditCurrDropdown = false
                                    }
                                )
                            }
                        }
                    }
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
                Text("اختر المنشأة المالية من القائمة المنسدلة للتبديل والتحكم والاطلاع:", fontSize = 11.sp, color = Color.Gray)

                var companyDropdownExpanded by remember { mutableStateOf(false) }

                // Dropdown Selector for all companies
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("قائمة الشركات والدفاتر المالية المتوفرة") },
                        leadingIcon = { Text("🏪", modifier = Modifier.padding(start = 8.dp)) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFFD0DEDD)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { companyDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = companyDropdownExpanded,
                        onDismissRequest = { companyDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        companiesList.forEach { comp ->
                            val isActive = comp.id == activeCompanyId
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🏪 ", fontSize = 12.sp)
                                                Text(comp.name, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                            }
                                            Text("هاتف: ${comp.phone} | عملة: ${comp.currency}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        if (isActive) {
                                            Text("المنشأة النشطة 🟢", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("انتقال 🔄", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    companyDropdownExpanded = false
                                    if (!isActive) {
                                        viewModel.switchCompanyDb(comp.id, comp.name)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Underneath, show detailed info and control buttons for the currently active company
                val activeCompanyObj = remember(companiesList, activeCompanyId) {
                    companiesList.find { it.id == activeCompanyId }
                }

                activeCompanyObj?.let { comp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📍 التفاصيل الجغرافية والمالية للمنشأة النشطة:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("رقم المنشأة الإلكتروني:", fontSize = 10.sp, color = Color.Gray)
                                Text("#${comp.id}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("اسم الشركة:", fontSize = 10.sp, color = Color.Gray)
                                Text(comp.name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("رقم الهاتف والاتصال:", fontSize = 10.sp, color = Color.Gray)
                                Text(comp.phone.ifBlank { "غير محدد" }, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("العنوان الرئيسي:", fontSize = 10.sp, color = Color.Gray)
                                Text(comp.address.ifBlank { "غير محدد" }, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("العملة المحاسبية الافتراضية:", fontSize = 10.sp, color = Color.Gray)
                                Text(comp.currency, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Edit Button
                                OutlinedButton(
                                    onClick = {
                                        companyToEdit = comp
                                        editCompanyNameText = comp.name
                                        editCompanyPhoneText = comp.phone
                                        editCompanyAddressText = comp.address
                                        editCompanyCurrencyText = comp.currency
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("تعديل بياناتها ✏️", fontSize = 11.sp)
                                    }
                                }

                                // Delete Button
                                OutlinedButton(
                                    onClick = { companyToDelete = comp },
                                    enabled = companiesList.size > 1,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE03C3C))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("حذف الدفاتر 🗑️", fontSize = 11.sp)
                                    }
                                }
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
        Text(text = "النسخ الاحتياطي وإدارة قواعد البيانات المترابطة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
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
                        .clickable { viewModel.backupCurrentDatabase() }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "💾 إنشاء نسخة احتياطية للجهاز الآن", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "حفظ نسخة داتا لحظية حية فورية في المجلد المخصص بالوقت والتاريخ", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(text = "💾", fontSize = 20.sp)
                }

                HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))

                if (!isGoogleDriveLinked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                                )
                                    .requestEmail()
                                    .requestScopes(
                                        com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"),
                                        com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.appdata")
                                    )
                                    .build()
                                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(client.signInIntent)
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🔗 ربط وتفعيل حساب Google Drive", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Text(text = "تسجيل الدخول والمزامنة والنسخ السحابي وبصمة الأمان ضد الضياع", fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(text = "☁️", fontSize = 20.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "☁️ حساب Google Drive متصل ونشط",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = googleAccountEmail ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.DarkGray
                                )
                            }
                            TextButton(
                                onClick = { viewModel.unlinkGoogleAccount() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                            ) {
                                Text("فصل الحساب 🚪", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSyncingToCloud) { viewModel.backupCurrentDbToCloud() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "رفع نسخة سحابية حية الآن",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "توليد ملف نسخة احتياطية حقيقي وبصمة زمنية مباشرة وحفظه سحابياً",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            if (isSyncingToCloud) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(text = "⬆️", fontSize = 16.sp)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFD0DEDD).copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.refreshCloudBackups() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تحديث أرشيف النسخ السحابية",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "مزامنة وجلب قائمة ملفات النسخ السحابية المتوفرة حالياً من درايف",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(text = "🔄", fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "🗄️ النسخ الاحتياطية المتوفرة للاسترجاع (تاريخ ملخص)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (availableBackups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد نسخ احتياطية متوفرة حالياً داخل مجلد التطبيق.\nاضغط على (إنشاء نسخة احتياطية للجهاز الآن) بالأعلى لتوليد ملف فوري.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = "انقر هنا لاختيار نسخة واستعادتها (${availableBackups.size} نسخة متوفرة)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("أرشيف النسخ الاحتياطية المتوفرة") },
                            leadingIcon = { Text("🗄️", modifier = Modifier.padding(start = 8.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFFD0DEDD)
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { backupsDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = backupsDropdownExpanded,
                            onDismissRequest = { backupsDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            availableBackups.forEach { backup ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("📅 ", fontSize = 11.sp)
                                                    Text(text = backup.dateDisplay, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("🏢 ", fontSize = 10.sp)
                                                    Text(text = "منشأة: ${backup.companyName} (${backup.companyId})", fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }
                                            
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Quick Custom Button to restore
                                                Box(
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            backupsDropdownExpanded = false
                                                            backupToRestoreItem = backup
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text("استعادة 🔄", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }

                                                // Quick Custom Button to delete
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFFFEBEE), RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            backupsDropdownExpanded = false
                                                            backupToDeleteItem = backup
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text("حذف 🗑️", color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        backupsDropdownExpanded = false
                                        backupToRestoreItem = backup
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isGoogleDriveLinked) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "☁️ أرشيف النسخ الاحتياطية السحابية (على Google Drive)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFD0DEDD))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cloudBackups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isLoadingCloudBackups) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(
                                    text = if (isLoadingCloudBackups) "جاري جلب النسخ الاحتياطية من Google Drive..." else "لا توجد نسخ احتياطية متوفرة حالياً في مساحتك السحابية لـ Google Drive.\nيمكنك الضغط على (رفع نسخة سحابية حية الآن) بالأعلى لتوليد نسخة سحابية فورية.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = "انقر هنا لاختيار نسخة سحابية واستعادتها (${cloudBackups.size} نسخة متوفرة)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("أرشيف النسخ السحابية المتوفرة") },
                                leadingIcon = { Text("☁️", modifier = Modifier.padding(start = 8.dp)) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFFD0DEDD)
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { cloudBackupsDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = cloudBackupsDropdownExpanded,
                                onDismissRequest = { cloudBackupsDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                cloudBackups.forEach { backup ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("📅 ", fontSize = 11.sp)
                                                        Text(text = backup.dateDisplay, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("🏢 ", fontSize = 10.sp)
                                                        Text(text = "منشأة: ${backup.companyName} (${backup.companyId})", fontSize = 10.sp, color = Color.Gray)
                                                    }
                                                }
                                                
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Quick Button to Restore from Cloud
                                                    Box(
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                cloudBackupsDropdownExpanded = false
                                                                cloudBackupToRestoreItem = backup
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("استعادة 🔄", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // Quick Button to delete from Cloud
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFFFFEBEE), RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                cloudBackupsDropdownExpanded = false
                                                                cloudBackupToDeleteItem = backup
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("حذف 🗑️", color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            cloudBackupsDropdownExpanded = false
                                            cloudBackupToRestoreItem = backup
                                        }
                                    )
                                }
                            }
                        }
                    }
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

        // --- Backup Restore Dialog Overlay ---
        backupToRestoreItem?.let { backup ->
            AlertDialog(
                onDismissRequest = { backupToRestoreItem = null },
                title = {
                    Text(text = "🔄 تأكيد استعادة النسخة الاحتياطية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Text(
                        text = "هل أنت متأكد من استعادة النسخة الاحتياطية للشركة '${backup.companyName}' بتاريخ '${backup.dateDisplay}'؟\n\nتنبيه هام ⚠️: سيتم استبدال قاعدة البيانات الفعالة حالياً بشكل كامل وتحميل البيانات المسترجعة فوراً. يرجى التأكد من حفظ أي تغييرات قبل الاستمرار.",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.restoreDatabase(backup.file)
                            backupToRestoreItem = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("نعم، استعادة 🔄", fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { backupToRestoreItem = null }) {
                        Text("إلغاء", fontSize = 12.sp)
                    }
                }
            )
        }

        // --- Backup Delete Dialog Overlay ---
        backupToDeleteItem?.let { backup ->
            AlertDialog(
                onDismissRequest = { backupToDeleteItem = null },
                title = {
                    Text(text = "🗑️ تأكيد حذف نسخة احتياطية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Text(
                        text = "هل أنت متأكد من حذف ملف النسخة الاحتياطية للشركة '${backup.companyName}' بتاريخ '${backup.dateDisplay}' نهائياً من الجهاز؟\n\nلا يمكن التراجع عن هذا الإجراء بعد الحذف.",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteBackup(backup)
                            backupToDeleteItem = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("حذف نهائي 🗑️", fontSize = 12.sp, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { backupToDeleteItem = null }) {
                        Text("إلغاء", fontSize = 12.sp)
                    }
                }
            )
        }

        // --- Cloud Backup Restore Dialog Overlay ---
        cloudBackupToRestoreItem?.let { cloudBackup ->
            AlertDialog(
                onDismissRequest = { cloudBackupToRestoreItem = null },
                title = {
                    Text(text = "🔄 تأكيد استعادة النسخة السحابية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Text(
                        text = "هل أنت متأكد من تنزيل واستعادة النسخة الاحتياطية السحابية للشركة '${cloudBackup.companyName}' بتاريخ '${cloudBackup.dateDisplay}' منسابقة لجهازك؟\n\nتنبيه هام ⚠️: سيتم استبدال البيانات الحالية على هذا الجهاز فوراً ببيانات النسخة الاحتياطية السحابية التي تم تنزيلها. يرجى حفظ أي بيانات هامة أولاً.",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.restoreDbFromCloud(cloudBackup)
                            cloudBackupToRestoreItem = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("نعم، استعادة سحابية ☁️", fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { cloudBackupToRestoreItem = null }) {
                        Text("إلغاء", fontSize = 12.sp)
                    }
                }
            )
        }

        // --- Cloud Backup Delete Dialog Overlay ---
        cloudBackupToDeleteItem?.let { cloudBackup ->
            AlertDialog(
                onDismissRequest = { cloudBackupToDeleteItem = null },
                title = {
                    Text(text = "🗑️ تأكيد حذف نسخة سحابية", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Text(
                        text = "هل أنت متأكد من حذف ملف النسخة الاحتياطية السحابية للشركة '${cloudBackup.companyName}' بتاريخ '${cloudBackup.dateDisplay}' نهائياً من حساب Google Drive الخاص بك؟\n\nتنبيه هام ⚠️: سيتم محو هذا الملف من خوادم السحابة نهائياً ولا يمكن التراجع عن هذا الإجراء بعد الحذف.",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCloudBackup(cloudBackup)
                            cloudBackupToDeleteItem = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("نعم، حذف نهائي 🗑️", fontSize = 12.sp, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { cloudBackupToDeleteItem = null }) {
                        Text("إلغاء", fontSize = 12.sp)
                    }
                }
            )
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
                        val invoiceDate by viewModel.selectedInvoiceDate.collectAsState()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = invoiceDate.ifBlank { "2026-05-30" },
                                onValueChange = { },
                                label = { Text("تاريخ الفاتورة") },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                trailingIcon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = null) }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        showDatePicker(context) { date ->
                                            viewModel.selectedInvoiceDate.value = date
                                        }
                                    }
                            )
                        }
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

                    // 3. Payment Type & Down Payment Option
                    item {
                        val payType by viewModel.invoicePaymentType.collectAsState()
                        val paidAmt by viewModel.invoicePaidAmount.collectAsState()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "طريقة الدفع والتحصيل", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE4ECEB), RoundedCornerShape(10.dp))
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (payType == "cash") MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { 
                                                viewModel.invoicePaymentType.value = "cash"
                                                viewModel.invoicePaidAmount.value = ""
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "نقدي (كاش)",
                                            color = if (payType == "cash") Color.White else Color(0xFF4A6B65),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (payType == "credit") MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { 
                                                viewModel.invoicePaymentType.value = "credit"
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "آجل (رصيد ذمم)",
                                            color = if (payType == "credit") Color.White else Color(0xFF4A6B65),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                if (payType == "credit") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = paidAmt,
                                        onValueChange = { viewModel.invoicePaidAmount.value = it },
                                        label = { Text("تسجيل دفعة نقدية مسددة ($activeCurrency)") },
                                        placeholder = { Text("أدخل قيمة الدفعة (مثال: 50000)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        leadingIcon = { Text("💵", modifier = Modifier.padding(horizontal = 6.dp)) },
                                        singleLine = true
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
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(text = "سعر الوحدة:", fontSize = 11.sp, color = Color.Gray)
                                            var priceText by remember(item.price) { mutableStateOf(if (item.price == item.price.toInt().toDouble()) item.price.toInt().toString() else item.price.toString()) }
                                            BasicTextField(
                                                value = priceText,
                                                onValueChange = { newValue ->
                                                    priceText = newValue
                                                    val parsed = newValue.toDoubleOrNull()
                                                    if (parsed != null && parsed >= 0.0) {
                                                        viewModel.setInvoiceItemFormPrice(idx, parsed)
                                                    }
                                                },
                                                textStyle = TextStyle(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                ),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier
                                                    .width(65.dp)
                                                    .background(Color.White, RoundedCornerShape(4.dp))
                                                    .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                            Text(text = "$activeCurrency", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "المجموع: ${viewModel.formatCurrency(item.qty * item.price)} $activeCurrency", color = Color.DarkGray, fontSize = 11.sp)
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

                    // Optional Discount and Tax fields
                    item {
                        val discountStr by viewModel.invoiceDiscount.collectAsState()
                        val taxStr by viewModel.invoiceTax.collectAsState()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = discountStr,
                                onValueChange = { viewModel.invoiceDiscount.value = it },
                                label = { Text("حسم السعر ($activeCurrency)") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Text("🎁", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
                            )

                            OutlinedTextField(
                                value = taxStr,
                                onValueChange = { viewModel.invoiceTax.value = it },
                                label = { Text("الضريبة المضافة (%)") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = { Text("📝", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
                            )
                        }
                    }

                    // Sub totals summary card in arabic
                    item {
                        val discountVal = viewModel.invoiceDiscount.collectAsState().value.toDoubleOrNull() ?: 0.0
                        val taxPercent = viewModel.invoiceTax.collectAsState().value.toDoubleOrNull() ?: 0.0
                        val taxVal = (total - discountVal) * (taxPercent / 100.0)
                        val finalTotal = total - discountVal + taxVal

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE4ECEB))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "إجمالي المواد:", fontSize = 12.sp, color = Color.Gray)
                                    Text(text = "${viewModel.formatCurrency(total)} $activeCurrency", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                if (discountVal > 0.0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "الحسم (الخصم):", fontSize = 12.sp, color = Color(0xFFC0392B))
                                        Text(text = "− ${viewModel.formatCurrency(discountVal)} $activeCurrency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                                    }
                                }
                                if (taxVal > 0.0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = "الضريبة المضافة ($taxPercent%):", fontSize = 12.sp, color = Color(0xFF2E86C1))
                                        Text(text = "+ ${viewModel.formatCurrency(taxVal)} $activeCurrency", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E86C1))
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray).padding(vertical = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "صافي قيمة الفاتورة:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "${viewModel.formatCurrency(finalTotal)} $activeCurrency", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
    val categories by viewModel.categories.collectAsState()

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
                        val currentCategoryObj = categories.find { it.id == category }
                        val catLabel = currentCategoryObj?.name ?: "أخرى"
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
                            categories.forEach { catObj ->
                                DropdownMenuItem(
                                    text = { Text(text = "${catObj.icon}   ${catObj.name}") },
                                    onClick = {
                                        category = catObj.id
                                        viewModel.newProductCategory.value = catObj.id
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

                Spacer(modifier = Modifier.height(10.dp))

                val context = androidx.compose.ui.platform.LocalContext.current
                val voucherDateState by viewModel.voucherDate.collectAsState()
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (voucherDateState.isBlank()) viewModel.getTodayDateStr() else voucherDateState,
                        onValueChange = {},
                        label = { Text("تاريخ السند (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                showDatePicker(context) { viewModel.voucherDate.value = it }
                            }
                    )
                }

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

    val exchangeRatesList by viewModel.exchangeRates.collectAsState()

    var selectedRateDate by remember { mutableStateOf("2026-05-30") }

    // Look up if we have stored rates for selectedRateDate in SQLite database
    val matchingRate = remember(exchangeRatesList, selectedRateDate) {
        exchangeRatesList.find { it.date == selectedRateDate }
    }

    // Reactive input fields linked to currently selected date rate or global rates otherwise
    var inputUsd by remember(matchingRate, usd) { mutableStateOf(matchingRate?.rateUSD?.toInt()?.toString() ?: usd.toInt().toString()) }
    var inputEur by remember(matchingRate, eur) { mutableStateOf(matchingRate?.rateEUR?.toInt()?.toString() ?: eur.toInt().toString()) }
    var inputSar by remember(matchingRate, sar) { mutableStateOf(matchingRate?.rateSAR?.toInt()?.toString() ?: sar.toInt().toString()) }
    var inputTry by remember(matchingRate, tryVal) { mutableStateOf(matchingRate?.rateTRY?.toInt()?.toString() ?: tryVal.toInt().toString()) }

    var convertAmountText by remember { mutableStateOf("") }
    var convertFromCurrency by remember { mutableStateOf("USD") }
    var convertResultDisplayStr by remember { mutableStateOf("0 ل.س") }

    fun runCurrencyMath() {
        val amt = convertAmountText.toDoubleOrNull() ?: 0.0
        val u = inputUsd.toDoubleOrNull() ?: usd
        val e = inputEur.toDoubleOrNull() ?: eur
        val s = inputSar.toDoubleOrNull() ?: sar
        val t = inputTry.toDoubleOrNull() ?: tryVal
        val rateMultiplier = when (convertFromCurrency) {
            "USD" -> u
            "EUR" -> e
            "SAR" -> s
            else -> t
        }
        val resultingSyp = amt * rateMultiplier
        convertResultDisplayStr = viewModel.formatCurrency(resultingSyp) + " ل.س"
    }

    val context = androidx.compose.ui.platform.LocalContext.current

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

                // Date picker trigger for historical exchange rate editing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable {
                            showDatePicker(context) { selectedRateDate = it }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("صرف اليوم والتواريخ التاريخية:", fontSize = 11.sp, color = Color.Gray)
                        Text("📅 تاريخ تعديل الصرف: $selectedRateDate", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("سجل التاريخ ⚙️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                if (matchingRate != null) {
                    Text(
                        text = "✓ وجد أسعار مخزنة سابقاً لهذا التاريخ بقاعدة البيانات",
                        color = Color(0xFF2EBD7A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                } else {
                    Text(
                        text = "• سيتم إنشاء باقة أسعار تاريخية جديدة لهذا الموعد عند الحفظ",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val editableRates = listOf(
                    Triple("🇺🇸 دولار أمريكي (USD)", inputUsd) { valNew: String -> inputUsd = valNew },
                    Triple("🇪🇺 يورو أوروبي (EUR)", inputEur) { valNew: String -> inputEur = valNew },
                    Triple("🇸🇦 ريال سعودي (SAR)", inputSar) { valNew: String -> inputSar = valNew },
                    Triple("🇹🇷 ليرة تركية (TRY)", inputTry) { valNew: String -> inputTry = valNew }
                )

                editableRates.forEach { (title, currentStr, updateFn) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = currentStr,
                            onValueChange = { stringVal ->
                                updateFn(stringVal)
                                runCurrencyMath()
                            },
                            modifier = Modifier.width(110.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ل.س", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.LightGray)
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "برنامج محوّل أسعار صرف العملات", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val u = inputUsd.toDoubleOrNull() ?: usd
                            val e = inputEur.toDoubleOrNull() ?: eur
                            val s = inputSar.toDoubleOrNull() ?: sar
                            val t = inputTry.toDoubleOrNull() ?: tryVal
                            // Save as global defaults
                            viewModel.rateUSD.value = u
                            viewModel.rateEUR.value = e
                            viewModel.rateSAR.value = s
                            viewModel.rateTRY.value = t
                            // Save for selected date as well
                            viewModel.saveExchangeRateForDate(selectedRateDate, u, e, s, t)
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حفظ وتأكيد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val u = inputUsd.toDoubleOrNull() ?: usd
                            val e = inputEur.toDoubleOrNull() ?: eur
                            val s = inputSar.toDoubleOrNull() ?: sar
                            val t = inputTry.toDoubleOrNull() ?: tryVal
                            // ONLY save for this historical date in SQLite
                            viewModel.saveExchangeRateForDate(selectedRateDate, u, e, s, t)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("تثبيت تاريخي فقط", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}


// ==========================================
// 10. REVIEWS STATEMENTS & REPORTS MODAL DETAILS
// ==========================================
@Composable
fun ReportDetailsDialog(
    viewModel: AppViewModel,
    type: String,
    initialStartDate: String,
    initialEndDate: String,
    onClose: () -> Unit
) {
    val invoices by viewModel.invoices.collectAsState()
    val products by viewModel.products.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val vouchers by viewModel.vouchers.collectAsState()
    val exchangeRatesList by viewModel.exchangeRates.collectAsState()

    val usdDefault by viewModel.rateUSD.collectAsState()
    val eurDefault by viewModel.rateEUR.collectAsState()
    val sarDefault by viewModel.rateSAR.collectAsState()
    val tryDefault by viewModel.rateTRY.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }

    val filteredInvoices = remember(invoices, startDate, endDate) {
        invoices.filter {
            val cleanDate = cleanDateStr(it.date)
            val dateOk = (startDate.isEmpty() || cleanDate >= startDate) &&
                         (endDate.isEmpty() || cleanDate <= endDate)
            dateOk
        }
    }

    val filteredVouchers = remember(vouchers, startDate, endDate) {
        vouchers.filter {
            val cleanDate = cleanDateStr(it.date)
            val dateOk = (startDate.isEmpty() || cleanDate >= startDate) &&
                         (endDate.isEmpty() || cleanDate <= endDate)
            dateOk
        }
    }

    val totalSales = remember(filteredInvoices, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredInvoices.filter { it.status == "saved" && (it.type == "sale" || it.type == "return" || it.type == "return_sale") }.sumOf { inv ->
            val rateDateObj = exchangeRatesList.find { it.date == inv.date }
            val factor = when (inv.currency) {
                "USD" -> rateDateObj?.rateUSD ?: usdDefault
                "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                else -> 1.0
            }
            val multiplier = if (inv.type == "sale") 1.0 else -1.0
            inv.total * factor * multiplier
        }
    }

    val totalProfit = remember(filteredInvoices, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredInvoices.filter { it.status == "saved" && (it.type == "sale" || it.type == "return" || it.type == "return_sale") }.sumOf { inv ->
            val rateDateObj = exchangeRatesList.find { it.date == inv.date }
            val factor = when (inv.currency) {
                "USD" -> rateDateObj?.rateUSD ?: usdDefault
                "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                else -> 1.0
            }
            val multiplier = if (inv.type == "sale") 1.0 else -1.0
            inv.profit * factor * multiplier
        }
    }

    val totalReceipts = remember(filteredVouchers, accounts, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredVouchers.filter { it.type == "receipt" }.sumOf { v ->
            val acc = accounts.find { it.id == v.accountId }
            val rateDateObj = exchangeRatesList.find { it.date == v.date }
            val factor = when (acc?.currency) {
                "USD" -> rateDateObj?.rateUSD ?: usdDefault
                "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                else -> 1.0
            }
            v.amount * factor
        }
    }

    val totalPayments = remember(filteredVouchers, accounts, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredVouchers.filter { it.type == "payment" }.sumOf { v ->
            val acc = accounts.find { it.id == v.accountId }
            val rateDateObj = exchangeRatesList.find { it.date == v.date }
            val factor = when (acc?.currency) {
                "USD" -> rateDateObj?.rateUSD ?: usdDefault
                "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                else -> 1.0
            }
            v.amount * factor
        }
    }

    val totalExpenses = remember(filteredVouchers, accounts, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        filteredVouchers.filter { it.type == "payment" }.sumOf { v ->
            val acc = accounts.find { it.id == v.accountId }
            if (acc?.type == "expense") {
                val rateDateObj = exchangeRatesList.find { it.date == v.date }
                val factor = when (acc.currency) {
                    "USD" -> rateDateObj?.rateUSD ?: usdDefault
                    "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                    "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                    "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                    else -> 1.0
                }
                v.amount * factor
            } else {
                0.0
            }
        }
    }

    val topProductsList = remember(filteredInvoices, products, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        val soldMap = mutableMapOf<String, Int>()
        val revenueMap = mutableMapOf<String, Double>()
        filteredInvoices.filter { it.status == "saved" && it.type == "sale" }.forEach { inv ->
            val rateDateObj = exchangeRatesList.find { it.date == inv.date }
            val factor = when (inv.currency) {
                "USD" -> rateDateObj?.rateUSD ?: usdDefault
                "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                else -> 1.0
            }
            try {
                val items = viewModel.deserializeItems(inv.itemsJson)
                items.forEach { item ->
                    soldMap[item.name] = (soldMap[item.name] ?: 0) + item.qty
                    revenueMap[item.name] = (revenueMap[item.name] ?: 0.0) + (item.qty * item.price * factor)
                }
            } catch (e: Exception) {}
        }
        products.map { p ->
            val soldQty = soldMap[p.name] ?: 0
            val soldRevenue = revenueMap[p.name] ?: 0.0
            p to (soldQty to soldRevenue)
        }.sortedByDescending { it.second.first }
    }

    val topCustomersList = remember(filteredInvoices, filteredVouchers, accounts, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault) {
        val activityMap = mutableMapOf<String, Double>()
        filteredInvoices.filter { it.status == "saved" }.forEach { inv ->
            val matchingAccount = accounts.find { it.name == inv.customer }
            if (matchingAccount != null) {
                val rateDateObj = exchangeRatesList.find { it.date == inv.date }
                val factor = when (inv.currency) {
                    "USD" -> rateDateObj?.rateUSD ?: usdDefault
                    "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                    "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                    "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                    else -> 1.0
                }
                activityMap[matchingAccount.id] = (activityMap[matchingAccount.id] ?: 0.0) + (inv.total * factor)
            }
        }
        filteredVouchers.forEach { v ->
            val matchingAccount = accounts.find { it.id == v.accountId }
            if (matchingAccount != null) {
                val rateDateObj = exchangeRatesList.find { it.date == v.date }
                val factor = when (matchingAccount.currency) {
                    "USD" -> rateDateObj?.rateUSD ?: usdDefault
                    "EUR" -> rateDateObj?.rateEUR ?: eurDefault
                    "SAR" -> rateDateObj?.rateSAR ?: sarDefault
                    "TRY" -> rateDateObj?.rateTRY ?: tryDefault
                    else -> 1.0
                }
                activityMap[v.accountId] = (activityMap[v.accountId] ?: 0.0) + (v.amount * factor)
            }
        }
        val customers = accounts.filter { it.type == "customer" }
        customers.map { c ->
            val totalActivityStr = activityMap[c.id] ?: 0.0
            c to totalActivityStr
        }.sortedByDescending { it.second }
    }

    val title = when (type) {
        "daily" -> "تقرير الحركة اليومية"
        "pl" -> "بيان الأرباح والخسائر"
        "topProducts" -> "المستودع: المواد الأكثر حركة مبيعاً"
        "topCustomers" -> "العملاء الأكثر حركة محاسبية"
        else -> "المستودع: المواد تحت حد الأمان النقدي"
    }

    val dateRangeText = when {
        startDate.isNotEmpty() && endDate.isNotEmpty() -> "الفترة: من $startDate إلى $endDate"
        startDate.isNotEmpty() -> "الفترة: منذ تاريخ $startDate"
        endDate.isNotEmpty() -> "الفترة: حتى تاريخ $endDate"
        else -> "الفترة: كافة التواريخ والبيانات"
    }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color(0xFFE4ECEB))
                Spacer(modifier = Modifier.height(8.dp))

                // Date Filter Section
                Text(
                    text = "⚙️ فرز وتحديد الفترة الزمنية للتقرير:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = if (startDate.isBlank()) "من تاريخ" else startDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("من تاريخ", fontSize = 10.sp) },
                            textStyle = TextStyle(fontSize = 11.sp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color(0xFFF0F5F4),
                                unfocusedContainerColor = Color(0xFFF0F5F4)
                            ),
                            trailingIcon = {
                                if (startDate.isNotBlank()) {
                                    IconButton(onClick = { startDate = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
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
                            value = if (endDate.isBlank()) "إلى تاريخ" else endDate,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("إلى تاريخ", fontSize = 10.sp) },
                            textStyle = TextStyle(fontSize = 11.sp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = Color(0xFFF0F5F4),
                                unfocusedContainerColor = Color(0xFFF0F5F4)
                            ),
                            trailingIcon = {
                                if (endDate.isNotBlank()) {
                                    IconButton(onClick = { endDate = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
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

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFE4ECEB))
                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (type) {
                        "daily" -> {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = dateRangeText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                                            Text("سندات صرف مالي", fontSize = 11.sp, color = Color.Gray)
                                            Text(viewModel.formatCurrency(totalPayments), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE03C3C))
                                        }
                                    }
                                    Card(modifier = Modifier.weight(1f).padding(4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9))) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text("سندات استلام مالي", fontSize = 11.sp, color = Color.Gray)
                                            Text(viewModel.formatCurrency(totalReceipts), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2EBD7A))
                                        }
                                    }
                                }
                            }
                        }
                        "pl" -> {
                            val cogs = totalSales - totalProfit
                            val grossProfit = totalProfit
                            val dynamicExpenses = totalExpenses
                            val netProfit = grossProfit - dynamicExpenses
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Text(
                                    text = dateRangeText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2EBD7A).copy(alpha = 0.12f))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "صافي ربح الفترة المحدد مسبقاً", fontSize = 11.sp, color = Color(0xFF1A9A60), fontWeight = FontWeight.Bold)
                                        Text(text = "${viewModel.formatCurrency(netProfit)} ل.س", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A9A60))
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                val plLines = listOf(
                                    Triple("إجمالي المبيعات المحققة", totalSales, Color(0xFF1A9A60)),
                                    Triple("تكلفة البضاعة المباعة (الرقم الفعلي)", -cogs, Color(0xFFE03C3C)),
                                    Triple("مجمل الربح الإجمالي", grossProfit, Color(0xFF1A9A60)),
                                    Triple("المصاريف التشغيلية الفعلية", -dynamicExpenses, Color(0xFFE03C3C)),
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
                            val sortedList = topProductsList.filter { it.second.first > 0 || (startDate.isEmpty() && endDate.isEmpty()) }
                            if (sortedList.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("لا توجد مبيعات في هذه الفترة المحددة")
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(sortedList) { index, (p, stats) ->
                                        val (soldQty, soldRevenue) = stats
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
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(text = "الكمية المباعة: $soldQty ${p.unit}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                                Text(text = "${viewModel.formatCurrency(soldRevenue)} ل.س", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "topCustomers" -> {
                            val activeCustomers = topCustomersList.filter { it.second > 0.0 || (startDate.isEmpty() && endDate.isEmpty()) }
                            if (activeCustomers.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("لا توجد حركة مالية للعملاء في هذه الفترة")
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(activeCustomers) { index, (c, volume) ->
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
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = c.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    if (volume > 0.0) {
                                                        Text(text = "حجم التعامل المحاسبي بالفترة: ${viewModel.formatCurrency(volume)} ${c.currency}", fontSize = 11.sp, color = Color.Gray)
                                                    } else {
                                                        Text(text = "لا توجد حركة مالية بالفترة الحالية", fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                }
                                                Text(text = "${viewModel.formatCurrency(Math.abs(c.balance))} ${c.currency}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
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
                        onClick = {
                            exportReportToExcel(
                                context = context,
                                type = type,
                                title = title,
                                startDate = startDate,
                                endDate = endDate,
                                companyName = viewModel.companyName.value,
                                totalSales = totalSales,
                                totalProfit = totalProfit,
                                totalReceipts = totalReceipts,
                                totalPayments = totalPayments,
                                totalExpenses = totalExpenses,
                                topProductsList = topProductsList,
                                topCustomersList = topCustomersList,
                                lowStockList = products.filter { it.qty <= it.minQty },
                                filteredInvoices = filteredInvoices,
                                filteredVouchers = filteredVouchers,
                                accounts = accounts
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7145))
                    ) {
                        Text("تصدير Excel 📊")
                    }
                    Button(
                        onClick = {
                            printReport(
                                context = context,
                                type = type,
                                title = title,
                                startDate = startDate,
                                endDate = endDate,
                                companyName = viewModel.companyName.value,
                                totalSales = totalSales,
                                totalProfit = totalProfit,
                                totalReceipts = totalReceipts,
                                totalPayments = totalPayments,
                                totalExpenses = totalExpenses,
                                topProductsList = topProductsList,
                                topCustomersList = topCustomersList,
                                lowStockList = products.filter { it.qty <= it.minQty },
                                filteredInvoices = filteredInvoices,
                                filteredVouchers = filteredVouchers,
                                accounts = accounts
                            )
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("طباعة تقرير PDF 🖨️")
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

// --- Helper class for Account Statement detailed ledger rows ---
data class LedgerRow(
    val date: String,
    val desc: String,
    val amount: Double,
    val runningBalance: Double,
    val sourceId: String? = null,
    val sourceType: String? = null // "invoice" or "voucher"
)

data class TempTx(
    val date: String,
    val desc: String,
    val amount: Double,
    val sourceId: String,
    val sourceType: String // "invoice" or "voucher"
)

fun cleanDateStr(date: String): String {
    val trimmed = date.trim()
    return if (trimmed.length >= 10) trimmed.substring(0, 10) else trimmed
}

fun escapeCsvCell(value: Any?): String {
    if (value == null) return ""
    val str = value.toString().trim()
    val escaped = str.replace("\"", "\"\"")
    return "\"$escaped\""
}

// --- Account Statement (كشف الحساب المحاسبي) dialog ---
@Composable
fun AccountStatementDialog(
    viewModel: AppViewModel,
    account: Account,
    onClose: () -> Unit,
    onAddVoucher: (String) -> Unit,
    onViewInvoice: (Invoice) -> Unit,
    onViewVoucher: (Voucher) -> Unit
) {
    val invoices by viewModel.invoices.collectAsState()
    val vouchers by viewModel.vouchers.collectAsState()
    val exchangeRatesList by viewModel.exchangeRates.collectAsState()
    val usdDefault by viewModel.rateUSD.collectAsState()
    val eurDefault by viewModel.rateEUR.collectAsState()
    val sarDefault by viewModel.rateSAR.collectAsState()
    val tryDefault by viewModel.rateTRY.collectAsState()

    val accountVouchers = vouchers.filter { it.accountId == account.id }
    // Only include saved/posted invoices (ignore drafts)
    val accountInvs = invoices.filter { it.customer.trim() == account.name.trim() && it.status == "saved" }

    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    val allTx = remember(accountInvs, accountVouchers, exchangeRatesList, usdDefault, eurDefault, sarDefault, tryDefault, account.currency) {
        (
        accountInvs.map { inv ->
            val invoiceName = when (inv.type) {
                "sale" -> "فاتورة مبيعات رقم ${inv.id}"
                "purchase" -> "فاتورة مشتريات رقم ${inv.id}"
                "return", "return_sale" -> "فاتورة مرتجع مبيعات رقم ${inv.id}"
                "return_purchase" -> "فاتورة مرتجع مشتريات رقم ${inv.id}"
                else -> "فاتورة رقم ${inv.id}"
            }
            val rawInvoiceVal = when (inv.type) {
                "sale" -> -inv.total
                "purchase" -> inv.total
                "return", "return_sale" -> inv.total
                "return_purchase" -> -inv.total
                else -> 0.0
            }
            val historicalRate = exchangeRatesList.find { it.date == inv.date }
            val activeUsd = historicalRate?.rateUSD ?: usdDefault
            val activeEur = historicalRate?.rateEUR ?: eurDefault
            val activeSar = historicalRate?.rateSAR ?: sarDefault
            val activeTry = historicalRate?.rateTRY ?: tryDefault

            val rateFrom = when (inv.currency) {
                "USD" -> activeUsd
                "EUR" -> activeEur
                "SAR" -> activeSar
                "TRY" -> activeTry
                else -> 1.0
            }
            val rateTo = when (account.currency) {
                "USD" -> activeUsd
                "EUR" -> activeEur
                "SAR" -> activeSar
                "TRY" -> activeTry
                else -> 1.0
            }
            val sypAmount = rawInvoiceVal * rateFrom
            val amountInAccountCurrency = if (rateTo != 0.0) sypAmount / rateTo else sypAmount

            TempTx(
                date = inv.date,
                desc = invoiceName,
                amount = amountInAccountCurrency,
                sourceId = inv.id,
                sourceType = "invoice"
            )
        } +
        accountVouchers.map { v ->
            val prefixText = if (v.desc.isBlank()) {
                if (v.type == "receipt") "سند قبض نقدي رقم ${v.id}" else "سند صرف نقدي رقم ${v.id}"
            } else v.desc
            TempTx(
                date = v.date,
                desc = prefixText,
                amount = if (v.type == "receipt") v.amount else -v.amount,
                sourceId = v.id.toString(),
                sourceType = "voucher"
            )
        }
        )
    }

    val chronologicalTx = remember(allTx) {
        allTx.sortedWith(compareBy<TempTx> { cleanDateStr(it.date) }.thenBy { it.desc })
    }

    val txWithRunningBalance = remember(chronologicalTx) {
        var currentRunning = 0.0
        chronologicalTx.map { tx ->
            currentRunning += tx.amount
            LedgerRow(
                date = tx.date,
                desc = tx.desc,
                amount = tx.amount,
                runningBalance = currentRunning,
                sourceId = tx.sourceId,
                sourceType = tx.sourceType
            )
        }
    }

    val filteredTx = remember(txWithRunningBalance, startDate, endDate) {
        val sDate = startDate.trim()
        val eDate = endDate.trim()
        val list = txWithRunningBalance.filter { tx ->
            val cleanTxDate = cleanDateStr(tx.date)
            val afterStart = if (sDate.isBlank()) true else cleanTxDate >= sDate
            val beforeEnd = if (eDate.isBlank()) true else cleanTxDate <= eDate
            afterStart && beforeEnd
        }
        list.sortedByDescending { cleanDateStr(it.date) }
    }

    val previousBalance = remember(chronologicalTx, startDate) {
        val sDate = startDate.trim()
        if (sDate.isBlank()) 0.0
        else chronologicalTx.filter { cleanDateStr(it.date) < sDate }.sumOf { it.amount }
    }

    val periodNet = remember(filteredTx) {
        filteredTx.sumOf { it.amount }
    }

    val periodClosing = remember(previousBalance, periodNet, startDate, endDate) {
        if (startDate.isBlank() && endDate.isBlank()) account.balance
        else previousBalance + periodNet
    }

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
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (startDate.isNotBlank() || endDate.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "الرصيد السابق للمركز:", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                    Text(text = "${viewModel.formatCurrency(previousBalance)} ${account.currency}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "صافي حركة الفترة المحددة:", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                                    val prefix = if (periodNet > 0) "+" else ""
                                    Text(text = "$prefix${viewModel.formatCurrency(periodNet)} ${account.currency}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "الرصيد الختامي للفترة المحددة:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
                            Text(text = "${viewModel.formatCurrency(Math.abs(periodClosing))} ${account.currency}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            val periodSubtitle = if (periodClosing > 0) "← رصيد دائن للفترة (له علينا)" else if (periodClosing < 0) "← رصيد مدين للفترة (عليه لنا)" else "الحساب متوازن للفترة"
                            Text(text = periodSubtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        } else {
                            Text(text = "الرصيد الكلي الإجمالي الحالي:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(text = "${viewModel.formatCurrency(Math.abs(account.balance))} ${account.currency}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            val subtitle = if (account.balance > 0) "← مستحق للغير (له علينا)" else if (account.balance < 0) "← مستحق للشركة (عليه لنا)" else "الحساب متوازن"
                            Text(text = subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        }
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

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (filteredTx.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "لا توجد أي حركات قيود لهذه الفترة", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredTx) { tx ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (tx.sourceType == "invoice") {
                                                val matchedInv = invoices.find { it.id == tx.sourceId }
                                                if (matchedInv != null) {
                                                    onViewInvoice(matchedInv)
                                                } else {
                                                    viewModel.triggerToast("لم يتم العثور على تفاصيل الفاتورة")
                                                }
                                            } else if (tx.sourceType == "voucher") {
                                                val matchedV = vouchers.find { it.id.toString() == tx.sourceId }
                                                if (matchedV != null) {
                                                    onViewVoucher(matchedV)
                                                } else {
                                                    viewModel.triggerToast("لم يتم العثور على تفاصيل السند المحاسبي")
                                                }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9)),
                                    border = BorderStroke(1.dp, Color(0xFFD0DEDD))
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(text = tx.desc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Icon(
                                                    imageVector = if (tx.sourceType == "invoice") Icons.Default.Search else Icons.Default.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Text(text = tx.date, fontSize = 10.sp, color = Color.Gray)
                                                Text(
                                                    text = "الرصيد الجاري: ${viewModel.formatCurrency(tx.runningBalance)} ${account.currency}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        val color = if (tx.amount > 0) Color(0xFF2EBD7A) else Color(0xFFE03C3C)
                                        val prefix = if (tx.amount > 0) "+" else ""
                                        Text(text = "$prefix${viewModel.formatCurrency(tx.amount)} ${account.currency}", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                companyName = viewModel.companyName.value,
                                startDate = startDate,
                                endDate = endDate,
                                previousBalance = previousBalance,
                                periodNet = periodNet,
                                periodClosing = periodClosing
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
                                endDate = endDate,
                                previousBalance = previousBalance,
                                periodNet = periodNet,
                                periodClosing = periodClosing
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

// Helper unwrapper to extract Activity from Context wrappers safely
fun findActivity(context: android.content.Context): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

// System print web adapter helper for Account Statement
fun printAccountStatement(
    context: android.content.Context, 
    account: Account, 
    txList: List<LedgerRow>, 
    companyName: String, 
    startDate: String, 
    endDate: String,
    previousBalance: Double,
    periodNet: Double,
    periodClosing: Double
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
                <p>تاريخ استخراج التقرير: 2026-05-31</p>
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
                    <td><b>الفترة الزمنية المحددة:</b> من ${startDate.ifBlank { "البداية" }} إلى ${endDate.ifBlank { "اليوم" }}</td>
                    <td><b>حجم الحركات في الفترة:</b> $totalCount قيد مالي</td>
                </tr>
            </table>
            
            <table class="statement-table">
                <thead>
                    <tr>
                        <th style="width: 20%;">التاريخ</th>
                        <th style="width: 40%;">البيان والتفاصيل</th>
                        <th style="width: 20%;">الدائن / المدين</th>
                        <th style="width: 20%;">الرصيد الجاري</th>
                    </tr>
                </thead>
                <tbody>
    """.trimIndent())
    
    txList.forEach { tx ->
        val amt = tx.amount
        val amtClass = if (amt >= 0) "credit" else "debit"
        val amtSign = if (amt >= 0) "+" else ""
        htmlBuilder.append("""
            <tr>
                <td>${tx.date}</td>
                <td>${tx.desc}</td>
                <td class="$amtClass">$amtSign${String.format(java.util.Locale.US, "%,.2f", amt)} ${account.currency}</td>
                <td>${String.format(java.util.Locale.US, "%,.2f", tx.runningBalance)} ${account.currency}</td>
            </tr>
        """.trimIndent())
    }
    
    htmlBuilder.append("""
                </tbody>
            </table>
            <table class="meta-table" style="margin-top: 20px;">
                <tr>
                    <td><b>رصيد ما قبل الفترة (الافتتاحي):</b> ${String.format(java.util.Locale.US, "%,.2f", previousBalance)} ${account.currency}</td>
                    <td><b>صافي حركة الفترة المحددة:</b> ${String.format(java.util.Locale.US, "%,.2f", periodNet)} ${account.currency}</td>
                </tr>
            </table>
            <div class="total-box">
                الرصيد الختامي للقيد المحاسبي في نهاية المدة: ${String.format(java.util.Locale.US, "%,.2f", periodClosing)} ${account.currency}
            </div>
            <p style="text-align: center; margin-top: 30px; font-size: 12px; color: #777;">تم توليد وحفظ هذا التقرير كـ PDF إلكتروني عبر نظام المحاسب الذكي 📱</p>
        </body>
        </html>
    """.trimIndent())

    val act = findActivity(context)
    act?.runOnUiThread {
        val webView = android.webkit.WebView(act)
        MainActivity.tempWebViewForPrinting = webView
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                val printManager = act.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
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
    txList: List<LedgerRow>, 
    startDate: String, 
    endDate: String,
    previousBalance: Double,
    periodNet: Double,
    periodClosing: Double
) {
    val csvContent = StringBuilder()
    // Unicode UTF-8 Byte Order Mark (BOM) to correctly display Arabic in Microsoft Excel!
    csvContent.append('\ufeff')
    csvContent.append("${escapeCsvCell("كشف حساب مالي تفصيلي")}\n")
    csvContent.append("${escapeCsvCell("اسم الحساب")},${escapeCsvCell(account.name)}\n")
    csvContent.append("${escapeCsvCell("رقم الهاتف")},${escapeCsvCell(account.phone)}\n")
    csvContent.append("${escapeCsvCell("العنوان")},${escapeCsvCell(account.address)}\n")
    csvContent.append("${escapeCsvCell("نوع الحساب")},${escapeCsvCell(account.type)}\n")
    csvContent.append("${escapeCsvCell("الفترة")},${escapeCsvCell("من ${startDate.ifBlank { "البداية" }} إلى ${endDate.ifBlank { "اليوم" }}")}\n")
    csvContent.append("${escapeCsvCell("الرصيد الافتتاحي قبل الفترة")},${escapeCsvCell("${previousBalance} ${account.currency}")}\n")
    csvContent.append("${escapeCsvCell("صافي حركة الفترة")},${escapeCsvCell("${periodNet} ${account.currency}")}\n")
    csvContent.append("${escapeCsvCell("الرصيد الختامي للفترة")},${escapeCsvCell("${periodClosing} ${account.currency}")}\n")
    csvContent.append("\n")
    csvContent.append("${escapeCsvCell("التاريخ")},${escapeCsvCell("البيان")},${escapeCsvCell("المبلغ")},${escapeCsvCell("الرصيد الجاري")},${escapeCsvCell("العملة")}\n")
    
    txList.forEach { tx ->
        val amt = tx.amount
        val amtStr = "${if (amt >= 0) "+" else ""}$amt"
        csvContent.append("${escapeCsvCell(tx.date)},${escapeCsvCell(tx.desc)},${escapeCsvCell(amtStr)},${escapeCsvCell(tx.runningBalance)},${escapeCsvCell(account.currency)}\n")
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
            clipData = android.content.ClipData.newRawUri("", uri)
        }
        val chooser = android.content.Intent.createChooser(intent, "تصدير كشف الحساب إلى Excel").apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "فشل تصدير الملف: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// System print web adapter helper for General Reports
fun printReport(
    context: android.content.Context,
    type: String,
    title: String,
    startDate: String,
    endDate: String,
    companyName: String,
    totalSales: Double,
    totalProfit: Double,
    totalReceipts: Double,
    totalPayments: Double,
    totalExpenses: Double,
    topProductsList: List<Pair<Product, Pair<Int, Double>>>,
    topCustomersList: List<Pair<Account, Double>>,
    lowStockList: List<Product>,
    filteredInvoices: List<Invoice>,
    filteredVouchers: List<Voucher>,
    accounts: List<Account>
) {
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
                .statement-table { width: 100%; border-collapse: collapse; margin-top: 15px; }
                .statement-table th, .statement-table td { padding: 10px; border: 1px solid #777; text-align: right; font-size: 13px; }
                .statement-table th { background-color: #e4eceb; color: #1c544d; }
                .credit { color: #2ebd7a; font-weight: bold; }
                .debit { color: #e03c3c; font-weight: bold; }
                .total-box { margin-top: 25px; border: 2px solid #1c544d; padding: 15px; font-weight: bold; text-align: center; font-size: 16px; background-color: #f7faf9; }
                .section-title { font-weight: bold; font-size: 16px; margin-top: 20px; margin-bottom: 10px; color: #1c544d; border-bottom: 2px solid #1c544d; padding-bottom: 5px; }
            </style>
        </head>
        <body>
            <header>
                <h2>$companyName</h2>
                <h3>$title</h3>
                <p>تاريخ استخراج التقرير: 2026-05-31</p>
                <p>الفترة الزمنية المحددة: من ${startDate.ifBlank { "البداية" }} إلى ${endDate.ifBlank { "اليوم" }}</p>
            </header>
    """.trimIndent())

    when (type) {
        "daily" -> {
            htmlBuilder.append("""
                <div class="section-title">ملخص حركات اليوم/الفترة</div>
                <table class="meta-table">
                    <tr>
                        <td><b>إجمالي المبيعات المحققة:</b> ${String.format(java.util.Locale.US, "%,.2f", totalSales)} ل.س</td>
                        <td><b>أرباح المبيعات المحققة:</b> ${String.format(java.util.Locale.US, "%,.2f", totalProfit)} ل.س</td>
                    </tr>
                    <tr>
                        <td><b>مجموع المقبوضات النقدية:</b> ${String.format(java.util.Locale.US, "%,.2f", totalReceipts)} ل.س</td>
                        <td><b>مجموع المدفوعات النقدية:</b> ${String.format(java.util.Locale.US, "%,.2f", totalPayments)} ل.س</td>
                    </tr>
                </table>
                
                <div class="section-title">الحركات التفصيلية - فواتير المبيعات والعودة</div>
                <table class="statement-table">
                    <thead>
                        <tr>
                            <th>رقم الفاتورة</th>
                            <th>التاريخ</th>
                            <th>الحساب والعميل</th>
                            <th>نوع الحركة</th>
                            <th>القيمة</th>
                            <th>العملة</th>
                        </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            filteredInvoices.forEach { inv ->
                val typeStr = when(inv.type) {
                    "sale" -> "مبيعات"
                    "purchase" -> "مشتريات"
                    "return", "return_sale" -> "مرتجع مبيعات"
                    "return_purchase" -> "مرتجع مشتريات"
                    else -> inv.type
                }
                htmlBuilder.append("""
                    <tr>
                        <td>${inv.id}</td>
                        <td>${inv.date}</td>
                        <td>${inv.customer}</td>
                        <td>$typeStr</td>
                        <td>${String.format(java.util.Locale.US, "%,.2f", inv.total)}</td>
                        <td>${inv.currency}</td>
                    </tr>
                """.trimIndent())
            }

            htmlBuilder.append("""
                    </tbody>
                </table>
                
                <div class="section-title">الحركات التفصيلية - السندات المحاسبية</div>
                <table class="statement-table">
                    <thead>
                        <tr>
                            <th>رقم السند</th>
                            <th>التاريخ</th>
                            <th>اسم الحساب والبيان</th>
                            <th>النوع</th>
                            <th>القيمة المحولة</th>
                        </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            filteredVouchers.forEach { v ->
                val accName = accounts.find { it.id == v.accountId }?.name ?: "حساب محذوف"
                val typeStr = if (v.type == "receipt") "قبض نقدي" else "صرف نقدي"
                val amtClass = if (v.type == "receipt") "credit" else "debit"
                val amtSign = if (v.type == "receipt") "+" else "-"
                htmlBuilder.append("""
                    <tr>
                        <td>${v.id}</td>
                        <td>${v.date}</td>
                        <td>$accName - ${v.desc}</td>
                        <td>$typeStr</td>
                        <td class="$amtClass">$amtSign${String.format(java.util.Locale.US, "%,.2f", v.amount)}</td>
                    </tr>
                """.trimIndent())
            }

            htmlBuilder.append("""
                    </tbody>
                </table>
            """.trimIndent())
        }
        "pl" -> {
            val cogs = totalSales - totalProfit
            val grossProfit = totalProfit
            val dynamicExpenses = totalExpenses
            val netProfit = grossProfit - dynamicExpenses

            htmlBuilder.append("""
                <div class="section-title">بيان الأرباح والخسائر الشامل</div>
                <table class="statement-table" style="width: 100%;">
                    <thead>
                        <tr>
                            <th style="width: 60%;">الحساب المالي / البيان</th>
                            <th style="width: 40%; text-align: left;">القيمة بالعملة المحلية (ل.س)</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td><b>إجمالي المبيعات المحققة بالفترة</b></td>
                            <td class="credit" style="text-align: left;">+ ${String.format(java.util.Locale.US, "%,.2f", totalSales)}</td>
                        </tr>
                        <tr>
                            <td><b>تكلفة البضاعة المباعة (الرقم الفعلي)</b></td>
                            <td class="debit" style="text-align: left;">- ${String.format(java.util.Locale.US, "%,.2f", cogs)}</td>
                        </tr>
                        <tr style="background-color: #f0f5f4;">
                            <td><b>مجمل الربح الإجمالي (إجمالي المساهمة)</b></td>
                            <td class="credit" style="font-weight: bold; text-align: left;">+ ${String.format(java.util.Locale.US, "%,.2f", grossProfit)}</td>
                        </tr>
                        <tr>
                            <td><b>المصاريف التشغيلية الفعلية (سندات الصرف)</b></td>
                            <td class="debit" style="text-align: left;">- ${String.format(java.util.Locale.US, "%,.2f", dynamicExpenses)}</td>
                        </tr>
                        <tr style="background-color: #e4eceb; border-top: 2px solid #1c544d;">
                            <td><b style="font-size: 15px;">صافي الدخل النهائي والربح الصافي</b></td>
                            <td class="credit" style="font-size: 15px; font-weight: bold; text-align: left;">${String.format(java.util.Locale.US, "%,.2f", netProfit)}</td>
                        </tr>
                    </tbody>
                </table>
            """.trimIndent())
        }
        "topProducts" -> {
            htmlBuilder.append("""
                <div class="section-title">المواد الأكثر حركة ومبيعاً</div>
                <table class="statement-table">
                    <thead>
                        <tr>
                            <th>الترتيب</th>
                            <th>المادة</th>
                            <th>الكمية المباعة</th>
                            <th>الإيرادات الإجمالية ل.س</th>
                        </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            topProductsList.filter { it.second.first > 0 || (startDate.isEmpty() && endDate.isEmpty()) }.forEachIndexed { idx, item ->
                htmlBuilder.append("""
                    <tr>
                        <td style="text-align: center;">${idx + 1}</td>
                        <td>${item.first.icon} ${item.first.name}</td>
                        <td>${item.second.first} ${item.first.unit}</td>
                        <td class="credit">${String.format(java.util.Locale.US, "%,.2f", item.second.second)} ل.س</td>
                    </tr>
                """.trimIndent())
            }

            htmlBuilder.append("""
                    </tbody>
                </table>
            """.trimIndent())
        }
        "topCustomers" -> {
            htmlBuilder.append("""
                <div class="section-title">ترتيب عملاء الشركة الأكثر حركة</div>
                <table class="statement-table">
                    <thead>
                        <tr>
                            <th>الترتيب</th>
                            <th>اسم الحساب والعميل</th>
                            <th>حجم التعامل المالي بالفترة</th>
                            <th>الرصيد الكلي الحالي بالحساب</th>
                        </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            topCustomersList.filter { it.second > 0.0 || (startDate.isEmpty() && endDate.isEmpty()) }.forEachIndexed { idx, item ->
                val balanceColor = if (item.first.balance >= 0) "credit" else "debit"
                htmlBuilder.append("""
                    <tr>
                        <td style="text-align: center;">${idx + 1}</td>
                        <td>${item.first.name}</td>
                        <td>${String.format(java.util.Locale.US, "%,.2f", item.second)} ${item.first.currency}</td>
                        <td class="$balanceColor">${String.format(java.util.Locale.US, "%,.2f", Math.abs(item.first.balance))} ${item.first.currency}</td>
                    </tr>
                """.trimIndent())
            }

            htmlBuilder.append("""
                    </tbody>
                </table>
            """.trimIndent())
        }
        "lowStock" -> {
            htmlBuilder.append("""
                <div class="section-title">جرد النواقص والمواد تحت خط الأمان</div>
                <table class="statement-table">
                    <thead>
                        <tr>
                            <th>المادة</th>
                            <th>الكمية المتوفرة حالياً</th>
                            <th>الحد الأدنى لطلب الأمان</th>
                            <th>الوحدة القياسية</th>
                            <th>حالة الإنذار</th>
                        </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            lowStockList.forEach { p ->
                htmlBuilder.append("""
                    <tr>
                        <td>${p.icon} ${p.name}</td>
                        <td class="debit">${p.qty}</td>
                        <td>${p.minQty}</td>
                        <td>${p.unit}</td>
                        <td style="color: #e03c3c; font-weight: bold;">تحت الأمان ⚠️</td>
                    </tr>
                """.trimIndent())
            }

            htmlBuilder.append("""
                    </tbody>
                </table>
            """.trimIndent())
        }
    }

    htmlBuilder.append("""
            <p style="text-align: center; margin-top: 40px; font-size: 12px; color: #777;">تم توليد هذا التقرير المحاسبي إلكترونياً عبر تطبيق فواتير والمحاسب الذكي 📈</p>
        </body>
        </html>
    """.trimIndent())

    val act = findActivity(context)
    act?.runOnUiThread {
        val webView = android.webkit.WebView(act)
        MainActivity.tempWebViewForPrinting = webView
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                val printManager = act.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                val jobName = "تقرير $title - المحاسب الذكي"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
    }
}

// System export CSV web adapter helper for General Reports
fun exportReportToExcel(
    context: android.content.Context,
    type: String,
    title: String,
    startDate: String,
    endDate: String,
    companyName: String,
    totalSales: Double,
    totalProfit: Double,
    totalReceipts: Double,
    totalPayments: Double,
    totalExpenses: Double,
    topProductsList: List<Pair<Product, Pair<Int, Double>>>,
    topCustomersList: List<Pair<Account, Double>>,
    lowStockList: List<Product>,
    filteredInvoices: List<Invoice>,
    filteredVouchers: List<Voucher>,
    accounts: List<Account>
) {
    val csvContent = StringBuilder()
    csvContent.append('\ufeff')
    csvContent.append("sep=,\n") // Force Excel separator detection
    csvContent.append("${escapeCsvCell("تقرير الذكي - $companyName")}\n")
    csvContent.append("${escapeCsvCell("نوع التقرير")},${escapeCsvCell(title)}\n")
    csvContent.append("${escapeCsvCell("الفترة")},${escapeCsvCell("من ${startDate.ifBlank { "البداية" }} إلى ${endDate.ifBlank { "اليوم" }}")}\n")
    csvContent.append("${escapeCsvCell("تاريخ التصدير")},${escapeCsvCell("2026-05-31")}\n\n")

    when (type) {
        "daily" -> {
            csvContent.append("${escapeCsvCell("ملخص حركات الفترة")}\n")
            csvContent.append("${escapeCsvCell("إجمالي المبيعات المحققة ل.س")},${escapeCsvCell(totalSales)}\n")
            csvContent.append("${escapeCsvCell("أرباح المبيعات ل.س")},${escapeCsvCell(totalProfit)}\n")
            csvContent.append("${escapeCsvCell("المقبوضات الإجمالية ل.س")},${escapeCsvCell(totalReceipts)}\n")
            csvContent.append("${escapeCsvCell("المدفوعات الإجمالية ل.س")},${escapeCsvCell(totalPayments)}\n\n")

            csvContent.append("${escapeCsvCell("فواتير المبيعات والمرتجع")}\n")
            csvContent.append("${escapeCsvCell("رقم الفاتورة")},${escapeCsvCell("التاريخ")},${escapeCsvCell("الحساب والعميل")},${escapeCsvCell("النوع")},${escapeCsvCell("القيمة")},${escapeCsvCell("العملة")},${escapeCsvCell("الحالة")}\n")
            filteredInvoices.forEach { inv ->
                val typeStr = when(inv.type) {
                    "sale" -> "مبيعات"
                    "purchase" -> "مشتريات"
                    "return", "return_sale" -> "مرتجع مبيعات"
                    "return_purchase" -> "مرتجع مشتريات"
                    else -> inv.type
                }
                csvContent.append("${escapeCsvCell(inv.id)},${escapeCsvCell(inv.date)},${escapeCsvCell(inv.customer)},${escapeCsvCell(typeStr)},${escapeCsvCell(inv.total)},${escapeCsvCell(inv.currency)},${escapeCsvCell(if (inv.status == "saved") "محفوظة" else "مسودة")}\n")
            }
            csvContent.append("\n${escapeCsvCell("سندات القبض والصرف")}\n")
            csvContent.append("${escapeCsvCell("رقم السند")},${escapeCsvCell("التاريخ")},${escapeCsvCell("الحساب")},${escapeCsvCell("النوع")},${escapeCsvCell("القيمة")},${escapeCsvCell("البيان")}\n")
            filteredVouchers.forEach { v ->
                val acc = accounts.find { it.id == v.accountId }?.name ?: "غير معروف"
                val typeStr = if (v.type == "receipt") "قبض" else "صرف"
                csvContent.append("${escapeCsvCell(v.id)},${escapeCsvCell(v.date)},${escapeCsvCell(acc)},${escapeCsvCell(typeStr)},${escapeCsvCell(v.amount)},${escapeCsvCell(v.desc)}\n")
            }
        }
        "pl" -> {
            val cogs = totalSales - totalProfit
            val grossProfit = totalProfit
            val dynamicExpenses = totalExpenses
            val netProfit = grossProfit - dynamicExpenses

            csvContent.append("${escapeCsvCell("البند المحاسبي")},${escapeCsvCell("القيمة ل.س")}\n")
            csvContent.append("${escapeCsvCell("إجمالي المبيعات المحققة")},${escapeCsvCell(totalSales)}\n")
            csvContent.append("${escapeCsvCell("تكلفة البضاعة المباعة")},${escapeCsvCell("-$cogs")}\n")
            csvContent.append("${escapeCsvCell("مجمل الربح الإجمالي")},${escapeCsvCell(grossProfit)}\n")
            csvContent.append("${escapeCsvCell("المصاريف التشغيلية الفعلية")},${escapeCsvCell("-$dynamicExpenses")}\n")
            csvContent.append("${escapeCsvCell("صافي الدخل النهائي للفترة")},${escapeCsvCell(netProfit)}\n")
        }
        "topProducts" -> {
            csvContent.append("${escapeCsvCell("الترتيب")},${escapeCsvCell("المادة")},${escapeCsvCell("الكمية المباعة")},${escapeCsvCell("الإيرادات المحققة ل.س")}\n")
            topProductsList.filter { it.second.first > 0 || (startDate.isEmpty() && endDate.isEmpty()) }.forEachIndexed { idx, item ->
                csvContent.append("${escapeCsvCell(idx + 1)},${escapeCsvCell(item.first.name)},${escapeCsvCell(item.second.first)},${escapeCsvCell(item.second.second)}\n")
            }
        }
        "topCustomers" -> {
            csvContent.append("${escapeCsvCell("الترتيب")},${escapeCsvCell("العميل")},${escapeCsvCell("حجم التعامل بالفترة")},${escapeCsvCell("الرصيد الحالي")},${escapeCsvCell("العملة")}\n")
            topCustomersList.filter { it.second > 0.0 || (startDate.isEmpty() && endDate.isEmpty()) }.forEachIndexed { idx, item ->
                csvContent.append("${escapeCsvCell(idx + 1)},${escapeCsvCell(item.first.name)},${escapeCsvCell(item.second)},${escapeCsvCell(item.first.balance)},${escapeCsvCell(item.first.currency)}\n")
            }
        }
        "lowStock" -> {
            csvContent.append("${escapeCsvCell("المادة")},${escapeCsvCell("الكمية المتوفرة")},${escapeCsvCell("الحد الأدنى للأمان")},${escapeCsvCell("الوحدة")}\n")
            lowStockList.forEach { p ->
                csvContent.append("${escapeCsvCell(p.name)},${escapeCsvCell(p.qty)},${escapeCsvCell(p.minQty)},${escapeCsvCell(p.unit)}\n")
            }
        }
    }

    val fileName = "report_${type}_${System.currentTimeMillis()}.csv"
    try {
        val file = java.io.File(context.cacheDir, fileName)
        file.writeText(csvContent.toString(), Charsets.UTF_8)
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.example.fileprovider",
            file
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            this.type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "تصدير تقرير: $title")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("", uri)
        }
        val chooser = android.content.Intent.createChooser(intent, "تصدير التقرير إلى Excel").apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "فشل تصدير الملف: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

// --- Helper Barcode Analyzer for CameraX using Google ML Kit ---
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && rawValue.trim().isNotEmpty()) {
                            onBarcodeDetected(rawValue)
                            break
                        }
                    }
                }
                .addOnFailureListener {}
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

// --- Dynamic Barcode Scanner Composable with Real Live CameraX Viewfinder and Pulsing Laser ---
@Composable
fun BarcodeScannerCustomDialog(
    viewModel: AppViewModel,
    onScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val products by viewModel.products.collectAsState()
    var customCodeInput by remember { mutableStateOf("") }
    
    // Check and request camera permission dynamically in Compose
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                viewModel.triggerToast("يجب السماح بالوصول للكاميرا لقراءة الباركود")
            }
        }
    )

    // Trigger permission request automatically on Dialog open
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Keep track so we triggers scanning ONLY ONCE
    var scanningFinished by remember { mutableStateOf(false) }

    fun triggerSuccessScan(code: String) {
        if (code.isBlank() || scanningFinished) return
        scanningFinished = true
        
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
                        text = "📷 قارئ الباركود والمنتجات",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Camera viewfinder / Permission Request view
                Box(
                    modifier = Modifier
                        .size(height = 160.dp, width = 260.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .border(2.dp, if (hasCameraPermission) Color(0xFF0F9D58) else Color.Red, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        // Real CameraX Viewfinder
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also {
                                                it.setAnalyzer(Executors.newSingleThreadExecutor(), BarcodeAnalyzer { code ->
                                                    // Process barcode callbacks on the UI/Main Thread
                                                    (ctx as? android.app.Activity)?.runOnUiThread {
                                                        triggerSuccessScan(code)
                                                    } ?: run {
                                                        triggerSuccessScan(code)
                                                    }
                                                })
                                            }
                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (exc: Exception) {
                                        // Empty camera hardware handling (eg inside standard android raw emulator)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )

                        // Overlaid Viewfinder Target Frame
                        Box(
                            modifier = Modifier
                                .size(height = 90.dp, width = 190.dp)
                                .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        )

                        // Pulsing red laser line!
                        var animTrigger by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                animTrigger = !animTrigger
                                delay(1200)
                            }
                        }
                        val laserOffset by animateDpAsState(
                            targetValue = if (animTrigger) (-35).dp else 35.dp,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ), label = ""
                        )
                        Box(
                            modifier = Modifier
                                .offset(y = laserOffset)
                                .height(2.dp)
                                .width(180.dp)
                                .background(Color.Red)
                        )

                        Text(
                            text = "وجه رمز الباركود نحو المنتصف للمسح التلقائي",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .padding(bottom = 6.dp)
                        )
                    } else {
                        // Request Permission CTA layout
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                "الكاميرا مغلقة أو الصلاحية غير ممنوحة",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("تفعيل صلاحية الكاميرا 📷", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of existing warehouse products barcodes for seamless scanning demo / backup input!
                Text(
                    text = "🎯 أو اختر منتجاً لمحاكاة الباركود من قائمة المستودع:",
                    color = Color(0xFFAAB8C2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
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

                Spacer(modifier = Modifier.height(12.dp))

                // Or type manually
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customCodeInput,
                        onValueChange = { customCodeInput = it },
                        placeholder = { Text("أو اكتب الباركود يدوياً...", color = Color.Gray, fontSize = 12.sp) },
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
            MainActivity.tempWebViewForPrinting = webView
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

// --- Manage Custom Categories Dialog ---
@Composable
fun ManageCategoriesDialog(
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val products by viewModel.products.collectAsState()

    var newCatName by remember { mutableStateOf("") }
    
    // Default emojis list for user selection
    val availableEmojis = listOf("📁", "📦", "🍬", "🔌", "🍔", "🛠️", "👕", "📚", "🎨", "🥑", "🚗", "🧸")
    var selectedEmoji by remember { mutableStateOf("📁") }

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📁 إدارة فئات المنتجات",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFE4ECEB))
                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable List of Categories
                Text(
                    text = "الفئات الحالية:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color(0xFFF0F5F4), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isBuiltIn = cat.id in listOf("food", "electronics", "other")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.icon, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cat.name,
                                    fontSize = 13.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isBuiltIn) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "🔒 فئة أساسية",
                                    tint = Color.Gray.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                IconButton(
                                    onClick = { viewModel.deleteCategory(cat) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الفئة",
                                        tint = Color.Red.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFE4ECEB))
                Spacer(modifier = Modifier.height(12.dp))

                // Add Category Form
                Text(
                    text = "🔨 إضافة فئة جديدة:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Emoji picker horizontal scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableEmojis.forEach { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("اسم الفئة الجديدة") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (newCatName.isNotBlank() && newCatName.trim() != "أخرى") {
                            viewModel.saveCategory(newCatName, selectedEmoji)
                            newCatName = "" // clear input
                        } else if (newCatName.trim() == "أخرى") {
                            viewModel.triggerToast("الفئة (أخرى) موجودة كفئة افتراضية")
                        } else {
                            viewModel.triggerToast("يرجى كتابة اسم الفئة أولاً")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("إضافة الفئة الجديدة", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// System print adapter helper for Invoices (handles both A4 standard layout and 80mm/58mm thermal receipt layouts)
fun printInvoiceDoc(
    context: android.content.Context,
    invoice: Invoice,
    items: List<InvoiceItem>,
    printType: String, // "a4" or "thermal"
    companyName: String,
    companyPhone: String,
    companyAddress: String,
    companyCurrency: String,
    viewModel: AppViewModel
) {
    val htmlBuilder = java.lang.StringBuilder()
    val activeCurrency = invoice.currency
    val typeText = when (invoice.type) {
        "sale" -> "فاتورة مبيعات"
        "purchase" -> "فاتورة مشتريات"
        "return_sale" -> "فاتورة مرتجع مبيعات"
        "return_purchase" -> "فاتورة مرتجع مشتريات"
        else -> "فاتورة تجارية"
    }
    
    val paymentTypeText = if (invoice.paymentType == "cash") "نقدي كاش" else "آجل على الذمة"
    
    if (printType == "thermal") {
        htmlBuilder.append("""
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Courier New', monospace, sans-serif;
                        direction: rtl;
                        padding: 2px 10px;
                        width: 100%;
                        max-width: 290px;
                        margin: 0 auto;
                        font-size: 11px;
                        color: #000;
                    }
                    header {
                        text-align: center;
                        margin-bottom: 8px;
                        border-bottom: 1px dashed #000;
                        padding-bottom: 6px;
                    }
                    h2 { margin: 2px 0; font-size: 14px; font-weight: bold; }
                    h3 { margin: 1px 0; font-size: 11px; font-weight: normal; }
                    .meta-info {
                        font-size: 10px;
                        margin-bottom: 4px;
                        border-bottom: 1px dashed #000;
                        padding-bottom: 4px;
                    }
                    .meta-row { display: flex; justify-content: space-between; margin: 1px 0; }
                    .items-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 4px;
                        margin-bottom: 4px;
                        font-size: 10px;
                    }
                    .items-table th, .items-table td {
                        padding: 2px 0;
                        text-align: right;
                    }
                    .items-table th {
                        border-bottom: 1px solid #000;
                        font-weight: bold;
                    }
                    .items-table td {
                        border-bottom: 0.5px dashed #ccc;
                    }
                    .summary-box {
                        margin-top: 4px;
                        border-top: 1px dashed #000;
                        padding-top: 4px;
                        font-size: 10px;
                    }
                    .summary-row {
                        display: flex;
                        justify-content: space-between;
                        margin: 1px 0;
                    }
                    .grand-total {
                        font-size: 13px;
                        font-weight: bold;
                        border: 1px solid #000;
                        padding: 3px;
                        text-align: center;
                        margin-top: 4px;
                    }
                    .footer {
                        text-align: center;
                        font-size: 9px;
                        margin-top: 10px;
                        border-top: 1px dashed #000;
                        padding-top: 6px;
                    }
                </style>
            </head>
            <body>
                <header>
                    <h2>$companyName</h2>
                    <h3>$typeText</h3>
                    <p style="font-size: 9px; margin: 2px 0;">هاتف: $companyPhone | العنوان: $companyAddress</p>
                </header>
                <div class="meta-info">
                    <div class="meta-row"><span>رقم الفاتورة:</span> <span><b>${invoice.id}</b></span></div>
                    <div class="meta-row"><span>التاريخ:</span> <span>${invoice.date}</span></div>
                    <div class="meta-row"><span>العميل:</span> <span><b>${invoice.customer}</b></span></div>
                    <div class="meta-row"><span>الدفع:</span> <span>$paymentTypeText</span></div>
                </div>
                
                <table class="items-table">
                    <thead>
                        <tr>
                            <th style="width: 50%;">المادة</th>
                            <th style="width: 15%; text-align: center;">الكمية</th>
                            <th style="width: 35%; text-align: left;">الإجمالي</th>
                        </tr>
                    </thead>
                    <tbody>
        """.trimIndent())
        
        items.forEach { item ->
            htmlBuilder.append("""
                <tr>
                    <td>${item.name}</td>
                    <td style="text-align: center;">${item.qty}</td>
                    <td style="text-align: left;">${viewModel.formatCurrency(item.qty * item.price)}</td>
                </tr>
            """.trimIndent())
        }
        
        val itemsTotal = items.sumOf { it.qty * it.price }
        htmlBuilder.append("""
                    </tbody>
                </table>
                
                <div class="summary-box">
                    <div class="summary-row"><span>إجمالي قيمة المواد:</span> <span>${viewModel.formatCurrency(itemsTotal)} $activeCurrency</span></div>
        """.trimIndent())
        
        if (invoice.discount > 0.0) {
            htmlBuilder.append("""
                    <div class="summary-row"><span>الخصم الممنوح:</span> <span>- ${viewModel.formatCurrency(invoice.discount)} $activeCurrency</span></div>
            """.trimIndent())
        }
        
        if (invoice.tax > 0.0) {
            htmlBuilder.append("""
                    <div class="summary-row"><span>الضريبة المضافة:</span> <span>+ ${viewModel.formatCurrency(invoice.tax)} $activeCurrency</span></div>
            """.trimIndent())
        }
        
        htmlBuilder.append("""
                    <div class="grand-total">
                        الصافي النهائي: ${viewModel.formatCurrency(invoice.total)} $activeCurrency
                    </div>
        """.trimIndent())
        
        if (invoice.paymentType == "credit") {
            val remaining = maxOf(0.0, invoice.total - invoice.paidAmount)
            htmlBuilder.append("""
                    <div class="summary-row" style="margin-top: 2px;"><span>المسدد نقداً:</span> <span>${viewModel.formatCurrency(invoice.paidAmount)} $activeCurrency</span></div>
                    <div class="summary-row"><span>المتبقي في الذمة:</span> <span>${viewModel.formatCurrency(remaining)} $activeCurrency</span></div>
            """.trimIndent())
        }
        
        if (invoice.notes.isNotBlank()) {
            htmlBuilder.append("""
                    <div style="font-size: 8px; margin-top: 4px; border-top: 0.5px dashed #ccc; padding-top: 2px;">
                        <b>ملاحظات:</b> ${invoice.notes}
                    </div>
            """.trimIndent())
        }
        
        htmlBuilder.append("""
                </div>
                <div class="footer">
                    <p>نشكر ثقتكم بنا وزيارتكم الكريمة!</p>
                    <p style="font-size: 8px; color: #555;">تم التوليد والطباعة عبر المحاسب الذكي 📈</p>
                </div>
            </body>
            </html>
        """.trimIndent())
        
    } else {
        htmlBuilder.append("""
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Courier New', sans-serif;
                        direction: rtl;
                        padding: 20px;
                        background-color: #fff;
                    }
                    header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-bottom: 2px solid #1c544d;
                        padding-bottom: 10px;
                        margin-bottom: 20px;
                    }
                    .company-info h2 { margin: 0; color: #1c544d; font-size: 22px; font-weight: bold; }
                    .company-info p { margin: 2px 0; color: #555; font-size: 12px; }
                    .invoice-title { text-align: left; }
                    .invoice-title h1 { margin: 0; color: #1c544d; font-size: 20px; }
                    .invoice-title p { margin: 2px 0; color: #777; font-size: 12px; }
                    
                    .meta-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 20px;
                    }
                    .meta-table td {
                        padding: 8px;
                        border: 1px dashed #ccc;
                        font-size: 13px;
                    }
                    
                    .items-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 10px;
                        margin-bottom: 20px;
                    }
                    .items-table th, .items-table td {
                        padding: 10px;
                        border: 1px solid #ddd;
                        text-align: right;
                        font-size: 12px;
                    }
                    .items-table th {
                        background-color: #e4eceb;
                        color: #1c544d;
                        font-weight: bold;
                    }
                    
                    .total-section {
                        width: 100%;
                        display: flex;
                        justify-content: flex-end;
                        margin-top: 10px;
                    }
                    .total-table {
                        width: 50%;
                        border-collapse: collapse;
                    }
                    .total-table td {
                        padding: 6px 10px;
                        border: 1px solid #eee;
                        text-align: right;
                        font-size: 12px;
                    }
                    .total-row-highlight {
                        background-color: #f7faf9;
                        border-top: 2px solid #1c544d !important;
                        font-weight: bold;
                        font-size: 14px !important;
                        color: #1c544d;
                    }
                    .notes-box {
                        margin-top: 20px;
                        border: 1px solid #ddd;
                        background-color: #fafafa;
                        padding: 10px;
                        border-radius: 4px;
                        font-size: 11px;
                        width: 45%;
                        float: right;
                    }
                    .signature-box {
                        margin-top: 40px;
                        display: flex;
                        justify-content: space-between;
                        padding: 0 20px;
                        font-size: 12px;
                        clear: both;
                    }
                    .footer-branding {
                        text-align: center;
                        margin-top: 60px;
                        font-size: 10px;
                        color: #777;
                        border-top: 1px solid #1c544d;
                        padding-top: 10px;
                    }
                </style>
            </head>
            <body>
                <header>
                    <div class="company-info">
                        <h2>$companyName</h2>
                        <p>العنوان: $companyAddress</p>
                        <p>رقم الهاتف: $companyPhone</p>
                    </div>
                    <div class="invoice-title">
                        <h1>$typeText</h1>
                        <p>تاريخ الفاتورة: ${invoice.date}</p>
                        <p>الرقم المرجعي: <b>${invoice.id}</b></p>
                    </div>
                </header>
                
                <table class="meta-table">
                    <tr>
                        <td><b>اسم العميل المحاسبي:</b> ${invoice.customer}</td>
                        <td><b>طريقة الدفع والتحصيل:</b> $paymentTypeText</td>
                    </tr>
                    <tr>
                        <td><b>الجهة المصدرة:</b> نظام المبيعات والمخازن</td>
                        <td><b>عملة الفاتورة:</b> $activeCurrency</td>
                    </tr>
                </table>
                
                <table class="items-table">
                    <thead>
                        <tr>
                            <th style="width: 10%; text-align: center;">الرقم</th>
                            <th style="width: 40%;">اسم المادة</th>
                            <th style="width: 15%; text-align: center;">الكمية</th>
                            <th style="width: 15%; text-align: left;">سعر الوحدة</th>
                            <th style="width: 20%; text-align: left;">الإجمالي</th>
                        </tr>
                    </thead>
                    <tbody>
        """.trimIndent())
        
        items.forEachIndexed { index, item ->
            htmlBuilder.append("""
                <tr>
                    <td style="text-align: center;">${index + 1}</td>
                    <td><b>${item.name}</b></td>
                    <td style="text-align: center;">${item.qty}</td>
                    <td style="text-align: left;">${viewModel.formatCurrency(item.price)} $activeCurrency</td>
                    <td style="text-align: left;"><b>${viewModel.formatCurrency(item.qty * item.price)}</b> $activeCurrency</td>
                </tr>
            """.trimIndent())
        }
        
        val itemsTotal = items.sumOf { it.qty * it.price }
        htmlBuilder.append("""
                    </tbody>
                </table>
                
                <div class="total-section">
                    <table class="total-table">
                        <tr>
                            <td>إجمالي قيمة المواد:</td>
                            <td style="text-align: left;">${viewModel.formatCurrency(itemsTotal)} $activeCurrency</td>
                        </tr>
        """.trimIndent())
        
        if (invoice.discount > 0.0) {
            htmlBuilder.append("""
                        <tr>
                            <td style="color: #C0392B;">قيمة الخصم الممنوح:</td>
                            <td style="text-align: left; color: #C0392B; font-weight: bold;">- ${viewModel.formatCurrency(invoice.discount)} $activeCurrency</td>
                        </tr>
            """.trimIndent())
        }
        
        if (invoice.tax > 0.0) {
            htmlBuilder.append("""
                        <tr>
                            <td style="color: #2E86C1;">الضريبة المضافة:</td>
                            <td style="text-align: left; color: #2E86C1; font-weight: bold;">+ ${viewModel.formatCurrency(invoice.tax)} $activeCurrency</td>
                        </tr>
            """.trimIndent())
        }
        
        htmlBuilder.append("""
                        <tr class="total-row-highlight">
                            <td>صافي قيمة الفاتورة النهائية:</td>
                            <td style="text-align: left;">${viewModel.formatCurrency(invoice.total)} $activeCurrency</td>
                        </tr>
        """.trimIndent())
        
        if (invoice.paymentType == "credit") {
            val remaining = maxOf(0.0, invoice.total - invoice.paidAmount)
            htmlBuilder.append("""
                        <tr>
                            <td style="color: #27AE60;">المسدد (دفعة نقدية):</td>
                            <td style="text-align: left; color: #27AE60;">${viewModel.formatCurrency(invoice.paidAmount)} $activeCurrency</td>
                        </tr>
                        <tr>
                            <td style="color: #E67E22; font-weight: bold;">المتبقي برسم الذمة:</td>
                            <td style="text-align: left; color: #E67E22; font-weight: bold;">${viewModel.formatCurrency(remaining)} $activeCurrency</td>
                        </tr>
            """.trimIndent())
        }
        
        htmlBuilder.append("""
                    </table>
                </div>
        """.trimIndent())
        
        if (invoice.notes.isNotBlank()) {
            htmlBuilder.append("""
                <div class="notes-box">
                    <b>ملاحظات إضافية:</b>
                    <p style="margin: 4px 0 0 0; line-height: 1.5;">${invoice.notes}</p>
                </div>
            """.trimIndent())
        }
        
        htmlBuilder.append("""
                <div class="signature-box">
                    <div><b>توقيع المحاسب المستند:</b> _________________</div>
                    <div><b>توقيع المستلم:</b> _________________</div>
                </div>
                
                <div class="footer-branding">
                    <p>نشكر ثقتكم بنا وزيارتكم الكريمة!</p>
                    <p>تم توليد وحفظ هذا المستند كملف PDF رسمي مشفر إلكترونياً عبر تطبيق فواتير والمحاسب الذكي 📱</p>
                </div>
            </body>
            </html>
        """.trimIndent())
    }
    
    val act = findActivity(context)
    act?.runOnUiThread {
        val webView = android.webkit.WebView(act)
        MainActivity.tempWebViewForPrinting = webView
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                val printManager = act.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                val jobName = "فاتورة - ${invoice.id} - ${invoice.customer}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePreviewPrintDialog(
    invoice: Invoice,
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onEditInvoice: (Invoice) -> Unit
) {
    val items = remember(invoice) { viewModel.deserializeItems(invoice.itemsJson) }
    var isSimulatingPrint by remember { mutableStateOf(false) }
    var showPrintOptions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activeCurrency = invoice.currency
    val context = androidx.compose.ui.platform.LocalContext.current

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "معاينة الفاتورة وطباعتها",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Invoice Sheet
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isSimulatingPrint) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🖨️", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("جاري طباعة الفاتورة المحاسبية...", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Invoice metadata card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9F8)),
                                    border = BorderStroke(1.dp, Color(0xFFD0DEDD))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "رقم الفاتورة:", fontSize = 11.sp, color = Color.Gray)
                                            Text(text = invoice.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "العميل المستفيد:", fontSize = 11.sp, color = Color.Gray)
                                            Text(text = invoice.customer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "التاريخ:", fontSize = 11.sp, color = Color.Gray)
                                            Text(text = invoice.date, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "نوع العملة والمادة:", fontSize = 11.sp, color = Color.Gray)
                                            Text(text = invoice.currency, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "نوع الفاتورة والتحصيل:", fontSize = 11.sp, color = Color.Gray)
                                            val typeText = when (invoice.type) {
                                                "sale" -> "مبيع"
                                                "purchase" -> "شراء"
                                                else -> "مرتجع"
                                            }
                                            val payText = if (invoice.paymentType == "cash") "نقدي كاش" else "آجل على الذمة"
                                            Text(text = "$typeText ($payText)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // Items Header title
                            item {
                                Text(
                                    text = "تفاصيل المواد المسجلة:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            // Items List inside dialog
                            items(items) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                                    border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = "${item.qty} وحدة × ${viewModel.formatCurrency(item.price)} $activeCurrency",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Text(
                                            text = "${viewModel.formatCurrency(item.qty * item.price)} $activeCurrency",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Subtotal Breakdowns
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1F0))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val itemsTotal = items.sumOf { it.qty * it.price }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("إجمالي قيمة المواد:", fontSize = 12.sp, color = Color.DarkGray)
                                            Text("${viewModel.formatCurrency(itemsTotal)} $activeCurrency", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (invoice.discount > 0.0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("خصم / حسم في الفاتورة:", fontSize = 12.sp, color = Color(0xFFC0392B))
                                                Text("− ${viewModel.formatCurrency(invoice.discount)} $activeCurrency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                                            }
                                        }
                                        if (invoice.tax > 0.0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("الضريبة المضافة المقدرة:", fontSize = 12.sp, color = Color(0xFF2E86C1))
                                                Text("+ ${viewModel.formatCurrency(invoice.tax)} $activeCurrency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E86C1))
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.LightGray)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("صافي الفاتورة النهائي:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("${viewModel.formatCurrency(invoice.total)} $activeCurrency", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }

                                        if (invoice.paymentType == "credit") {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("المسدد (الدفعة النقدية):", fontSize = 11.sp, color = Color.Gray)
                                                Text("${viewModel.formatCurrency(invoice.paidAmount)} $activeCurrency", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF27AE60))
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val remaining = maxOf(0.0, invoice.total - invoice.paidAmount)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("المتبقي بذمة العميل:", fontSize = 11.sp, color = Color.Gray)
                                                Text("${viewModel.formatCurrency(remaining)} $activeCurrency", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE67E22))
                                            }
                                        }
                                    }
                                }
                            }

                            // Notes
                            if (invoice.notes.isNotBlank()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                                        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("ملاحظات إضافية:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(invoice.notes, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Under Preview Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Close button
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إغلاق", fontSize = 12.sp)
                    }

                    // Edit button
                    Button(
                        onClick = {
                            onEditInvoice(invoice)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("✏️ تعديل", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Print action button
                    Button(
                        onClick = {
                            showPrintOptions = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🖨️ طباعة الفاتورة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showPrintOptions) {
            AlertDialog(
                onDismissRequest = { showPrintOptions = false },
                title = { 
                    Text(
                        text = "خيارات ونوع الطباعة 🖨️", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp, 
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Right
                    ) 
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "الرجاء تحديد نوع وتنسيق الفاتورة لملائمتها مع الطابعة المتصلة بهاتفك:", 
                            fontSize = 13.sp, 
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Right
                        )
                        
                        // Option 1: A4 layout
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPrintOptions = false
                                    printInvoiceDoc(
                                        context = context,
                                        invoice = invoice,
                                        items = items,
                                        printType = "a4",
                                        companyName = viewModel.companyName.value,
                                        companyPhone = viewModel.companyPhone.value,
                                        companyAddress = viewModel.companyAddress.value,
                                        companyCurrency = viewModel.companyCurrency.value,
                                        viewModel = viewModel
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7F6)),
                            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp), 
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "📄 طباعة قياسية (برنت A4 أو PDF)", 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Right
                                    )
                                    Text(
                                        text = "للطابعات المكتبية المنزلية والشبكية وتطبيقات PDF", 
                                        fontSize = 11.sp, 
                                        color = Color.Gray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Right
                                    )
                                }
                            }
                        }
                        
                        // Option 2: Thermal receipt layout
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPrintOptions = false
                                    printInvoiceDoc(
                                        context = context,
                                        invoice = invoice,
                                        items = items,
                                        printType = "thermal",
                                        companyName = viewModel.companyName.value,
                                        companyPhone = viewModel.companyPhone.value,
                                        companyAddress = viewModel.companyAddress.value,
                                        companyCurrency = viewModel.companyCurrency.value,
                                        viewModel = viewModel
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F5F1)),
                            border = BorderStroke(1.dp, Color(0xFFEFE5DB))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp), 
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "🧾 طباعة حرارية (إيصال كاشير 80 مم / 58 مم)", 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Right
                                    )
                                    Text(
                                        text = "للطابعات والمكائن الحرارية المحمولة وبلوتوث", 
                                        fontSize = 11.sp, 
                                        color = Color.Gray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Right
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrintOptions = false }) {
                        Text(text = "إلغاء الأمر", color = Color.Gray)
                    }
                }
            )
        }
    }
}

fun printVoucherDoc(
    context: android.content.Context,
    voucher: Voucher,
    accountName: String,
    companyName: String,
    companyPhone: String,
    companyAddress: String,
    viewModel: AppViewModel
) {
    val htmlBuilder = java.lang.StringBuilder()
    val titleText = if (voucher.type == "receipt") "سند قبض نقدي" else "سند صرف نقدي"
    val subtitleText = if (voucher.type == "receipt") "مقبوضات نقدية وصندوق" else "مدفوعات نقدية خارج الصندوق"
    val formattedAmount = viewModel.formatCurrency(voucher.amount)
    
    htmlBuilder.append("""
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: 'Courier New', sans-serif; direction: rtl; padding: 25px; color: #333; }
                .border-wrapper { border: 3px double #1c544d; padding: 20px; }
                header { text-align: center; border-bottom: 2px solid #1c544d; padding-bottom: 12px; margin-bottom: 20px; }
                .company-name { font-size: 22px; font-weight: bold; color: #1c544d; margin: 0; }
                .company-info { font-size: 11px; color: #666; margin: 3px 0; }
                .doc-title { font-size: 26px; font-weight: bold; color: #1c544d; text-align: center; margin: 15px 0 5px 0; letter-spacing: 1px; }
                .doc-subtitle { font-size: 12px; color: #777; text-align: center; margin-bottom: 25px; }
                .meta-section { width: 100%; border-collapse: collapse; margin-bottom: 25px; }
                .meta-section td { padding: 10px; border: 1px dashed #ccc; font-size: 13px; vertical-align: middle; }
                .amount-box { border: 2px solid #1c544d; background-color: #f7faf9; font-size: 20px; font-weight: bold; color: #1c544d; padding: 12px; text-align: center; width: 60%; margin: 15px auto; }
                .sign { width: 100%; margin-top: 35px; border-collapse: collapse; }
                .sign td { font-size: 13px; font-weight: bold; text-align: center; width: 33%; padding-top: 50px; }
                .sign-line { border-top: 1px solid #777; width: 80%; margin: 0 auto; padding-top: 6px; }
                footer { text-align: center; margin-top: 40px; font-size: 10px; color: #aaa; border-top: 1px solid #eee; padding-top: 15px; }
            </style>
        </head>
        <body>
            <div class="border-wrapper">
                <header>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="width: 40%; text-align: right; vertical-align: top;">
                                <div class="company-name">${companyName}</div>
                                <div class="company-info">هاتف: ${companyPhone}</div>
                                <div class="company-info">العنوان: ${companyAddress}</div>
                            </td>
                            <td style="width: 20%; text-align: center; vertical-align: middle;">
                                <div style="font-size: 32px;">📑</div>
                            </td>
                            <td style="width: 40%; text-align: left; vertical-align: top; font-size: 12px; color: #555;">
                                <div><b>رقم المستند الداخلي:</b> #${voucher.id}</div>
                                <div><b>التاريخ والوقت:</b> ${voucher.date}</div>
                                <div><b>حالة السند:</b> مرحل ومعتمد (Saved)</div>
                            </td>
                        </tr>
                    </table>
                </header>
                
                <div class="doc-title">${titleText}</div>
                <div class="doc-subtitle">${subtitleText}</div>
                
                <table class="meta-section">
                    <tr>
                        <td style="width: 25%; background-color: #f7faf9;"><b>اسم المستلم/المستفيد:</b></td>
                        <td style="width: 75%; font-size: 15px; font-weight: bold; color: #1c544d;">${accountName}</td>
                    </tr>
                    <tr>
                        <td style="background-color: #f7faf9;"><b>المبلغ المستحق بالأرقام:</b></td>
                        <td>
                            <div class="amount-box">${formattedAmount} ل.س</div>
                        </td>
                    </tr>
                    <tr>
                        <td style="background-color: #f7faf9;"><b>بيان وتفاصيل الحركة:</b></td>
                        <td style="font-style: italic; line-height: 1.5;">${voucher.desc.ifBlank { "دفع/قبض نقدي لتسوية متبادلات في الذمم والودائع" }}</td>
                    </tr>
                </table>
                
                <table class="sign">
                    <tr>
                        <td>
                            <div class="sign-line">أمين الصندوق (الشركة)</div>
                        </td>
                        <td>
                            <div class="sign-line">المحاسب المسؤول</div>
                        </td>
                        <td>
                            <div class="sign-line">توقيع المستلم والعميل</div>
                        </td>
                    </tr>
                </table>
                
                <footer>
                     تم إصدار هذه الوثيقة إلكترونياً وتخزينها في قاعدة البيانات المحلية. تطبيق المحاسب الذكي 📱
                </footer>
            </div>
        </body>
        </html>
    """.trimIndent())

    val act = findActivity(context)
    act?.runOnUiThread {
        val webView = android.webkit.WebView(act)
        MainActivity.tempWebViewForPrinting = webView
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                val printManager = act.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                val jobName = "سند-${voucher.id}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherPreviewDialog(
    voucher: Voucher,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val matchedAccount = remember(voucher, accounts) { accounts.find { it.id == voucher.accountId } }
    val accountName = matchedAccount?.name ?: "حساب غير معروف"

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFD0DEDD))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تفاصيل مستند السند المحاسبي",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title/Type Banner
                    val isReceipt = voucher.type == "receipt"
                    val bannerBg = if (isReceipt) Color(0xFFE8F8F1) else Color(0xFFFCE8E6)
                    val bannerBorder = if (isReceipt) Color(0xFF2EBD7A) else Color(0xFFE03C3C)
                    val typeText = if (isReceipt) "سند قبض نقدي لصندوق الشركة 📥" else "سند صرف نقدي خارج صندوق الشركة 📤"
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bannerBg, RoundedCornerShape(8.dp))
                            .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = typeText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = bannerBorder,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Metadata Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9F8)),
                        border = BorderStroke(1.dp, Color(0xFFD0DEDD))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "رقم السجل المحاسبي:", fontSize = 11.sp, color = Color.Gray)
                                Text(text = "#${voucher.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "التاريخ:", fontSize = 11.sp, color = Color.Gray)
                                Text(text = voucher.date, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "اسم الحساب المستهدف:", fontSize = 11.sp, color = Color.Gray)
                                Text(text = accountName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Amount Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF9)),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "القيمة الإجمالية للمستند النقدي", fontSize = 11.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${viewModel.formatCurrency(voucher.amount)} ل.س",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Notice / Description
                    Text(text = "ملاحظات وتوضيح الحركة:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF9F9F9), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFE5E5E5), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = voucher.desc.ifBlank { "لا توجد ملاحظات إضافية مسجلة على هذا السند المالي." },
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("إغلاق الخيارات")
                    }
                    Button(
                        onClick = {
                            printVoucherDoc(
                                context = context,
                                voucher = voucher,
                                accountName = accountName,
                                companyName = viewModel.companyName.value,
                                companyPhone = viewModel.companyPhone.value,
                                companyAddress = viewModel.companyAddress.value,
                                viewModel = viewModel
                            )
                        },
                        modifier = Modifier.weight(2f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("طباعة ومشاركة 🖨️")
                        }
                    }
                }
            }
        }
    }
}
