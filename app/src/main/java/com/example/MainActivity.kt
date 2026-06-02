package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.*
import com.example.ui.AppViewModel
import kotlinx.coroutines.launch
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

// Global Material 3 Color Theme
@Composable
fun SmartAccountantTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF0FBD95), // Jade Green
        secondary = Color(0xFF1E3A8A), // Deep Slate Navy
        tertiary = Color(0xFFEAB308), // Golden Amber
        background = Color(0xFF0D0E12), // Cosmic Blue Obsidian
        surface = Color(0xFF161920), // Card Blue Charcoal
        onPrimary = Color(0xFF000000),
        onSecondary = Color(0xFFFFFFFF),
        onBackground = Color(0xFFE2E8F0),
        onSurface = Color(0xFFF1F5F9),
        error = Color(0xFFEF4444)
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountantAppContent() {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel()
    val scope = rememberCoroutineScope()

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val activeCompany by viewModel.activeCompany.collectAsState()
    
    val companies by viewModel.allCompanies.collectAsState()
    val currencies by viewModel.currencies.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val products by viewModel.products.collectAsState()
    val invoices by viewModel.invoices.collectAsState()
    val vouchers by viewModel.vouchers.collectAsState()
    val attendance by viewModel.attendance.collectAsState()
    val manufacturing by viewModel.manufacturing.collectAsState()

    // Screen states
    var selectedTab by remember { mutableStateOf(0) } // 0: الرئيسيّة, 1: الحسابات, 2: المخزون, 3: العمليات المالية, 4: التصنيع والدوام, 5: التقارير والنسخ

    // Export launch actions
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            val success = viewModel.exportReportToCsv(context, it)
            if (success) {
                Toast.makeText(context, "تم تصدير كشف الحساب والتحليل المالي بنجاح بصيغة CSV!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "فشل تصدير التقرير", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val success = viewModel.exportBackup(context, it)
            if (success) {
                Toast.makeText(context, "تم حفظ النسخة الاحتياطية بنجاح!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "فشل حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val success = viewModel.importBackup(context, it)
                if (success) {
                    Toast.makeText(context, "تم استعادة كامل البيانات والمستندات بنجاح من النسخة المحددة!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل استعادة البيانات من الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (isLoggedIn) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val tabItems = listOf(
                        Triple("الرئيسية", Icons.Default.Dashboard, 0),
                        Triple("الحسابات", Icons.Default.PeopleAlt, 1),
                        Triple("المخزون", Icons.Default.Inventory2, 2),
                        Triple("المالية", Icons.Default.ReceiptLong, 3),
                        Triple("التصنيع", Icons.Default.Factory, 4),
                        Triple("التقارير", Icons.Default.Analytics, 5)
                    )
                    tabItems.forEach { (title, icon, index) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, contentDescription = title) },
                            label = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (!isLoggedIn) {
                LoginScreen(onLoginSuccess = { phone, pass ->
                    val success = viewModel.login(phone, pass)
                    if (!success) {
                        Toast.makeText(context, "عذراً! هاتف المدير أو كلمة المرور غير صحيحة.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "تم الدخول بنجاح بصفتك محاسب معتمد!", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                // Main Application Hub
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Modern Multi-Company Header Selection Bar
                    CompanyHeaderSelector(
                        activeCompany = activeCompany,
                        companies = companies,
                        onSelect = { viewModel.selectCompany(it) },
                        onAddCompany = { name, curr -> viewModel.addCompany(name, curr) },
                        onDeleteCompany = { viewModel.deleteCompany(it) },
                        onLogout = { viewModel.logout() }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                    // Dynamic tab contents
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> DashboardScreen(
                                activeCompany = activeCompany,
                                products = products,
                                invoices = invoices,
                                vouchers = vouchers,
                                accounts = accounts,
                                viewModel = viewModel
                            )
                            1 -> AccountsScreen(
                                activeCompany = activeCompany,
                                accounts = accounts,
                                currencies = currencies,
                                viewModel = viewModel,
                                onExportCsv = { exportCsvLauncher.launch("report_export_${System.currentTimeMillis()}.csv") }
                            )
                            2 -> StockScreen(
                                activeCompany = activeCompany,
                                products = products,
                                viewModel = viewModel
                            )
                            3 -> FinancialsScreen(
                                activeCompany = activeCompany,
                                accounts = accounts,
                                currencies = currencies,
                                products = products,
                                invoices = invoices,
                                vouchers = vouchers,
                                viewModel = viewModel
                            )
                            4 -> OperationsScreen(
                                activeCompany = activeCompany,
                                products = products,
                                manufacturing = manufacturing,
                                attendance = attendance,
                                viewModel = viewModel
                            )
                            5 -> ReportsScreen(
                                activeCompany = activeCompany,
                                products = products,
                                accounts = accounts,
                                invoices = invoices,
                                vouchers = vouchers,
                                attendance = attendance,
                                viewModel = viewModel,
                                onSaveBackup = { exportBackupLauncher.launch("smart_accountant_backup_${System.currentTimeMillis()}.json") },
                                onLoadBackup = { importBackupLauncher.launch(arrayOf("application/json")) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 1. LOGIN SCREEN COMPONENT
// ---------------------------------------------------------
@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    var phone by remember { mutableStateOf("9933210618") }
    var password by remember { mutableStateOf("123456") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0FBD95).copy(alpha = 0.15f), Color(0xFF0D0E12))
                )
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Logo Icon
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AccountBalance,
                contentDescription = "Logo",
                modifier = Modifier.size(54.dp),
                tint = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "المُحَاسِب الذّكِي الأنِيق",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            "نظام مالي واحترافي متعدد الشركات ومتعدد العملات",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "بوابة تسجيل الدخول الآمنة للمحاسب",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم هاتف المدير") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("phone_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("رمز المرور") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = { onLoginSuccess(phone, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_login"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("دخـول الـنـظـام", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "رقم الأدمن التجريبي: 9933210618 | الرمز: 123456",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    }
}

// ---------------------------------------------------------
// 2. COMPANY HEADER SELECTOR
// ---------------------------------------------------------
@Composable
fun CompanyHeaderSelector(
    activeCompany: CompanyEntity?,
    companies: List<CompanyEntity>,
    onSelect: (CompanyEntity) -> Unit,
    onAddCompany: (String, String) -> Unit,
    onDeleteCompany: (CompanyEntity) -> Unit,
    onLogout: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onLogout) {
            Icon(Icons.Default.ExitToApp, contentDescription = "تسجيل خروج", tint = MaterialTheme.colorScheme.error)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDropdown = true }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "سهم")
            Spacer(modifier = Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    activeCompany?.name ?: "اختر الشركة المحاسبية",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "العملة المحلية: ${activeCompany?.currencyLocal ?: "بلا"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.Business, contentDescription = "مؤسسة", tint = MaterialTheme.colorScheme.primary)
        }
    }

    if (showDropdown) {
        Dialog(onDismissRequest = { showDropdown = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                showDropdown = false
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("شركة جديدة", color = Color.Black, fontSize = 12.sp)
                        }
                        Text("الشركات النشطة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(companies) { company ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (company.id == activeCompany?.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickable {
                                        onSelect(company)
                                        showDropdown = false
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (companies.size > 1) {
                                    IconButton(onClick = { onDeleteCompany(company) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color.Red)
                                    }
                                } else {
                                    Box(modifier = Modifier.size(24.dp))
                                }

                                Text(
                                    "${company.name} (${company.currencyLocal})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (company.id == activeCompany?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showDropdown = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إغـلاق")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var cName by remember { mutableStateOf("") }
        var cCurrency by remember { mutableStateOf("SYP") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("إضافة شركة جديدة للمكتب", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    OutlinedTextField(
                        value = cName,
                        onValueChange = { cName = it },
                        label = { Text("اسم الشركة أو النشاط التجاري") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = cCurrency,
                        onValueChange = { cCurrency = it },
                        label = { Text("رمز العملة المحلية المهيمنة (مثل SYP او USD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                if (cName.isNotBlank() && cCurrency.isNotBlank()) {
                                    onAddCompany(cName, cCurrency)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 3. DASHBOARD TAB
// ---------------------------------------------------------
@Composable
fun DashboardScreen(
    activeCompany: CompanyEntity?,
    products: List<ProductEntity>,
    invoices: List<InvoiceEntity>,
    vouchers: List<VoucherEntity>,
    accounts: List<AccountEntity>,
    viewModel: AppViewModel
) {
    val aiResponse by viewModel.aiResponse.collectAsState()
    val isEvaluatingReport by viewModel.isEvaluatingReport.collectAsState()

    val plData = remember(invoices, vouchers, products) { viewModel.getDynamicProfitAndLoss() }
    val lowStockCount = remember(products) { products.filter { it.stockQuantity <= it.lowStockThreshold }.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Welcoming Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        activeCompany?.name ?: "المجموعة المحاسبية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "مكتبك الذكي جاهز لإدارة فواتير البيع، الشراء والعمليات الصناعية المحترفة.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text("الإحصاءات السريعة (بالعملة المحلية)", fontWeight = FontWeight.Bold, fontSize = 15.sp)

        // Statistics Grid (Custom Column Grid)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "صافي المبيعات",
                value = "${plData["netSales"]} ${activeCompany?.currencyLocal}",
                icon = Icons.Default.PriceCheck,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "الربح الصافي",
                value = "${plData["netProfit"]} ${activeCompany?.currencyLocal}",
                icon = Icons.Default.Payments,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = "الإيرادات الإضافية",
                value = "${plData["revenues"]} ${activeCompany?.currencyLocal}",
                icon = Icons.Default.AddBusiness,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "سلع توشك على النفاد",
                value = "$lowStockCount منتج",
                icon = Icons.Default.Warning,
                color = if (lowStockCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Gemini Intelligent Financial Adviser Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEvaluatingReport) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        IconButton(onClick = { viewModel.requestAiConsultation() }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "تحليلات", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("مستشار مالي ذكي (Gemini)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                if (aiResponse.isBlank()) {
                    Text(
                        "اضغط على زر الذكاء الاصطناعي لتشخيص الميزانية والحصول على كشف استرشادي دقيق لرفع الأرباح وإدارة عملاتك التجارية.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        aiResponse,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------------------------------------------------------
// 4. ACCOUNTS & CURRENCIES TAB
// ---------------------------------------------------------
@Composable
fun AccountsScreen(
    activeCompany: CompanyEntity?,
    accounts: List<AccountEntity>,
    currencies: List<CurrencyEntity>,
    viewModel: AppViewModel,
    onExportCsv: () -> Unit
) {
    var showAddAccount by remember { mutableStateOf(false) }
    var showAddCurrency by remember { mutableStateOf(false) }
    var accountStatementAccount by remember { mutableStateOf<AccountEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showAddCurrency = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CurrencyExchange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("إعدادات العملات", fontSize = 12.sp)
            }

            Button(
                onClick = { showAddAccount = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("إضافة حساب", color = Color.Black, fontSize = 12.sp)
            }
        }

        // Active Currencies Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                Text("أسعار الصرف (معادل المحلية)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    items(currencies) { c ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("${c.code}: ${c.rateToLocal} ${activeCompany?.currencyLocal}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExportCsv) {
                Icon(Icons.Default.FileDownload, contentDescription = "تصدير كشف حساب", tint = MaterialTheme.colorScheme.primary)
            }
            Text("قائمة الحسابات والعملاء", fontWeight = FontWeight.Black, fontSize = 15.sp)
        }

        // Accounts list
        if (accounts.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("لا توجد حسابات مسجلة حالياً.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { acc ->
                    val balance = viewModel.getAccountBalance(acc.id)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left balance and details button
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { accountStatementAccount = acc },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("كشف الحساب", fontSize = 10.sp)
                                }

                                Text(
                                    String.format("%.2f %s", balance, acc.currencyCode),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = if (balance >= 0) MaterialTheme.colorScheme.primary else Color.Red
                                )
                            }

                            // Right content Name and Type
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        acc.type,
                                        fontSize = 11.sp,
                                        color = when (acc.type) {
                                            "زبون" -> MaterialTheme.colorScheme.primary
                                            "مورد" -> MaterialTheme.colorScheme.tertiary
                                            "مصاريف" -> Color.Red
                                            else -> Color.Cyan
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(acc.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                if (acc.phone.isNotBlank()) {
                                    Text(acc.phone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Account Dialog
    if (showAddAccount) {
        var name by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf("زبون") }
        var currencyCode by remember { mutableStateOf(activeCompany?.currencyLocal ?: "SYP") }
        var phone by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddAccount = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إضافة كارت حساب مالي جديد", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("الاسم الكامل للحساب") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Type Row Selection
                    Text("نوع التصنيف الحسابي:", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("زبون", "مورد", "مصاريف", "ايرادات").forEach { t ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selectedType == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                                    .clickable { selectedType = t }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(t, fontSize = 11.sp, color = if (selectedType == t) Color.Black else Color.White)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = currencyCode,
                        onValueChange = { currencyCode = it },
                        label = { Text("رمز العملة الخاصة بالتعامل") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات إضافية") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddAccount = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.addAccount(name, selectedType, currencyCode, phone, notes)
                                    showAddAccount = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Add/Manage Currencies rate Dialog
    if (showAddCurrency) {
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var rate by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddCurrency = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("تهيئة العملات وأسعار الصرف", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("رمز العملة الاختصاري (مثل USD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم العملة (مثل دولار أمريكي)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = it },
                        label = { Text("معادل العملة المحلية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddCurrency = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f)) {
                            Text("الخروج")
                        }
                        Button(
                            onClick = {
                                val dRate = rate.toDoubleOrNull()
                                if (code.isNotBlank() && name.isNotBlank() && dRate != null) {
                                    viewModel.addCurrency(code, name, dRate)
                                    code = ""
                                    name = ""
                                    rate = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تسجيل", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Account statement simulator
    if (accountStatementAccount != null) {
        val acc = accountStatementAccount!!
        val listVouchers = viewModel.vouchers.collectAsState().value.filter { it.accountId == acc.id }
        val listInvoices = viewModel.invoices.collectAsState().value.filter { it.accountId == acc.id }

        Dialog(onDismissRequest = { accountStatementAccount = null }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("كشف حساب مالي رسمي", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("الحساب: ${acc.name} (${acc.type}) - العملة: ${acc.currencyCode}", fontSize = 12.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())

                    HorizontalDivider()

                    LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listInvoices) { inv ->
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${inv.totalAmountForeign} ${inv.currencyCode}", color = MaterialTheme.colorScheme.primary)
                                Text("فاتورة ${inv.type} رقم ${inv.invoiceNumber}", fontSize = 12.sp)
                            }
                        }
                        items(listVouchers) { v ->
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${v.amountForeign} ${v.currencyCode}", color = MaterialTheme.colorScheme.tertiary)
                                Text("سند ${v.type} (${v.notes})", fontSize = 12.sp)
                            }
                        }
                    }

                    Button(
                        onClick = { accountStatementAccount = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إغلاق الكشف")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 5. INVENTORY & BARCODE TAB
// ---------------------------------------------------------
@Composable
fun StockScreen(
    activeCompany: CompanyEntity?,
    products: List<ProductEntity>,
    viewModel: AppViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedProductForBarcode by remember { mutableStateOf<ProductEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("مادة جديدة للشركة", color = Color.Black, fontSize = 12.sp)
            }

            Text("مستودع السلع ومقاييس المخزون", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (products.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("المخزن فارغ حالياً، قم بتسجيل وتصنيف السلع.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products) { item ->
                    val isLowStock = item.stockQuantity <= item.lowStockThreshold
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = if (isLowStock) BorderStroke(1.dp, Color.Red) else null
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(onClick = { selectedProductForBarcode = item }) {
                                        Icon(Icons.Default.QrCode, contentDescription = "Barcode", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        "${item.stockQuantity} ${item.unit}",
                                        fontWeight = FontWeight.Black,
                                        color = if (isLowStock) Color.Red else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("التصنيف: ${item.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("سعر المبيع: ${item.sellingPrice} ل.س", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("سعر التكلفة: ${item.costPrice} ل.س", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isLowStock) {
                                Text(
                                    "تنبيه: لقد شارف المخزون على النفاد!",
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Product dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("قطعة") }
        var costPrice by remember { mutableStateOf("") }
        var sellingPrice by remember { mutableStateOf("") }
        var barcode by remember { mutableStateOf("") }
        var stockQuantity by remember { mutableStateOf("") }
        var lowThreshold by remember { mutableStateOf("5.0") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إضافة مادة مخزنية جديدة", fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المادة") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("تصنيف المجموعة (مثل قطع غيار، بضاعة)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("وحدة القياس (قطعة، متر، كرتونة)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("سعر التكلفة الافتراضي ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = it }, label = { Text("سعر المبيع المتوقع ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("الباركود (أو اتركه فارغاً)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = stockQuantity, onValueChange = { stockQuantity = it }, label = { Text("الكمية المتوفرة الحالية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = lowThreshold, onValueChange = { lowThreshold = it }, label = { Text("حد التنبيه عند النفاذ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val cP = costPrice.toDoubleOrNull() ?: 0.0
                                val sP = sellingPrice.toDoubleOrNull() ?: 0.0
                                val sQ = stockQuantity.toDoubleOrNull() ?: 0.0
                                val lT = lowThreshold.toDoubleOrNull() ?: 5.0
                                if (name.isNotBlank()) {
                                    viewModel.addProduct(name, category.ifBlank { "عام" }, unit, cP, sP, barcode.ifBlank { System.currentTimeMillis().toString() }, sQ, lT)
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Thermal barcode generator simulator popup
    if (selectedProductForBarcode != null) {
        val prod = selectedProductForBarcode!!
        Dialog(onDismissRequest = { selectedProductForBarcode = null }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("معاينة ملصق الباركود الحراري", fontWeight = FontWeight.Bold)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(prod.name, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text("السعر: ${prod.sellingPrice} ل.س", color = Color.Black, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Canvas simulator bar lines
                            Canvas(modifier = Modifier.height(60.dp).width(160.dp)) {
                                val stepWidth = size.width / 24
                                for (i in 0..24) {
                                    val startX = i * stepWidth
                                    val isBar = (i % 2 == 0) || (i % 7 == 0)
                                    if (isBar) {
                                        drawLine(
                                            color = Color.Black,
                                            start = Offset(startX, 0f),
                                            end = Offset(startX, size.height),
                                            strokeWidth = if (i % 3 == 0) 6f else 3f
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(prod.barcode, color = Color.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedProductForBarcode = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                // Simulate sending barcode parameters to thermal printer
                                selectedProductForBarcode = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("طباعة حرارية", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 6. TRANSACTIONS TAB (INVOICES & VOUCHERS)
// ---------------------------------------------------------
@Composable
fun FinancialsScreen(
    activeCompany: CompanyEntity?,
    accounts: List<AccountEntity>,
    currencies: List<CurrencyEntity>,
    products: List<ProductEntity>,
    invoices: List<InvoiceEntity>,
    vouchers: List<VoucherEntity>,
    viewModel: AppViewModel
) {
    var selectedOrderType by remember { mutableStateOf(0) } // 0: الفواتير, 1: سندات الصرف والقبض

    var showInvoiceDialog by remember { mutableStateOf(false) }
    var showVoucherDialog by remember { mutableStateOf(false) }
    var selectedInvoiceToPrint by remember { mutableStateOf<InvoiceEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Selector bar
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { selectedOrderType = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedOrderType == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("الفواتير التجارية", color = if (selectedOrderType == 0) Color.Black else Color.White, fontSize = 12.sp)
            }
            Button(
                onClick = { selectedOrderType = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedOrderType == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("سندات القبض والدفع", color = if (selectedOrderType == 1) Color.Black else Color.White, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (selectedOrderType == 0) showInvoiceDialog = true else showVoucherDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (selectedOrderType == 0) "فاتورة مبيع/شراء" else "سند دفع/قبض جديد", color = Color.Black, fontSize = 11.sp)
            }

            Text(if (selectedOrderType == 0) "إرشيف الفواتير الشامل" else "سجل السندات والتدفقات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (selectedOrderType == 0) {
            if (invoices.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد فواتير مسجلة حالياً.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(invoices) { inv ->
                        val clientName = accounts.find { it.id == inv.accountId }?.name ?: "مجهول"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { selectedInvoiceToPrint = inv }) {
                                        Icon(Icons.Default.Print, contentDescription = "طباعة حرارية", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("فاتورة ${inv.type} #${inv.invoiceNumber}", fontWeight = FontWeight.Black)
                                        Text("الجهة: $clientName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("القيمة: ${inv.totalAmountLocal} ${activeCompany?.currencyLocal}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("العملة: ${inv.totalAmountForeign} ${inv.currencyCode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (vouchers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد سندات مسجلة حالياً.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vouchers) { v ->
                        val accName = accounts.find { it.id == v.accountId }?.name ?: "مجهول"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    IconButton(onClick = { viewModel.deleteVoucher(v) }) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = "حذف", tint = Color.Red)
                                    }
                                    Text("سند ${v.type}", fontWeight = FontWeight.Black, color = if (v.type == "قبض") MaterialTheme.colorScheme.primary else Color.Red)
                                }
                                Text("الحساب المرتبط: $accName", fontSize = 12.sp)
                                Text("قيمة السند: ${v.amountForeign} ${v.currencyCode}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("ملاحظة: ${v.notes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Invoice Dialog
    if (showInvoiceDialog) {
        var invoiceType by remember { mutableStateOf("مبيع") } // مبيع، شراء، مردود مبيعات، مردود مشتريات، إتلاف
        var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0) }
        var currencySelected by remember { mutableStateOf("SYP") }
        var exchangeRate by remember { mutableStateOf("1.0") }
        var discountAmt by remember { mutableStateOf("0.0") }

        // Cart items build helper
        var currentItemProduct by remember { mutableStateOf<ProductEntity?>(products.firstOrNull()) }
        var currentItemQty by remember { mutableStateOf("1.0") }
        var currentItemPrice by remember { mutableStateOf("") }
        val cartItems = remember { mutableStateListOf<InvoiceItem>() }

        Dialog(onDismissRequest = { showInvoiceDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(6.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("تسجيل فاتورة تجارية رسمية", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    // Type Selector Row
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(listOf("مبيع", "شراء", "مردود مبيعات", "مردود مشتريات", "إتلاف")) { t ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (invoiceType == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                                    .clickable { invoiceType = t }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(t, fontSize = 11.sp, color = if (invoiceType == t) Color.Black else Color.White)
                            }
                        }
                    }

                    // Account Selector
                    Text("الحساب العميل/المورد:", fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(accounts) { acc ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selectedAccountId == acc.id) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        selectedAccountId = acc.id
                                        currencySelected = acc.currencyCode
                                        val curRate = currencies.find { it.code == acc.currencyCode }?.rateToLocal ?: 1.0
                                        exchangeRate = curRate.toString()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("${acc.name} (${acc.type})", fontSize = 11.sp)
                            }
                        }
                    }

                    // Exchange rates & foreign indicators
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = currencySelected,
                            onValueChange = { currencySelected = it },
                            label = { Text("عملة الفاتورة") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = exchangeRate,
                            onValueChange = { exchangeRate = it },
                            label = { Text("سعر الصرف") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = discountAmt,
                        onValueChange = { discountAmt = it },
                        label = { Text("الخصم المسموح به") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    // Add item section
                    Text("بنود المواد في الفاتورة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    if (products.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("اختر المادة:")
                            LazyRow(modifier = Modifier.fillMaxWidth()) {
                                items(products) { p ->
                                    Box(
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (currentItemProduct?.id == p.id) MaterialTheme.colorScheme.primary else Color.DarkGray)
                                            .clickable {
                                                currentItemProduct = p
                                                currentItemPrice = p.sellingPrice.toString()
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Text(p.name, fontSize = 11.sp, color = if (currentItemProduct?.id == p.id) Color.Black else Color.White)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = currentItemQty,
                                onValueChange = { currentItemQty = it },
                                label = { Text("الكمية") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = currentItemPrice,
                                onValueChange = { currentItemPrice = it },
                                label = { Text("سعر الوحدة") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                val qtyVal = currentItemQty.toDoubleOrNull() ?: 1.0
                                val pVal = currentItemPrice.toDoubleOrNull() ?: 0.0
                                val pName = currentItemProduct?.name ?: "بضاعة"
                                cartItems.add(InvoiceItem(name = pName, quantity = qtyVal, unitPrice = pVal))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("إضافة البند للسلة", fontSize = 11.sp)
                        }
                    }

                    // Render current items in cart
                    cartItems.forEachIndexed { idx, itm ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { cartItems.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                            }
                            Text("${itm.quantity} ${itm.name} @ ${itm.unitPrice}", fontSize = 11.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showInvoiceDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f)) {
                            Text("رجوع")
                        }
                        Button(
                            onClick = {
                                if (selectedAccountId != 0 && cartItems.isNotEmpty()) {
                                    val rate = exchangeRate.toDoubleOrNull() ?: 1.0
                                    val disc = discountAmt.toDoubleOrNull() ?: 0.0
                                    var sumFor = 0.0
                                    cartItems.forEach { sumFor += it.quantity * itmCost(it, products) }

                                    val finalSumFor = sumFor - disc
                                    val finalSumLoc = finalSumFor * rate

                                    viewModel.addInvoice(
                                        type = invoiceType,
                                        accountId = selectedAccountId,
                                        currencyCode = currencySelected,
                                        exchangeRate = rate,
                                        discount = disc,
                                        totalAmtLoc = finalSumLoc,
                                        totalAmtFor = finalSumFor,
                                        itemsList = cartItems.toList()
                                    )
                                    showInvoiceDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تخزين", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Add Voucher Dialog
    if (showVoucherDialog) {
        var voucherType by remember { mutableStateOf("قبض") } // قبض، دفع
        var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0) }
        var currencyCode by remember { mutableStateOf("SYP") }
        var amountForeign by remember { mutableStateOf("") }
        var exchangeRate by remember { mutableStateOf("1.0") }
        var notes by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showVoucherDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إنشاء سند مالي", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { voucherType = "قبض" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (voucherType == "قبض") MaterialTheme.colorScheme.primary else Color.DarkGray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("سند قبض (قبض نقدي)", color = if (voucherType == "قبض") Color.Black else Color.White)
                        }
                        Button(
                            onClick = { voucherType = "دفع" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (voucherType == "دفع") Color.Red else Color.DarkGray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("سند دفع (صرف مالي)", color = Color.White)
                        }
                    }

                    // Account Selector
                    Text("صاحب الحساب:", fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(accounts) { acc ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selectedAccountId == acc.id) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        selectedAccountId = acc.id
                                        currencyCode = acc.currencyCode
                                        val curRate = currencies.find { it.code == acc.currencyCode }?.rateToLocal ?: 1.0
                                        exchangeRate = curRate.toString()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(acc.name, fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountForeign,
                        onValueChange = { amountForeign = it },
                        label = { Text("مبلغ الحركة المالي") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currencyCode,
                        onValueChange = { currencyCode = it },
                        label = { Text("رمز العملة") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = exchangeRate,
                        onValueChange = { exchangeRate = it },
                        label = { Text("معادل تحويل الصرف للمحلية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("البيان والسبب") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showVoucherDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f)) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val amtNum = amountForeign.toDoubleOrNull()
                                val rateNum = exchangeRate.toDoubleOrNull() ?: 1.0
                                if (selectedAccountId != 0 && amtNum != null) {
                                    viewModel.addVoucher(voucherType, selectedAccountId, amtNum, currencyCode, rateNum, notes)
                                    showVoucherDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ السند", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Thermal Printer invoice layout preview dialog
    if (selectedInvoiceToPrint != null) {
        val inv = selectedInvoiceToPrint!!
        val details = try {
            Json.decodeFromString<List<InvoiceItem>>(inv.detailsJson)
        } catch (e: Exception) {
            emptyList<InvoiceItem>()
        }
        val client = accounts.find { it.id == inv.accountId }?.name ?: "مجهول"

        Dialog(onDismissRequest = { selectedInvoiceToPrint = null }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("معاينة الفاتورة الحرارية (80 مم)", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("***** ${activeCompany?.name} *****", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("كشف فاتورة ${inv.type} حرارية", color = Color.Black, fontSize = 11.sp)
                            Text("رقم الفاتورة التلقائي: ${inv.invoiceNumber}", color = Color.Black, fontSize = 11.sp)
                            Text("التاريخ: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(inv.date))}", color = Color.Black, fontSize = 10.sp)
                            Text("موجّهة إلى: $client", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("--------------------------------", color = Color.Black)

                            details.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${item.quantity} x ${item.unitPrice}", color = Color.Black, fontSize = 11.sp)
                                    Text(item.name, color = Color.Black, fontSize = 11.sp)
                                }
                            }

                            Text("--------------------------------", color = Color.Black)
                            Text("الخصم الإجمالي: ${inv.discount} ${inv.currencyCode}", color = Color.Black, fontSize = 11.sp)
                            Text("قيمة الصافي: ${inv.totalAmountForeign} ${inv.currencyCode}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Text("صافي بالمحلية: ${inv.totalAmountLocal} ${activeCompany?.currencyLocal}", color = Color.Black, fontSize = 11.sp)

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("شكراً لثقتكم بنا!", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { selectedInvoiceToPrint = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إرسال للطابعة الحرارية وإغلاق")
                    }
                }
            }
        }
    }
}

private fun itmCost(itm: InvoiceItem, products: List<ProductEntity>): Double {
    return itm.unitPrice
}

// ---------------------------------------------------------
// 7. MANUFACTURING & STAFF ATTENDANCE
// ---------------------------------------------------------
@Composable
fun OperationsScreen(
    activeCompany: CompanyEntity?,
    products: List<ProductEntity>,
    manufacturing: List<ManufacturingEntity>,
    attendance: List<AttendanceEntity>,
    viewModel: AppViewModel
) {
    var modeSelected by remember { mutableStateOf(0) } // 0: التصنيع والتركيب, 1: حضور ودوام الموظفين

    var showMfgDialog by remember { mutableStateOf(false) }
    var showAttDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector Bar
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { modeSelected = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (modeSelected == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("المعامل والصناعة", color = if (modeSelected == 0) Color.Black else Color.White, fontSize = 12.sp)
            }
            Button(
                onClick = { modeSelected = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (modeSelected == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("الموارد البشرية والدوام", color = if (modeSelected == 1) Color.Black else Color.White, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (modeSelected == 0) showMfgDialog = true else showAttDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (modeSelected == 0) "عملية صناعية" else "تسجيل حضور جديد", color = Color.Black, fontSize = 11.sp)
            }

            Text(if (modeSelected == 0) "إرشيف الإنتاج الصناعي" else "سجل مراقبة دوام الموظفين", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (modeSelected == 0) {
            if (manufacturing.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد عمليات صناعية مسجلة.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(manufacturing) { m ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.deleteManufacturing(m) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الوجبة", tint = Color.Red)
                                    }
                                    Text("إنتاج: ${m.itemName}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("الكمية المصنعة بالتسويق: ${m.quantityResult} قطعة", fontSize = 12.sp)
                                Text("تكلفة الوجبة الإجمالية: ${m.totalCostLocal} ل.س", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        } else {
            if (attendance.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا يوجد سجل حضور للموظفين حتى الآن.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attendance) { att ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    att.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (att.status) {
                                        "حاضر" -> MaterialTheme.colorScheme.primary
                                        "غائب" -> Color.Red
                                        else -> MaterialTheme.colorScheme.tertiary
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(att.employeeName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("التاريخ: ${att.date}  |  ${att.notes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manufacturing dialog
    if (showMfgDialog) {
        var finishedGoodsName by remember { mutableStateOf("") }
        var targetQty by remember { mutableStateOf("10.0") }
        var conversionCost by remember { mutableStateOf("1500") }

        // Raw items addition helper
        var selectedRawProduct by remember { mutableStateOf<ProductEntity?>(products.firstOrNull()) }
        var rawUsageQty by remember { mutableStateOf("1.0") }
        var rawEstimatedUnitPrice by remember { mutableStateOf("") }
        val finalRawList = remember { mutableStateListOf<RawMaterial>() }

        Dialog(onDismissRequest = { showMfgDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(6.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("تشغيل وجبة إنتاج كيمياوي/صناعي", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    OutlinedTextField(
                        value = finishedGoodsName,
                        onValueChange = { finishedGoodsName = it },
                        label = { Text("اسم السلعة المنتجة المرنة لتسويقها") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = targetQty,
                            onValueChange = { targetQty = it },
                            label = { Text("الكمية الناتجة") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = conversionCost,
                            onValueChange = { conversionCost = it },
                            label = { Text("أجور تصنيع مضافة") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()
                    Text("المواد الأولية الداخلة بالصناعة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    if (products.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("اختر مادة:")
                            LazyRow(modifier = Modifier.fillMaxWidth()) {
                                items(products) { p ->
                                    Box(
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (selectedRawProduct?.id == p.id) MaterialTheme.colorScheme.primary else Color.DarkGray)
                                            .clickable {
                                                selectedRawProduct = p
                                                rawEstimatedUnitPrice = p.costPrice.toString()
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Text(p.name, fontSize = 11.sp, color = if (selectedRawProduct?.id == p.id) Color.Black else Color.White)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = rawUsageQty,
                                onValueChange = { rawUsageQty = it },
                                label = { Text("المقدار المستعمل") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rawEstimatedUnitPrice,
                                onValueChange = { rawEstimatedUnitPrice = it },
                                label = { Text("أغلاق التكلفة للواحدة") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                val uQ = rawUsageQty.toDoubleOrNull() ?: 1.0
                                val uP = rawEstimatedUnitPrice.toDoubleOrNull() ?: 0.0
                                val pName = selectedRawProduct?.name ?: "مادة أولية"
                                finalRawList.add(RawMaterial(name = pName, quantity = uQ, unitPrice = uP))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("أدخل لتشكيلة التصنيع", fontSize = 11.sp)
                        }
                    }

                    // Render list components
                    finalRawList.forEachIndexed { index, raw ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { finalRawList.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "مسح", tint = Color.Red)
                            }
                            Text("${raw.quantity} من ${raw.name} بسعر ${raw.unitPrice}", fontSize = 11.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showMfgDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f)) {
                            Text("رجوع")
                        }
                        Button(
                            onClick = {
                                val qtyVal = targetQty.toDoubleOrNull() ?: 1.0
                                val extra = conversionCost.toDoubleOrNull() ?: 0.0
                                if (finishedGoodsName.isNotBlank() && finalRawList.isNotEmpty()) {
                                    viewModel.addManufacturingOperation(finishedGoodsName, qtyVal, finalRawList.toList(), extra)
                                    showMfgDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("شغّل المعمل", color = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Staff dialog
    if (showAttDialog) {
        var empName by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("حاضر") }
        var remarks by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAttDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("حضور وغياب العمال والتقييم الداخلي", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    OutlinedTextField(
                        value = empName,
                        onValueChange = { empName = it },
                        label = { Text("اسم الموظف / السائق") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("حاضر", "غائب", "إجازة").forEach { s ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (status == s) MaterialTheme.colorScheme.primary else Color.DarkGray)
                                    .clickable { status = s }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(s, color = if (status == s) Color.Black else Color.White)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("إشعار التأخر أو الملاحظات") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAttDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), modifier = Modifier.weight(1f)) {
                            Text("رجوع")
                        }
                        Button(
                            onClick = {
                                if (empName.isNotBlank()) {
                                    val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    viewModel.addAttendance(empName, formattedDate, status, remarks)
                                    showAttDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إقرار الدوام", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 8. REPORTS & DATABASE BACKUP CONTROL TAB
// ---------------------------------------------------------
@Composable
fun ReportsScreen(
    activeCompany: CompanyEntity?,
    products: List<ProductEntity>,
    accounts: List<AccountEntity>,
    invoices: List<InvoiceEntity>,
    vouchers: List<VoucherEntity>,
    attendance: List<AttendanceEntity>,
    viewModel: AppViewModel,
    onSaveBackup: () -> Unit,
    onLoadBackup: () -> Unit
) {
    var showStoreAudit by remember { mutableStateOf(false) }
    var showDebtsReport by remember { mutableStateOf(false) }
    var showPnLReport by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text("التقارير المحاسبية والميزانيات العمومية", fontWeight = FontWeight.Bold, fontSize = 15.sp)

        // Options layout
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportChoiceCard(
                title = "جرد المستودعات وتكلفة رأس المال",
                icon = Icons.Default.Inventory,
                onClick = { showStoreAudit = true },
                modifier = Modifier.weight(1f)
            )
            ReportChoiceCard(
                title = "جدول الديون والأرصدة الدائنة والمدينة",
                icon = Icons.Default.TrendingDown,
                onClick = { showDebtsReport = true },
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportChoiceCard(
                title = "جدول أرباح وخسائر الفترة المحددة",
                icon = Icons.Default.QueryStats,
                onClick = { showPnLReport = true },
                modifier = Modifier.weight(1.0f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("النسخ الاحتياطي للأرشيف واسترجاعه", fontWeight = FontWeight.Bold, fontSize = 15.sp)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "تأمين حركة المبيعات وتأمين قاعدة البيانات بالكامل على هيئة ملف مشفر وقابل للنقل.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onLoadBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استعادة النسخة", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onSaveBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ احتياطي", color = Color.Black, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // Warehouse Stock check Dialog
    if (showStoreAudit) {
        var totalCogs = 0.0
        var totalSaleVal = 0.0
        products.forEach {
            totalCogs += it.stockQuantity * it.costPrice
            totalSaleVal += it.stockQuantity * it.sellingPrice
        }

        Dialog(onDismissRequest = { showStoreAudit = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("جرد المخزون وتكلفة البضاعة المتواجدة", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                        Text("إجمالي تقييم رأس المال بسعر التكلفة:")
                        Text("${totalCogs} ل.س", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Text("إجمالي القيمة المتوقعة بسعر المبيع:")
                        Text("${totalSaleVal} ل.س", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.tertiary)
                    }

                    LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(products) { p ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${p.stockQuantity} ${p.unit}", color = MaterialTheme.colorScheme.primary)
                                Text(p.name)
                            }
                        }
                    }

                    Button(onClick = { showStoreAudit = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("إغـلاق")
                    }
                }
            }
        }
    }

    // Debts & Balances Dialog
    if (showDebtsReport) {
        val debtorsList = accounts.filter { viewModel.getAccountBalance(it.id) != 0.0 }

        Dialog(onDismissRequest = { showDebtsReport = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("أرصدة العملاء والموردين الشاملة", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(debtorsList) { acc ->
                            val bal = viewModel.getAccountBalance(acc.id)
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${bal} ${acc.currencyCode}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (bal >= 0) MaterialTheme.colorScheme.primary else Color.Red
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(acc.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(acc.type, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Button(onClick = { showDebtsReport = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("إغـلاق")
                    }
                }
            }
        }
    }

    // Profit Loss summary sheet popup
    if (showPnLReport) {
        val pl = viewModel.getDynamicProfitAndLoss()

        Dialog(onDismissRequest = { showPnLReport = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("قائمة الأرباح والخسائر للشركة", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pl["sales"]} ل.س")
                            Text("إجمالي المبيعات:")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pl["cogs"]} ل.س", color = Color.Red)
                            Text("تكلفة المبيعات المقدرة:")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pl["revenues"]} ل.س", color = MaterialTheme.colorScheme.primary)
                            Text("الإيرادات الأخرى الدائنة:")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pl["expenses"]} ل.س", color = Color.Red)
                            Text("المصاريف والأجور المدفوعة:")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pl["damage"]} ل.س", color = Color.Red)
                            Text("تلف منتجات وهدر صناعي:")
                        }

                        HorizontalDivider()

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${pl["netProfit"]} ل.س",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if ((pl["netProfit"] ?: 0.0) >= 0) MaterialTheme.colorScheme.primary else Color.Red
                            )
                            Text("صافي الربح الفتروي المالي:", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(onClick = { showPnLReport = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("إغـلاق")
                    }
                }
            }
        }
    }
}

@Composable
fun ReportChoiceCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Right)
        }
    }
}
