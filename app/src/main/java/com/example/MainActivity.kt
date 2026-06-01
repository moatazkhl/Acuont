package com.example

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.FinancialMetrics
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartAccountantTheme {
                // Force Right-to-Left RTL Layout Direction for flawless Arabic UX
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        MainAppContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel()

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val loggedInPhone by viewModel.loggedInPhone.collectAsState()
    val loggedInName by viewModel.loggedInName.collectAsState()
    val loggedInRole by viewModel.loggedInRole.collectAsState()
    val activeUserStatus by viewModel.activeUserStatus.collectAsState()

    val products by viewModel.allProducts.collectAsState()
    val invoices by viewModel.filteredInvoices.collectAsState()
    val vouchers by viewModel.filteredVouchers.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()

    // Database trial item limitations
    val currentInvoicesCount = invoices.size
    val currentVouchersCount = vouchers.size
    val currentProductsCount = products.size
    val isPremium = (activeUserStatus == "ACTIVE")

    var showTrialLimitAlert by remember { mutableStateOf(false) }
    var trialAlertMessage by remember { mutableStateOf("") }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: الرئيسي, 1: المخزن, 2: سندات وحسابات

    // Dialog state controllers
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showAddInvoiceDialog by remember { mutableStateOf(false) }
    var showAddVoucherDialog by remember { mutableStateOf(false) }
    var showAiReportDialog by remember { mutableStateOf(false) }

    // CSV Document Creation Launcher
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val success = viewModel.exportReportToCsv(context, uri)
            if (success) {
                Toast.makeText(context, "تم حفظ الملف بنجاح كـ CSV متقن يدعم إكسل!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "فشل حفظ الملف. حاول مجدداً.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // JSON Backup Document Creation Launcher
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(
                context = context,
                uri = uri,
                onSuccess = {
                    Toast.makeText(context, "تم حفظ النسخة الاحتياطية سحابياً بنجاح!", Toast.LENGTH_LONG).show()
                },
                onError = { error ->
                    Toast.makeText(context, "فشل النسخ الاحتياطي: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // JSON Backup Document Open Launcher
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(
                context = context,
                uri = uri,
                onSuccess = {
                    Toast.makeText(context, "تمت استعادة البيانات والدفاتر بنجاح وسلاسة!", Toast.LENGTH_LONG).show()
                },
                onError = { error ->
                    Toast.makeText(context, "فشل استعادة النسخة: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale("ar")) }

    if (!isLoggedIn) {
        SmartAuthScreen(viewModel = viewModel)
    } else if (loggedInRole == "ADMIN") {
        SmartAdminPortalScreen(viewModel = viewModel)
    } else {
        Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
            // App Header Toolbar with Action buttons
            SmallTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "المحاسب الذكي الأنيق",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    if (activeUserStatus == "ACTIVE") {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "نسخة مدفوعة 🌟", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "تسجيل الخروج", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            // Dynamic Subscription Banner warnings
            if (activeUserStatus == "TRIAL") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text("أنت تستخدم النسخة التجريبية المحدودة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                text = "تبقى لديك استخدام محدود لـ 5 فواتير، 5 منتجات و5 سندات مالية. يرجى طلب التفعيل لفتح المزايا الكاملة.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.requestActivation()
                                Toast.makeText(context, "تم تقديم طلب التفعيل بنجاح! بانتظار موافقة الإدارة.", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("طلب التفعيل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (activeUserStatus == "PENDING") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                                Text("بانتظار موافقة المسؤول بالتفعيل", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100))
                            }
                            Text(
                                text = "تم تقديم الطلب! للتفعيل المباشر والدعم الفني، تواصل مع المسؤول على الرقم 0938385157 على الواتساب.",
                                fontSize = 11.sp,
                                color = Color(0xFF5D4037)
                            )
                        }
                        Button(
                            onClick = {
                                contactWhatsApp(context, "963938385157", "مرحباً، لقد أرسلت طلب تفعيل لحسابي في تطبيق المحاسب الذكي. رقم الهاتف: $loggedInPhone ومسجل باسم: $loggedInName. أرجو الموافقة عليه وتفعيل النسخة المدفوعة.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تفعيل واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Global Precise Date Filter Panel (Applies to all lists & summary analytics concurrently)
            GlobalDateFilterPanel(
                startDate = startDate,
                endDate = endDate,
                dateFormatter = dateFormatter,
                onSelectRange = { start, end ->
                    viewModel.updateDateRange(start, end)
                }
            )

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Tab Row Switcher
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("الرئيسية والتحليل", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("المستودع والمواد", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Warehouse, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("الحسابات والسندات", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }
                )
            }

            // Sub-screen displays based on chosen tab
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        metrics = metrics,
                        invoicesCount = invoices.size,
                        vouchersCount = vouchers.size,
                        productsCount = products.size,
                        onExportExcel = {
                            val simpleName = "Smart_Accountant_Report_${System.currentTimeMillis()}.csv"
                            csvLauncher.launch(simpleName)
                        },
                        onTriggerAi = {
                            viewModel.generateAiReportAsync()
                            showAiReportDialog = true
                        },
                        onBackup = {
                            val simpleName = "Smart_Accountant_Backup_${System.currentTimeMillis()}.json"
                            backupLauncher.launch(simpleName)
                        },
                        onRestore = {
                            restoreLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    1 -> WarehouseScreen(
                        products = products,
                        onAddProductClick = {
                            if (!isPremium && currentProductsCount >= 5) {
                                trialAlertMessage = "لقد استهلكت الحد الأقصى للمنتجات بالنسخة التجريبية (5 منتجات). تواصل مع الدعم الفني أو اطلب تفعيل التطبيق الآن!"
                                showTrialLimitAlert = true
                            } else {
                                showAddProductDialog = true
                            }
                        },
                        onEditProduct = { viewModel.updateProduct(it) },
                        onDeleteProduct = { viewModel.deleteProduct(it) }
                    )
                    2 -> AccountingVouchersScreen(
                        invoices = invoices,
                        vouchers = vouchers,
                        allProducts = products,
                        viewModel = viewModel,
                        onAddInvoiceClick = {
                            if (!isPremium && currentInvoicesCount >= 5) {
                                trialAlertMessage = "لقد استهلكت الحد الأقصى للفواتير بالنسخة التجريبية (5 فواتير). تواصل مع الدعم الفني أو اطلب تفعيل التطبيق لعمل فواتير غير محدودة!"
                                showTrialLimitAlert = true
                            } else {
                                showAddInvoiceDialog = true
                            }
                        },
                        onAddVoucherClick = {
                            if (!isPremium && currentVouchersCount >= 5) {
                                trialAlertMessage = "لقد استهلكت الحد الأقصى للسندات بالنسخة التجريبية (5 سندات). تواصل مع الدعم الفني أو اطلب تفعيل التطبيق لعمل سندات غير محدودة!"
                                showTrialLimitAlert = true
                            } else {
                                showAddVoucherDialog = true
                            }
                        },
                        onDeleteInvoice = { viewModel.deleteInvoice(it) },
                        onDeleteVoucher = { viewModel.deleteVoucher(it) }
                    )
                }
            }
        }
    }

    // Modal dialogue boxes for creating models
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onSave = {
                viewModel.addProduct(it)
                showAddProductDialog = false
            }
        )
    }

    if (showAddInvoiceDialog) {
        AddInvoiceDialog(
            products = products,
            onDismiss = { showAddInvoiceDialog = false },
            onSave = { invoice, items ->
                viewModel.addInvoice(invoice, items)
                showAddInvoiceDialog = false
            }
        )
    }

    if (showAddVoucherDialog) {
        AddVoucherDialog(
            onDismiss = { showAddVoucherDialog = false },
            onSave = {
                viewModel.addVoucher(it)
                showAddVoucherDialog = false
            }
        )
    }

    if (showAiReportDialog) {
        val summaryText by viewModel.aiReport.collectAsState()
        val loading by viewModel.isAiLoading.collectAsState()
        AiReportDialog(
            summaryText = summaryText,
            isLoading = loading,
            onDismiss = { showAiReportDialog = false }
        )
    }

    if (showTrialLimitAlert) {
        AlertDialog(
            onDismissRequest = { showTrialLimitAlert = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("انتهت النسخة التجريبية 🔒", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Text(
                    text = trialAlertMessage + "\n\nيمكنك التواصل مع الدعم الفني مباشرة أو تقديم طلب تفعيل لتنشيط التطبيق بشكل غير محدود وسلس.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestActivation()
                        contactWhatsApp(context, "963938385157", "مرحباً، لقد وصلت للحد الأقصى للنسخة التجريبية في تطبيق المحاسب الذكي الأنيق. رقم هاتف الحساب: $loggedInPhone ومسجل باسم: $loggedInName. أرجو تفعيل حسابي للنسخة المدفوعة غير المحدودة.")
                        showTrialLimitAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تواصل تفعيل واتساب", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrialLimitAlert = false }) {
                    Text("سأطلب لاحقاً", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ------------------- DYNAMIC DATE FILTER COMPONENT -------------------
@Composable
fun GlobalDateFilterPanel(
    startDate: Long,
    endDate: Long,
    dateFormatter: SimpleDateFormat,
    onSelectRange: (Long, Long) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "فلترة شاملة للتقارير والحسابات بالتاريخ:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start Date Picker Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = startDate
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val valCal = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, 0, 0, 0)
                                    }
                                    onSelectRange(valCal.timeInMillis, endDate)
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("من: " + dateFormatter.format(Date(startDate)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // End Date Picker Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = endDate
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val valCal = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, 23, 59, 59)
                                    }
                                    onSelectRange(startDate, valCal.timeInMillis)
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إلى: " + dateFormatter.format(Date(endDate)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ------------------- TAB 1: DASHBOARD AND REPORTS -------------------
@Composable
fun DashboardScreen(
    metrics: FinancialMetrics,
    invoicesCount: Int,
    vouchersCount: Int,
    productsCount: Int,
    onExportExcel: () -> Unit,
    onTriggerAi: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Metrics Responsive Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "إجمالي المبيعات",
                        value = metrics.sales,
                        icon = Icons.Default.TrendingUp,
                        color = GreenProfit,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "إجمالي المشتريات",
                        value = metrics.purchases,
                        icon = Icons.Default.TrendingDown,
                        color = RedDanger,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "سندات القبض",
                        value = metrics.receipts,
                        icon = Icons.Default.MonetizationOn,
                        color = LightBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "سندات الصرف",
                        value = metrics.payments,
                        icon = Icons.Default.Payment,
                        color = YellowWarning,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Profit or Loss large readout card
                val profitColor = if (metrics.netProfit >= 0) GreenProfit else RedDanger
                val profitIcon = if (metrics.netProfit >= 0) Icons.Default.AccountBalanceWallet else Icons.Default.TrendingDown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, profitColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("صافي الفوائض والأرباح للفترة", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${metrics.netProfit} ر.س",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = profitColor
                            )
                        }
                        Icon(
                            imageVector = profitIcon,
                            contentDescription = null,
                            tint = profitColor,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        // Section header for Document Counts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    InfoCell(label = "مواد مسجلة", count = productsCount.toString())
                    InfoCell(label = "فواتير الحركة", count = invoicesCount.toString())
                    InfoCell(label = "سندات مالية", count = vouchersCount.toString())
                }
            }
        }

        // Action Buttons: Excel and AI Adviser
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Arabic CSV / Excel exportation call to action
                Button(
                    onClick = onExportExcel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("export_excel_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تصدير للتقرير المالي المنسق (يدعم Excel)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Gemini Business Advisor button
                Button(
                    onClick = onTriggerAi,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("ai_advisor_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الحصول على الاستشارة الذكية من Gemini",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Section: Cloud Backup & Restore Card (Google Drive integration alternative)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "النسخ الاحتياطي السحابي والاسترجاع",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "قم بنسخ واستعادة قاعدة البيانات والقيود مباشرة وبشكل آمن من وإلى جوجل درايف (Google Drive) أو أي خدمة تخزين سحابي أخرى مفعلة على هاتفك بنقرة واحدة لتفادي مشاكل تسجيل الدخول.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Backup Button (Cloud Upload)
                        Button(
                            onClick = onBackup,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("cloud_backup_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ احتياطي", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Restore Button (Cloud Download)
                        Button(
                            onClick = onRestore,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("cloud_restore_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استعادة النسخة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: Double,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "$value ر.س",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun InfoCell(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

// ------------------- TAB 2: WAREHOUSE MANAGEMENT -------------------
@Composable
fun WarehouseScreen(
    products: List<Product>,
    onAddProductClick: () -> Unit,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredProducts = products.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.barcode.contains(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("قائمة البضائع والمستودع", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onAddProductClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("مادة جديدة")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ابحث باسم المنتج أو الباركود...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("لا توجد مادة تطابق معايير البحث الحالية", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredProducts) { item ->
                    ProductItemRow(
                        product = item,
                        onEdit = onEditProduct,
                        onDelete = { onDeleteProduct(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductItemRow(
    product: Product,
    onEdit: (Product) -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = product.quantity <= product.minLimit
    val quantityColor = if (isLowStock) RedDanger else GreenProfit

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = product.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (product.barcode.isNotEmpty()) {
                        Text(text = "رقم الباركود: " + product.barcode, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${product.quantity} وحدة",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = quantityColor,
                        modifier = Modifier
                            .background(quantityColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = RedDanger, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "شراء: ${product.purchasePrice} ر.س", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(text = "بيع: ${product.salePrice} ر.س", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Text(
                    text = "التصنيف: " + product.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ------------------- TAB 3: ACCOUNTING VOCHERS AND INVOICES -------------------
@Composable
fun AccountingVouchersScreen(
    invoices: List<Invoice>,
    vouchers: List<Voucher>,
    allProducts: List<Product>,
    viewModel: AppViewModel,
    onAddInvoiceClick: () -> Unit,
    onAddVoucherClick: () -> Unit,
    onDeleteInvoice: (Invoice) -> Unit,
    onDeleteVoucher: (Voucher) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) } // 0: فواتير, 1: سندات مالية
    var invoiceFilter by remember { mutableIntStateOf(0) } // 0: الكل, 1: مبيعات (مدين), 2: مشتريات (دائن)
    var voucherFilter by remember { mutableIntStateOf(0) } // 0: الكل, 1: قبض (مدين), 2: صرف (دائن)
    val sdf = remember { SimpleDateFormat("yyyy/MM/dd", Locale("ar")) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            SegmentButton(selected = subTab == 0, text = "فواتير الحركة (شراء/بيع)", onClick = { subTab = 0 })
            Spacer(modifier = Modifier.width(6.dp))
            SegmentButton(selected = subTab == 1, text = "سندات مالية (قبض/صرف)", onClick = { subTab = 1 })
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (subTab == 0) {
            // Invoices sub-section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("قائمة الفواتير للفترة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onAddInvoiceClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إنشاء فاتورة")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Invoices Sub-Filters (Debit vs Credit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SegmentButton(selected = invoiceFilter == 0, text = "كل الفواتير", onClick = { invoiceFilter = 0 })
                SegmentButton(selected = invoiceFilter == 1, text = "مبيعات (مدين)", onClick = { invoiceFilter = 1 })
                SegmentButton(selected = invoiceFilter == 2, text = "مشتريات (دائن)", onClick = { invoiceFilter = 2 })
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayInvoices = remember(invoices, invoiceFilter) {
                when (invoiceFilter) {
                    1 -> invoices.filter { it.type == "SALE" }
                    2 -> invoices.filter { it.type == "PURCHASE" }
                    else -> invoices
                }
            }

            if (displayInvoices.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val emptyText = when (invoiceFilter) {
                        1 -> "لا توجد فواتير مبيعات (مدين) حالياً"
                        2 -> "لا توجد فواتير مشتريات (دائن) حالياً"
                        else -> "لا توجد فواتير مسجلة في هذا النطاق الزمني"
                    }
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayInvoices) { invoice ->
                        InvoiceRowItem(invoice = invoice, sdf = sdf, onDelete = { onDeleteInvoice(invoice) })
                    }
                }
            }
        } else {
            // Vouchers sub-section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("قائمة السندات المتوفرة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onAddVoucherClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("سند مالي جديد")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Vouchers Sub-Filters (Debit vs Credit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SegmentButton(selected = voucherFilter == 0, text = "كل السندات", onClick = { voucherFilter = 0 })
                SegmentButton(selected = voucherFilter == 1, text = "سندات قبض (مدين)", onClick = { voucherFilter = 1 })
                SegmentButton(selected = voucherFilter == 2, text = "سندات صرف (دائن)", onClick = { voucherFilter = 2 })
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayVouchers = remember(vouchers, voucherFilter) {
                when (voucherFilter) {
                    1 -> vouchers.filter { it.type == "RECEIPT" }
                    2 -> vouchers.filter { it.type == "PAYMENT" }
                    else -> vouchers
                }
            }

            if (displayVouchers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val emptyText = when (voucherFilter) {
                        1 -> "لا توجد سندات قبض (مدين) حالياً"
                        2 -> "لا توجد سندات صرف (دائن) حالياً"
                        else -> "لا توجد مقبوضات أو مدفوعات للفترة المذكورة"
                    }
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayVouchers) { voucher ->
                        VoucherRowItem(voucher = voucher, sdf = sdf, onDelete = { onDeleteVoucher(voucher) })
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentButton(selected: Boolean, text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun InvoiceRowItem(invoice: Invoice, sdf: SimpleDateFormat, onDelete: () -> Unit) {
    val typeText = if (invoice.type == "SALE") "فاتورة بيع" else "فاتورة شراء"
    val typeColor = if (invoice.type == "SALE") GreenProfit else RedDanger

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeText,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "رقم: " + invoice.invoiceNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = RedDanger, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("العامل / المورد: " + invoice.partyName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            if (invoice.notes.isNotEmpty()) {
                Text("ملاحظات: " + invoice.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = sdf.format(Date(invoice.date)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text(text = "المبلغ: " + invoice.totalAmount + " ر.س", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun VoucherRowItem(voucher: Voucher, sdf: SimpleDateFormat, onDelete: () -> Unit) {
    val isReceipt = voucher.type == "RECEIPT"
    val typeText = if (isReceipt) "سند قبض" else "سند صرف"
    val typeColor = if (isReceipt) LightBlue else YellowWarning

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeText,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(typeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "سند رقم: " + voucher.voucherNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = RedDanger, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("علاقة مع: "+ voucher.partyName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            if (voucher.notes.isNotEmpty()) {
                Text("البيان: " + voucher.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = sdf.format(Date(voucher.date)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text(text = "المبلغ: " + voucher.amount + " ر.س", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = typeColor)
            }
        }
    }
}

// ------------------- POPUP MODALS AND DIALOGS -------------------

@Composable
fun AddProductDialog(onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("عام") }
    var quantity by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var minLimit by remember { mutableStateOf("5.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إدراج مادة جديدة في المستودع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المادة") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("رقم الباركود (اختياري)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("التصنيف أو اسم الرف") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("الكمية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minLimit,
                        onValueChange = { minLimit = it },
                        label = { Text("الحد الأدنى") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("سعر الشراء") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = salePrice,
                        onValueChange = { salePrice = it },
                        label = { Text("سعر البيع") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty()) {
                        onSave(
                            Product(
                                name = name,
                                barcode = barcode,
                                category = category,
                                quantity = quantity.toDoubleOrNull() ?: 0.0,
                                purchasePrice = purchasePrice.toDoubleOrNull() ?: 0.0,
                                salePrice = salePrice.toDoubleOrNull() ?: 0.0,
                                minLimit = minLimit.toDoubleOrNull() ?: 5.0
                            )
                        )
                    }
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun AddInvoiceDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (Invoice, List<InvoiceItem>) -> Unit
) {
    var invoiceNumber by remember { mutableStateOf((1000..9999).random().toString()) }
    var selectedType by remember { mutableStateOf("SALE") } // SALE, PURCHASE
    var partyName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Items belonging to this new invoice
    var addedItems = remember { mutableStateListOf<InvoiceItem>() }

    // Selected product state
    var selectedProductIndex by remember { mutableIntStateOf(-1) }
    var quantityInput by remember { mutableStateOf("1") }
    var unitPriceInput by remember { mutableStateOf("") }

    val totalInvoicePrice = addedItems.sumOf { it.totalPrice }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء فاتورة جديدة") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Type selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نوع الفاتورة", fontWeight = FontWeight.Bold)
                        Row {
                            SegmentButton(selected = selectedType == "SALE", text = "مبيعات (عميل)", onClick = { selectedType = "SALE" })
                            Spacer(modifier = Modifier.width(4.dp))
                            SegmentButton(selected = selectedType == "PURCHASE", text = "مشتريات (مورد)", onClick = { selectedType = "PURCHASE" })
                        }
                    }
                }

                item {
                    OutlinedTextField(value = invoiceNumber, onValueChange = { invoiceNumber = it }, label = { Text("رقم الفاتورة") }, modifier = Modifier.fillMaxWidth())
                }

                item {
                    val labelString = if (selectedType == "SALE") "اسم العميل" else "اسم المورد"
                    OutlinedTextField(value = partyName, onValueChange = { partyName = it }, label = { Text(labelString) }, modifier = Modifier.fillMaxWidth())
                }

                item {
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("البيان والملاحظات") }, modifier = Modifier.fillMaxWidth())
                }

                item {
                    Divider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("إدراج مادة للفاتورة:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                item {
                    // Products Dropdown replacement/Simple selection row
                    if (products.isEmpty()) {
                        Text("ملاحظة: لا توجد بضائع في المستودع المذكور للإدراج", color = RedDanger, fontSize = 12.sp)
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = { expanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            ) {
                                val text = if (selectedProductIndex == -1) "اختر المادة من المستودع..." else products[selectedProductIndex].name
                                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                products.forEachIndexed { index, product ->
                                    DropdownMenuItem(
                                        text = { Text("${product.name} (متوفر: ${product.quantity})") },
                                        onClick = {
                                            selectedProductIndex = index
                                            expanded = false
                                            // auto-fill prices
                                            unitPriceInput = if (selectedType == "SALE") product.salePrice.toString() else product.purchasePrice.toString()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityInput,
                            onValueChange = { quantityInput = it },
                            label = { Text("الكمية") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unitPriceInput,
                            onValueChange = { unitPriceInput = it },
                            label = { Text("سعر الوحدة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (selectedProductIndex != -1) {
                                val product = products[selectedProductIndex]
                                val quantity = quantityInput.toDoubleOrNull() ?: 1.0
                                val price = unitPriceInput.toDoubleOrNull() ?: 0.0
                                val item = InvoiceItem(
                                    invoiceId = 0,
                                    productId = product.id,
                                    productName = product.name,
                                    quantity = quantity,
                                    unitPrice = price,
                                    totalPrice = quantity * price
                                )
                                addedItems.add(item)
                                // reset selections
                                selectedProductIndex = -1
                                quantityInput = "1"
                                unitPriceInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("أضف المادة للحزمة")
                    }
                }

                item {
                    Text("المواد المضافة للفاتورة الحالية:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                items(addedItems) { it ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(it.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${it.quantity} وحدة × ${it.unitPrice} ر.س", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Text("${it.totalPrice} ر.س", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "المبلغ الإجمالي الكلي: $totalInvoicePrice ر.س",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (partyName.isNotEmpty() && addedItems.isNotEmpty()) {
                        onSave(
                            Invoice(
                                invoiceNumber = invoiceNumber,
                                type = selectedType,
                                date = System.currentTimeMillis(),
                                partyName = partyName,
                                totalAmount = totalInvoicePrice,
                                notes = notes
                            ),
                            addedItems.toList()
                        )
                    }
                }
            ) {
                Text("تثبيت الفاتورة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun AddVoucherDialog(onDismiss: () -> Unit, onSave: (Voucher) -> Unit) {
    var voucherNumber by remember { mutableStateOf((5000..9999).random().toString()) }
    var selectedType by remember { mutableStateOf("RECEIPT") } // RECEIPT: قبض, PAYMENT: صرف
    var partyName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء سند مالي جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نوع السند", fontWeight = FontWeight.Bold)
                    Row {
                        SegmentButton(selected = selectedType == "RECEIPT", text = "سند قبض (أموال واردة)", onClick = { selectedType = "RECEIPT" })
                        Spacer(modifier = Modifier.width(4.dp))
                        SegmentButton(selected = selectedType == "PAYMENT", text = "سند صرف (أجور/مصاريف)", onClick = { selectedType = "PAYMENT" })
                    }
                }

                OutlinedTextField(value = voucherNumber, onValueChange = { voucherNumber = it }, label = { Text("رقم السند") }, modifier = Modifier.fillMaxWidth())

                val tag = if (selectedType == "RECEIPT") "اسم دافع المبلغ" else "اسم مستلم المبلغ"
                OutlinedTextField(value = partyName, onValueChange = { partyName = it }, label = { Text(tag) }, modifier = Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ المالي (ر.س)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("البيان والملاحظات") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (partyName.isNotEmpty() && amt > 0) {
                        onSave(
                            Voucher(
                                voucherNumber = voucherNumber,
                                type = selectedType,
                                date = System.currentTimeMillis(),
                                partyName = partyName,
                                amount = amt,
                                notes = notes
                            )
                        )
                    }
                }
            ) {
                Text("حفظ السند")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun AiReportDialog(summaryText: String, isLoading: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("المستشار المالي الذكي (Gemini)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = summaryText,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            Text(
                                text = summaryText,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("حسناً")
            }
        }
    )
}

// ------------------- WHATSAPP INTEGRATION & SUPPORT -------------------
fun contactWhatsApp(context: android.content.Context, phone: String, message: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
            `package` = "com.whatsapp"
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(context, "لم يتم العثور على تطبيق واتساب. رقم التواصل: 0938385157", Toast.LENGTH_LONG).show()
        }
    }
}

// ------------------- PREMIUM AUTHENTICATION SCREEN -------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAuthScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var isRegisterMode by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = if (isRegisterMode) "إنشاء حساب جديد" else "تسجيل الدخول",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "تطبيق المحاسب الذكي الأنيق لإدارة المبيعات والمستودعات والمالية",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("الاسم الكامل") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("phone_input"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("password_input"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        errorMsg = ""
                        if (isRegisterMode) {
                            if (name.isBlank() || phone.isBlank() || password.isBlank()) {
                                errorMsg = "يرجى ملء جميع الحقول المطلوبة!"
                            } else {
                                val success = viewModel.register(name, phone, password)
                                if (success) {
                                    Toast.makeText(context, "أهلاً بك! تم إنشاء حسابك وتفعيله كنسخة تجريبية.", Toast.LENGTH_LONG).show()
                                } else {
                                    errorMsg = "فشل التسجيل! قد يكون هذا الرقم مسجلاً بالفعل."
                                }
                            }
                        } else {
                            if (phone.isBlank() || password.isBlank()) {
                                errorMsg = "يرجى إدخال رقم الهاتف وكلمة المرور!"
                            } else {
                                val success = viewModel.login(phone, password)
                                if (success) {
                                    Toast.makeText(context, "تم تسجيل الدخول بنجاح!", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMsg = "خطأ في رقم الهاتف أو كلمة المرور!"
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_auth_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isRegisterMode) "إنشاء الحساب وبدء التجريب" else "تسجيل الدخول",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                TextButton(
                    onClick = {
                        isRegisterMode = !isRegisterMode
                        errorMsg = ""
                    },
                    modifier = Modifier.testTag("toggle_auth_mode_button")
                ) {
                    Text(
                        text = if (isRegisterMode) "لديك حساب بالفعل؟ سجل الدخول هنا" else "ليس لديك حساب؟ سجل حساباً تجريبياً مجاناً",
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Info for trial test login
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 معلومات للاختبار السريع ومراجعة التفعيل:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "رقم الهاتف للـمدير: admin  | كلمة المرور: admin",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "رقم الهاتف للمستخدم: 0938385157 | كلمة المرور: 123456",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------- SMART ADMIN PORTAL SCREEN -------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAdminPortalScreen(viewModel: AppViewModel) {
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SmallTopAppBar(
            title = {
                Text(
                    text = "بوابة مسؤول التطبيق - إدارة اشتراكات الدفع",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            actions = {
                IconButton(onClick = { viewModel.logout() }) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "تسجيل الخروج",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "من لوحة التحكم هذه، يمكنك مراجعة طلبات التفعيل والموافقة عليها بعد التأكد من استلام الدفعة المالية (مثلاً عبر حوالة أو كاش).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "إجمالي الحسابات المسجلة على هذا الجهاز: ${registeredUsers.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            item {
                Text(
                    text = "قائمة حسابات المستخدمين والطلبات:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (registeredUsers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد مستخدمون حالياً غير حساب المسؤول.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else {
                items(registeredUsers) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = user.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "رقم الهاتف: ${user.phone}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Status Badges
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val (badgeBg, badgeFg, label) = when (user.status) {
                                        "ACTIVE" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "نسخة مدفوعة نشطة")
                                        "PENDING" -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "بانتظار موافقة التفعيل ⏳")
                                        else -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), "نسخة تجريبية محدودة")
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(badgeBg)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = label, color = badgeFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (user.status == "PENDING") {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = "طلب التفعيل نشط", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (user.status != "ACTIVE") {
                                    Button(
                                        onClick = {
                                            viewModel.changeUserStatus(user.phone, "ACTIVE")
                                            Toast.makeText(context, "تم تفعيل حساب ${user.name} بنجاح كنسخة مدفوعة كاملة!", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("موافقة للتفعيل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.changeUserStatus(user.phone, "TRIAL")
                                            Toast.makeText(context, "تم إلغاء التفعيل وجعل حساب ${user.name} تجريبياً.", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إلغاء التفعيل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
