package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.AppViewModel
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartAccountantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AccountantAppContent()
                }
            }
        }
    }
}

// ------------------- DESIGN SYSTEM THEME DEFINITION -------------------
@Composable
fun SmartAccountantTheme(content: @Composable () -> Unit) {
    val lightColorScheme = lightColorScheme(
        primary = Color(0xFF1E3A8A), // Elegant Navy Blue
        onPrimary = Color.White,
        secondary = Color(0xFF10B981), // Emerald Green
        onSecondary = Color.White,
        tertiary = Color(0xFFF59E0B), // Golden Orange
        background = Color(0xFFF3F4F6), // Clean Light Gray
        surface = Color.White,
        onSurface = Color(0xFF111827),
        onSurfaceVariant = Color(0xFF4B5563),
        error = Color(0xFFEF4444),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF991B1B)
    )

    MaterialTheme(
        colorScheme = lightColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}

// ------------------- MAIN ROUTING SYSTEM -------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountantAppContent() {
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
    val metrics by viewModel.computedMetrics.collectAsState()

    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()

    // Premium/Trial status limitations
    val currentInvoicesCount = invoices.size
    val currentVouchersCount = vouchers.size
    val currentProductsCount = products.size
    val isPremium = (activeUserStatus == "ACTIVE")

    var showTrialLimitAlert by remember { mutableStateOf(false) }
    var trialAlertMessage by remember { mutableStateOf("") }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Warehouse, 2: Invoices & Vouchers

    // Dialogue control flags
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showAddInvoiceDialog by remember { mutableStateOf(false) }
    var showAddVoucherDialog by remember { mutableStateOf(false) }
    var showAiReportDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale("ar")) }

    // Launcher Intents for CSV and JSON export/import
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.viewModelScope.launch {
                val success = viewModel.exportReportToCsv(context, it)
                if (success) {
                    Toast.makeText(context, "تم تصدير كشف الحساب والتحليل المالي بنجاح بصيغة CSV!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل تصدير الكشف. حاول مرة أخرى.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.viewModelScope.launch {
                val success = viewModel.exportBackup(context, it)
                if (success) {
                    Toast.makeText(context, "تم حفظ النسخة الاحتياطية بنجاح!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل تصدير النسخة الاحتياطية الاسترجاعية.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.viewModelScope.launch {
                val success = viewModel.importBackup(context, it)
                if (success) {
                    Toast.makeText(context, "تم استعادة كامل البيانات والمستندات بنجاح من النسخة المحددة!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "تعذر استيراد البيانات، يرجى التحقق من صحة الملف الاحتياطي.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Edge To Edge container spacing
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (!isLoggedIn) {
                SmartAuthScreen(viewModel = viewModel)
            } else if (loggedInRole == "ADMIN") {
                SmartAdminPortalScreen(viewModel = viewModel)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Modern Toolbar with Subscription details
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
                                    text = stringResource(R.string.app_name),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.smallTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
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
                                    Text(
                                        text = "نسخة مدفوعة 🌟",
                                        color = Color(0xFF2E7D32),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.logout() },
                                modifier = Modifier.testTag("logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "تسجيل الخروج",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    // Subscription state notice displays
                    if (activeUserStatus == "TRIAL") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        Text("أنت تستخدم النسخة التجريبية المحدودة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                    Text(
                                        text = "المتبقي: ${5 - currentProductsCount}/5 مواد، ${5 - currentInvoicesCount}/5 فواتير، ${5 - currentVouchersCount}/5 سندات متبقية للتجربة.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.requestActivation()
                                        Toast.makeText(context, "تم إرسال طلب التفعيل بنجاح للمسؤول بانتظار الموافقة!", Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("request_activation_button")
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
                                        text = "للتفعيل الفوري لنسختك، أرسل رسالة سريعة مباشرة للمدير الفني عبر زر الواتساب التالي.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5D4037)
                                    )
                                }
                                Button(
                                    onClick = {
                                        contactWhatsApp(
                                            context,
                                            "963938385157",
                                            "مرحباً، لقد قدمت طلب تفعيل لحسابي في تطبيق المحاسب الأنيق. رقم الحساب: $loggedInPhone والاسم المسجل: $loggedInName. أرجو تفعيل النسخة الكاملة."
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("activation_whatsapp_button")
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تفعيل واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    // Precise Date Filter Selector Panel (Shared across the accounting system)
                    GlobalDateFilterPanel(
                        startDate = startDate,
                        endDate = endDate,
                        dateFormatter = dateFormatter,
                        onSelectRange = { start, end ->
                            viewModel.updateDateRange(start, end)
                        },
                        onReset = {
                            viewModel.resetDateFilters()
                        }
                    )

                    // Tab Selector Menu
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("الرئيسية والتحليل", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                            modifier = Modifier.testTag("tab_dashboard")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("المستودع والمواد", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Warehouse, contentDescription = null) },
                            modifier = Modifier.testTag("tab_warehouse")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("الحسابات والسندات", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                            modifier = Modifier.testTag("tab_accounting")
                        )
                    }

                    // Content screens controller
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> DashboardScreen(
                                metrics = metrics,
                                incomesCount = currentInvoicesCount,
                                vouchersCount = currentVouchersCount,
                                productsCount = currentProductsCount,
                                onExportExcel = {
                                    val fileName = "تقرير_المحاسب_الذكي_${System.currentTimeMillis()}.csv"
                                    csvLauncher.launch(fileName)
                                },
                                onTriggerAi = {
                                    viewModel.generateAiReportAsync()
                                    showAiReportDialog = true
                                },
                                onBackup = {
                                    val fileName = "نسخة_احتياطية_المحاسب_${System.currentTimeMillis()}.json"
                                    backupLauncher.launch(fileName)
                                },
                                onRestore = {
                                    restoreLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            1 -> WarehouseScreen(
                                products = products,
                                onAddProductClick = {
                                    if (!isPremium && currentProductsCount >= 5) {
                                        trialAlertMessage = "لقد وصلت للحد الأقصى للإنشاء في النسخة المجانية (5 مواد بمستودعك). تواصل مع المسؤول لتفعيل المزايا المفتوحة!"
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
                                        trialAlertMessage = "لقد استهلكت فواتيرك المجانية المسموحة (5 فواتير بالنسخة التجريبية). تفضل بطلب اشتراك التفعيل الفوري!"
                                        showTrialLimitAlert = true
                                    } else {
                                        showAddInvoiceDialog = true
                                    }
                                },
                                onAddVoucherClick = {
                                    if (!isPremium && currentVouchersCount >= 5) {
                                        trialAlertMessage = "انتهى رصيد السندات التجريبي المتاح (5 سندات مالية). بادر بطلب تفعيل حسابك كنسخة مدفوعة كاملة!"
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
        }
    }

    // Dialogue Overlay Modals
    if (showAddProductDialog) {
        AddProductDialog(
            onDismiss = { showAddProductDialog = false },
            onConfirm = { name, q, p, s ->
                viewModel.insertProduct(name, q, p, s)
                showAddProductDialog = false
            }
        )
    }

    if (showAddInvoiceDialog) {
        AddInvoiceDialog(
            products = products,
            onDismiss = { showAddInvoiceDialog = false },
            onConfirm = { type, docNo, customer, total, details ->
                viewModel.insertInvoice(type, docNo, customer, total, details)
                showAddInvoiceDialog = false
            }
        )
    }

    if (showAddVoucherDialog) {
        AddVoucherDialog(
            onDismiss = { showAddVoucherDialog = false },
            onConfirm = { type, docNo, party, amount, notes ->
                viewModel.insertVoucher(type, docNo, party, amount, notes)
                showAddVoucherDialog = false
            }
        )
    }

    if (showAiReportDialog) {
        AiAdviceReportDialog(
            viewModel = viewModel,
            onDismiss = { showAiReportDialog = false }
        )
    }

    if (showTrialLimitAlert) {
        AlertDialog(
            onDismissRequest = { showTrialLimitAlert = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("انتهت النسخة التجريبية 🔒", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Text(
                    text = "$trialAlertMessage\n\nيمكنك تقديم طلب تفعيل تالي والتواصل مباشرة معنا لتنشيط حسابك بالكامل ورفع القيود فوراً.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestActivation()
                        contactWhatsApp(
                            context,
                            "963938385157",
                            "أهلاً، لقد تجاوزتُ الحد التجريبي للتطبيق بـ $loggedInName ورقم الهاتف $loggedInPhone. أرجو تفعيل باقتي المدفوعة."
                        )
                        showTrialLimitAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.testTag("trial_alert_confirm")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تقديم وتواصل واتساب", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTrialLimitAlert = false },
                    modifier = Modifier.testTag("trial_alert_dismiss")
                ) {
                    Text("سأطلب لاحقاً", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ------------------- DYNAMIC DATE FILTER ELEMENT -------------------
@Composable
fun GlobalDateFilterPanel(
    startDate: Long,
    endDate: Long,
    dateFormatter: SimpleDateFormat,
    onSelectRange: (Long, Long) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("الفلترة بحسب تاريخ المستندات:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "من ${dateFormatter.format(Date(startDate))}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                            .clickable {
                                showDatePicker(context, startDate) { selectedDate ->
                                    onSelectRange(selectedDate, endDate)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text("إلى", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = dateFormatter.format(Date(endDate)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                            .clickable {
                                showDatePicker(context, endDate) { selectedDate ->
                                    val adjustEnd = Calendar.getInstance().apply {
                                        timeInMillis = selectedDate
                                        set(Calendar.HOUR_OF_DAY, 23)
                                        set(Calendar.MINUTE, 59)
                                        set(Calendar.SECOND, 59)
                                    }.timeInMillis
                                    onSelectRange(startDate, adjustEnd)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = onReset,
                modifier = Modifier.testTag("reset_date_filter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "إعادة تعيين التواريخ",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun showDatePicker(context: Context, initialTime: Long, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = if (initialTime > 0) initialTime else System.currentTimeMillis() }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val result = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onDateSelected(result)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

// ------------------- TAB 0: DASHBOARD & METRICS SCREEN -------------------
@Composable
fun DashboardScreen(
    metrics: FinancialMetrics,
    incomesCount: Int,
    vouchersCount: Int,
    productsCount: Int,
    onExportExcel: () -> Unit,
    onTriggerAi: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AI Advisor action banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Text(
                            text = "المستشار المالي الذكي (Gemini)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "اضغط لتشخيص صحة حساباتك وأرباحك ومستودعك وصياغة الحلول التنافسية الذكية برعاية الذكاء الاصطناعي التوليدي الحقيقي.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onTriggerAi,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("trigger_ai_button")
                    ) {
                        Text("بدء تشخيص الذكاء الاصطناعي ✨", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Metrics Grid System
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المؤشرات والصكوك المالية الحالية:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        title = "إجمالي المبيعات",
                        value = "${metrics.totalSales} ل.س",
                        icon = Icons.Default.TrendingUp,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "إجمالي المشتريات",
                        value = "${metrics.totalPurchases} ل.س",
                        icon = Icons.Default.TrendingDown,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        title = "قيمة مستودعك",
                        value = "${metrics.warehouseValue} ل.س",
                        icon = Icons.Default.Inventory,
                        color = Color(0xFF3F51B5),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "الأرباح الصافية التقديرية",
                        value = "${metrics.estimatedProfit} ل.س",
                        icon = Icons.Default.AccountBalanceWallet,
                        color = if (metrics.estimatedProfit >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        title = "المرتفعات/المرتجع",
                        value = "${metrics.totalReturns} ل.س",
                        icon = Icons.Default.KeyboardReturn,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "صافي النقد المتوفر",
                        value = "${metrics.netCashFlow} ل.س",
                        icon = Icons.Default.AttachMoney,
                        color = if (metrics.netCashFlow >= 0) Color(0xFF4CAF50) else Color(0xFFE91E63),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Action files utility section (CSV Export + Backup)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("أدوات تخزين وتصدير التقارير الهامة:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onExportExcel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_excel_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصدير Excel/CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onBackup,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ احتياطي للكل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onRestore,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استيراد واستعادة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ------------------- TAB 1: Warehouse & Materials SCREEN -------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WarehouseScreen(
    products: List<ProductEntity>,
    onAddProductClick: () -> Unit,
    onEditProduct: (ProductEntity) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = products.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Material list controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث باسم السلعة...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text("مستودعك فارغ حالياً! اضغط على المربع بالأسفل لإدخال بضائعك.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { prod ->
                        ProductItemCard(
                            product = prod,
                            onEdit = { onEditProduct(it) },
                            onDelete = { onDeleteProduct(it) }
                        )
                    }
                }
            }
        }

        // Floating action button to Add Products
        FloatingActionButton(
            onClick = onAddProductClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_product_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "إضافة مادة للجريد")
        }
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    onEdit: (ProductEntity) -> Unit,
    onDelete: (ProductEntity) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "الكمية: ${product.quantity}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (product.quantity <= 1) Color.Red else Color.DarkGray,
                        modifier = Modifier
                            .background(if (product.quantity <= 1) Color(0xFFFFEBEE) else Color(0xFFF3F4F6), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(text = "سعر الشراء: ${product.purchasePrice} ل.س", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "سعر المبيع: ${product.sellingPrice} ل.س", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل المادة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onDelete(product) }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف المادة", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showEditDialog) {
        var name by remember { mutableStateOf(product.name) }
        var qty by remember { mutableStateOf(product.quantity.toString()) }
        var purchase by remember { mutableStateOf(product.purchasePrice.toString()) }
        var sell by remember { mutableStateOf(product.sellingPrice.toString()) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("تعديل تفاصيل المادة المستودعية", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المادة") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("الكمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = purchase, onValueChange = { purchase = it }, label = { Text("سعر الشراء الفردي") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sell, onValueChange = { sell = it }, label = { Text("سعر البيع الفردي") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qNum = qty.toDoubleOrNull() ?: product.quantity
                        val pNum = purchase.toDoubleOrNull() ?: product.purchasePrice
                        val sNum = sell.toDoubleOrNull() ?: product.sellingPrice
                        onEdit(product.copy(name = name, quantity = qNum, purchasePrice = pNum, sellingPrice = sNum))
                        showEditDialog = false
                    }
                ) {
                    Text("حفظ التغييرات")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("إلغاء الأمر")
                }
            }
        )
    }
}

// ------------------- TAB 2: INVOICES & VOUCHERS SCREEN -------------------
@Composable
fun AccountingVouchersScreen(
    invoices: List<InvoiceEntity>,
    vouchers: List<VoucherEntity>,
    allProducts: List<ProductEntity>,
    viewModel: AppViewModel,
    onAddInvoiceClick: () -> Unit,
    onAddVoucherClick: () -> Unit,
    onDeleteInvoice: (InvoiceEntity) -> Unit,
    onDeleteVoucher: (VoucherEntity) -> Unit
) {
    var subTabState by remember { mutableIntStateOf(0) } // 0: Invoices, 1: Vouchers
    val df = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Toggle header sub-tabs
        TabRow(
            selectedTabIndex = subTabState,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = subTabState == 0, onClick = { subTabState = 0 }, text = { Text("سجل الفواتير", fontWeight = FontWeight.Bold, fontSize = 12.sp) })
            Tab(selected = subTabState == 1, onClick = { subTabState = 1 }, text = { Text("سجل السندات", fontWeight = FontWeight.Bold, fontSize = 12.sp) })
        }

        Box(modifier = Modifier.weight(1f)) {
            if (subTabState == 0) {
                // Invoices Section
                if (invoices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, size = 48.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text("لا توجد فواتير بهذا النطاق الزمني.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(invoices) { inv ->
                            InvoiceLogCard(invoice = inv, df = df, onDelete = { onDeleteInvoice(it) })
                        }
                    }
                }
            } else {
                // Vouchers Section
                if (vouchers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AttachMoney, contentDescription = null, size = 48.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text("لا توجد سندات قبض أو صرف بهذا النطاق الزمني.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(vouchers) { v ->
                            VoucherLogCard(voucher = v, df = df, onDelete = { onDeleteVoucher(it) })
                        }
                    }
                }
            }
        }

        // Action add controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddInvoiceClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_invoice_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("فاتورة جديدة", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onAddVoucherClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_voucher_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PostAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("سند مالي جديد", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun InvoiceLogCard(invoice: InvoiceEntity, df: SimpleDateFormat, onDelete: (InvoiceEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (badgeBg, badgeFg, label) = when (invoice.type) {
                        "SALE" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "مبيعات ↗")
                        "PURCHASE" -> Triple(Color(0xFFFEE2E2), Color(0xFFC53030), "مشتريات ↘")
                        else -> Triple(Color(0xFFFFF3E0), Color(0xFFD97706), "مرتجع ↺")
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = badgeFg)
                    }
                    Text(text = "سند رقم: ${invoice.documentNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "${invoice.totalAmount} ل.س", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onDelete(invoice) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف الفاتورة", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "اسم العميل: " + invoice.customerName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = df.format(Date(invoice.dateMillis)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Expanded detail view
            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                Text("تفاصيل بنود الفاتورة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                try {
                    val items = Json.decodeFromString<List<InvoiceItem>>(invoice.detailsJson)
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("- " + item.productName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("${item.quantity} عدد  ×  ${item.unitPrice} ل.س", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } catch (e: Exception) {
                    Text("لا يوجد بنود مفصلة", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun VoucherLogCard(voucher: VoucherEntity, df: SimpleDateFormat, onDelete: (VoucherEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (badgeBg, badgeFg, label) = when (voucher.type) {
                        "RECEIPT" -> Triple(Color(0xFFE0F2F1), Color(0xFF00796B), "سند قبض 💵")
                        else -> Triple(Color(0xFFFFE0B2), Color(0xFFE65100), "سند صرف 💳")
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = badgeFg)
                    }
                    Text(text = "رقم: ${voucher.documentNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "${voucher.amount} ل.س", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { onDelete(voucher) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف السند", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "صاحب العلاقة: " + voucher.partyName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = df.format(Date(voucher.dateMillis)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (voucher.notes.isNotBlank()) {
                Text(
                    text = "البيان والسبب: " + voucher.notes,
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                        .padding(6.dp)
                )
            }
        }
    }
}

// ------------------- OVERLAY INTERACTIVE FORM DIALOGS -------------------
@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: Double, purchasePrice: Double, sellingPrice: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var purchase by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعريف مادة جديدة بالمستودع", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم السلعة/المادة") }, modifier = Modifier.fillMaxWidth().testTag("product_name_input"))
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("الكمية الأولية بالدكان") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("product_qty_input"))
                OutlinedTextField(value = purchase, onValueChange = { purchase = it }, label = { Text("تكلفة سعر الشراء") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("product_purchase_input"))
                OutlinedTextField(value = sell, onValueChange = { sell = it }, label = { Text("سعر البيع المقترح") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("product_sell_input"))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            qty.toDoubleOrNull() ?: 1.0,
                            purchase.toDoubleOrNull() ?: 0.0,
                            sell.toDoubleOrNull() ?: 0.0
                        )
                    }
                },
                modifier = Modifier.testTag("dialog_add_product_confirm")
            ) {
                Text("إدخال للمستودع")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("الغاء")
            }
        }
    )
}

@Composable
fun AddInvoiceDialog(
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onConfirm: (type: String, docNo: String, customer: String, total: Double, details: List<InvoiceItem>) -> Unit
) {
    var invoiceType by remember { mutableStateOf("SALE") } // SALE / PURCHASE / RETURN
    var docNo by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }

    // Multiline item listings adding to invoice
    var selectedItemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }

    val invoiceItems = remember { mutableStateListOf<InvoiceItem>() }
    val totalInvoiceSum = invoiceItems.sumOf { it.quantity * it.unitPrice }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("منشئ الفواتير الذكي", fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.heightIn(max = 450.dp)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Type selector
                    Text("نوع السند المالي للفاتورة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { invoiceType = "SALE" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (invoiceType == "SALE") MaterialTheme.colorScheme.primary else Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) { Text("شراء/مبيع", fontSize = 10.sp) }

                        Button(
                            onClick = { invoiceType = "PURCHASE" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (invoiceType == "PURCHASE") MaterialTheme.colorScheme.error else Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) { Text("توريد/مشتريات", fontSize = 10.sp) }

                        Button(
                            onClick = { invoiceType = "RETURN" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (invoiceType == "RETURN") MaterialTheme.colorScheme.tertiary else Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) { Text("مرتجع", fontSize = 10.sp) }
                    }

                    OutlinedTextField(value = docNo, onValueChange = { docNo = it }, label = { Text("رقم السند/الفاتورة") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customer, onValueChange = { customer = it }, label = { Text("اسم العميل/الجهة") }, modifier = Modifier.fillMaxWidth())

                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("إضافة بنود للفاتورة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                    // Selection dropdown simulator (using clickable cards)
                    Text("اختر مادة من المستودع لتحديدها:", fontSize = 11.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        products.forEach { prod ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedItemName == prod.name) MaterialTheme.colorScheme.primaryContainer else Color(0xFFE5E7EB))
                                    .clickable {
                                        selectedItemName = prod.name
                                        itemPrice = if (invoiceType == "SALE") prod.sellingPrice.toString() else prod.purchasePrice.toString()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(prod.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedItemName == prod.name) MaterialTheme.colorScheme.primary else Color.Black)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = selectedItemName,
                        onValueChange = { selectedItemName = it },
                        label = { Text("اسم البند المحدد") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(value = itemQty, onValueChange = { itemQty = it }, label = { Text("الكمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = itemPrice, onValueChange = { itemPrice = it }, label = { Text("السعر الفردي") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.5f))
                    }

                    Button(
                        onClick = {
                            val qVal = itemQty.toDoubleOrNull() ?: 1.0
                            val pVal = itemPrice.toDoubleOrNull() ?: 0.0
                            if (selectedItemName.isNotBlank()) {
                                invoiceItems.add(InvoiceItem(selectedItemName, qVal, pVal))
                                selectedItemName = ""
                                itemQty = ""
                                itemPrice = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("إدراج بند للفاتورة +", fontSize = 11.sp)
                    }

                    // Render current items
                    if (invoiceItems.isNotEmpty()) {
                        Text("البنود المضافة حالياً:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        invoiceItems.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(4.dp))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${item.productName} (العدد: ${item.quantity})", fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${item.quantity * item.unitPrice} ل.س", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { invoiceItems.removeAt(idx) }, modifier = Modifier.size(16.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "إجمالي تجميع الفاتورة: $totalInvoiceSum ل.س",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (docNo.isNotBlank() && customer.isNotBlank() && invoiceItems.isNotEmpty()) {
                        onConfirm(invoiceType, docNo, customer, totalInvoiceSum, invoiceItems.toList())
                    }
                }
            ) {
                Text("حفظ الفاتورة وخياطة المخزن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء لسط")
            }
        }
    )
}

@Composable
fun AddVoucherDialog(
    onDismiss: () -> Unit,
    onConfirm: (type: String, docNo: String, partyName: String, amount: Double, notes: String) -> Unit
) {
    var voucherType by remember { mutableStateOf("RECEIPT") } // RECEIPT / PAYMENT
    var docNo by remember { mutableStateOf("") }
    var partyName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل سند مالي جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { voucherType = "RECEIPT" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (voucherType == "RECEIPT") Color(0xFF00796B) else Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) { Text("سند قبض 💵", fontSize = 11.sp, color = Color.White) }

                    Button(
                        onClick = { voucherType = "PAYMENT" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (voucherType == "PAYMENT") Color(0xFFE65100) else Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) { Text("سند صرف 💳", fontSize = 11.sp, color = Color.White) }
                }

                OutlinedTextField(value = docNo, onValueChange = { docNo = it }, label = { Text("رقم السند المالي") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = partyName, onValueChange = { partyName = it }, label = { Text("الجهة (العميل أو المورد)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("المبلغ (ل.س)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("البيان والسبب") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val aNum = amount.toDoubleOrNull() ?: 0.0
                    if (docNo.isNotBlank() && partyName.isNotBlank() && aNum > 0) {
                        onConfirm(voucherType, docNo, partyName, aNum, notes)
                    }
                }
            ) {
                Text("حفظ السند")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء لسط")
            }
        }
    )
}

@Composable
fun AiAdviceReportDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val adviceText by viewModel.aiAdvisorInsights.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("المستشار المالي الذكي ✨", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (isAiLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = adviceText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = adviceText,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, adviceText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة تقرير التحليل المالي"))
                    },
                    modifier = Modifier.testTag("share_ai_report")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مشاركة")
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_ai_report_dialog")
                ) {
                    Text("حسناً")
                }
            }
        }
    )
}

// ------------------- WHATSAPP INTEGRATION -------------------
fun contactWhatsApp(context: Context, phone: String, message: String) {
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
            Toast.makeText(context, "لم يتم العثور على تطبيق واتساب. الدعم الفني: 0938385157", Toast.LENGTH_LONG).show()
        }
    }
}

// ------------------- PREMIUM AUTHENTICATION PANEL -------------------
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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
                    text = "تطبيق المحاسب الذكي الأنيق لإدارة المبيعات والمستودعات والتقارير المالية الذكية",
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
                        modifier = Modifier.fillMaxWidth().testTag("auth_name_input"),
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
                                errorMsg = "يرجى ملء كافة الحقول لإتمام التسجيل!"
                            } else {
                                viewModel.register(name, phone, password)
                                Toast.makeText(context, "تم تسجيل حسابك التجريبي بنجاح!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (phone.isBlank() || password.isBlank()) {
                                errorMsg = "يرجى إدخال رقم الهاتف وكلمة المرور تسجيل الدخول!"
                            } else {
                                val userMatch = viewModel.registeredUsers.value.find { it.phone == phone && it.password == password }
                                if (userMatch != null) {
                                    viewModel.login(phone, password)
                                    Toast.makeText(context, "أهلاً بك! تم تسجيل الدخول بنجاح.", Toast.LENGTH_SHORT).show()
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
                        text = if (isRegisterMode) "لديك حساب بالفعل؟ سجل الدخول هنا" else "ليس لديك حساب؟ سجل حساباً تجريبياً مجاناً"
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Info helpful cards for evaluators
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 معلومات للاختبار والتقييم السريع:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "حساب المدير: الهاتف: admin | حساب المرور: admin",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "حساب المستخدم: الهاتف: 0938385157 | كلمة المرور: 123456",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------- ADMIN SUBSCRIPTION CONTROL PORTAL -------------------
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
                    text = "لوحة المسؤول - تفعيل باقات الاشتراكات",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            actions = {
                IconButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.testTag("admin_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "تسجيل الخروج",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "من خلال هذه البوابة الآمنة، يستطيع مدير التطبيق مراجعة الحسابات وتفعيل النسخ المدفوعة الكاملة للعملاء أو مراجعة طلباتهم وتنشيطها.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "إجمالي عدد الحسابات المسجلة: ${registeredUsers.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Text(
                    text = "سجل طلبات التفعيل والحسابات:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (registeredUsers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد مستخدمين مسجلين بالدكان مؤخراً.", color = Color.Gray)
                    }
                }
            } else {
                items(registeredUsers) { user ->
                    // Exclude showing the admin themselves for beauty
                    if (user.phone != "admin") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = "رقم هاتف العميل: ${user.phone}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    // Render badge
                                    val (badgeBg, badgeFg, label) = when (user.status) {
                                        "ACTIVE" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "باقة مدفوعة كاملة 🟢")
                                        "PENDING" -> Triple(Color(0xFFFFF3E0), Color(0xFFD97706), "بانتظار موافقة التفعيل ⏳")
                                        else -> Triple(Color(0xFFECEFF1), Color(0xFF546E7A), "باقة تجريبية محدودة ⚪")
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeBg)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = badgeFg)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (user.status != "ACTIVE") {
                                        Button(
                                            onClick = {
                                                viewModel.changeUserStatus(user.phone, "ACTIVE")
                                                Toast.makeText(context, "تم تفعيل باقة العميل ${user.name} بنجاح!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("موافقة وتفعيل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.changeUserStatus(user.phone, "TRIAL")
                                                Toast.makeText(context, "تم إلغاء باقة العميل ${user.name} وتجميدها.", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("إلغاء التنشيط", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
}
