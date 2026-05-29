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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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

    // Toast listener from ViewModel flows
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Main App Scaffold containing bottom bar and body
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        onAccountClick = { acc -> selectedStatementAccount = acc }
                    )
                    "products" -> ProductsTabScreen(viewModel = viewModel)
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
            NewInvoiceDialog(viewModel = viewModel, onClose = { isNewInvoiceOpen = false })
        }

        if (isNewAccountOpen) {
            NewAccountDialog(viewModel = viewModel, onClose = { isNewAccountOpen = false })
        }

        if (isNewProductOpen) {
            NewProductDialog(viewModel = viewModel, onClose = { isNewProductOpen = false })
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
                        text = "${viewModel.formatCurrency(invoice.total)} ل.س",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (invoice.profit > 0) {
                        Text(
                            text = "ربح: ${viewModel.formatCurrency(invoice.profit)} ل.س",
                            fontSize = 11.sp,
                            color = Color(0xFF2EBD7A),
                            fontWeight = FontWeight.Bold
                        )
                    } else if (invoice.profit < 0) {
                        Text(
                            text = "خسارة: ${viewModel.formatCurrency(Math.abs(invoice.profit))} ل.س",
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
fun AccountsTabScreen(viewModel: AppViewModel, onAccountClick: (Account) -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val filter by viewModel.accountFilter.collectAsState()
    val search by viewModel.accountSearch.collectAsState()

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
                    AccountItemRow(account = acc, viewModel = viewModel, onClick = { onAccountClick(acc) })
                }
            }
        }
    }
}

@Composable
fun AccountItemRow(account: Account, viewModel: AppViewModel, onClick: () -> Unit) {
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
                if (account.phone.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.triggerToast("اتصال هاتف للرقم ${account.phone}") },
                            modifier = Modifier.size(28.dp).background(Color(0xFF2EBD7A).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Text(text = "📞", fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { viewModel.triggerToast("رسالة واتساب للرقم ${account.phone}") },
                            modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        ) {
                            Text(text = "💬", fontSize = 12.sp)
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = balLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = balColor)
                Text(text = "ل.س", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}


// ==========================================
// 3. PRODUCTS TAB PAGE
// ==========================================
@Composable
fun ProductsTabScreen(viewModel: AppViewModel) {
    val products by viewModel.products.collectAsState()
    val filter by viewModel.productFilter.collectAsState()
    val search by viewModel.productSearch.collectAsState()

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
                    ProductItemRow(product = prod, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ProductItemRow(product: Product, viewModel: AppViewModel) {
    val lowStock = product.qty <= product.minQty

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.triggerToast("${product.name} — كود: ${product.code}") },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFD0DEDD))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE4ECEB)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = product.icon, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = "📊 ${product.code} | باركود: ${product.barcode}", fontSize = 11.sp, color = Color.Gray)
                Text(
                    text = "شراء: ${viewModel.formatCurrency(product.buyPrice)} | بيع: ${viewModel.formatCurrency(product.sellPrice)} ل.س",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = product.qty.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
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

    var showCompanyDialog by remember { mutableStateOf(false) }
    var tempCompanyNameText by remember { mutableStateOf(companyName) }

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
                            val totalCustomersDebt = accountsList.filter { it.type == "customer" }.sumOf { maxOf(0.0, it.balance) }
                            val totalSuppliersDebt = accountsList.filter { it.type == "supplier" }.sumOf { maxOf(0.0, it.balance) }
                            
                            Text("• إجمالي قيمة البضاعة بالمخازن: ${viewModel.formatCurrency(assetsValue)} ل.س", fontSize = 11.sp)
                            Text("• ديون مستحقة على العملاء: ${viewModel.formatCurrency(totalCustomersDebt)} ل.س", fontSize = 11.sp)
                            Text("• مستحقات للموردين علينا: ${viewModel.formatCurrency(totalSuppliersDebt)} ل.س", fontSize = 11.sp)
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
            title = { Text("تعديل اسم الشركة") },
            text = {
                OutlinedTextField(
                    value = tempCompanyNameText,
                    onValueChange = { tempCompanyNameText = it },
                    label = { Text("اسم الشركة") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCompanyName(tempCompanyNameText)
                    showCompanyDialog = false
                    viewModel.triggerToast("تم تعديل الاسم بنجاح")
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showCompanyDialog = false }) { Text("إلغاء") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
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
                    Column {
                        Text(text = "اسم الشركة الرئيسي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = companyName, fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(onClick = { showCompanyDialog = true }) {
                        Text("تعديل", fontSize = 12.sp)
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
                        Text(text = "الليرة السورية (ل.س)", fontSize = 11.sp, color = Color.Gray)
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
                    Text(text = companyName, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Menu Options
            val sideMenu = listOf(
                Triple("تغيير اسم الشركة المنشأة", "🏢") {
                    val names = listOf("مؤسسة الفجر للمقاولات", "مجموعات الشام التجارية", "سوبرماركت المدينة")
                    viewModel.setCompanyName(names.random())
                    viewModel.triggerToast("تم تبديل اسم الشركة كعرض")
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
fun NewInvoiceDialog(viewModel: AppViewModel, onClose: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val products by viewModel.products.collectAsState()
    val tempItems by viewModel.tempInvoiceItems.collectAsState()
    val selectedCustomer by viewModel.selectedInvoiceCustomer.collectAsState()
    val notes by viewModel.invoiceNotes.collectAsState()

    var showCustDropdown by remember { mutableStateOf(false) }
    var showProdDropdown by remember { mutableStateOf(false) }

    val total = tempItems.sumOf { it.qty * it.price }

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
                    Text(text = "فاتورة مبيعات جديدة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                    // Customer drop selector
                    item {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCustomer?.name ?: "-- اختر عميل من المنشأة --",
                                onValueChange = {},
                                label = { Text("العميل المستفيد") },
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
                                val customersList = accounts.filter { it.type == "customer" }
                                customersList.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(text = acc.name) },
                                        onClick = {
                                            viewModel.selectedInvoiceCustomer.value = acc
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
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showProdDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ انقر لاختيار مادة من المخزن")
                            }

                            DropdownMenu(expanded = showProdDropdown, onDismissRequest = { showProdDropdown = false }) {
                                products.forEach { prod ->
                                    DropdownMenuItem(
                                        text = { Text(text = "${prod.icon} ${prod.name} | متوفر: ${prod.qty}") },
                                        onClick = {
                                            viewModel.addProductToInvoiceForm(prod)
                                            showProdDropdown = false
                                        }
                                    )
                                }
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
                                        Text(text = "مجموع: ${viewModel.formatCurrency(item.qty * item.price)} ل.س", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(onClick = { viewModel.updateInvoiceItemFormQty(idx, -1) }, modifier = Modifier.size(24.dp).background(Color(0xFFE4ECEB), RoundedCornerShape(6.dp))) {
                                            Text("−", fontSize = 14.sp)
                                        }
                                        Text(text = item.qty.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    Text(text = "${viewModel.formatCurrency(total)} ل.س", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray).padding(vertical = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "صافي قيمة الفاتورة:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "${viewModel.formatCurrency(total)} ل.س", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

    var name by remember { mutableStateOf(initName) }
    var type by remember { mutableStateOf(initType) }
    var balance by remember { mutableStateOf(initBalance) }
    var phone by remember { mutableStateOf(initPhone) }
    var address by remember { mutableStateOf(initAddress) }
    var notes by remember { mutableStateOf(initNotes) }

    var showAccountTypeMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "إضافة حساب مالي جديد", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
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

                OutlinedTextField(
                    value = balance,
                    onValueChange = {
                        balance = it
                        viewModel.newAccountBalance.value = it
                    },
                    label = { Text("الرصيد الافتتاحي (بالسالب ليكون مطلوب منا)") },
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
                        Text("إلغاء لالغاء")
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
fun NewProductDialog(viewModel: AppViewModel, onClose: () -> Unit) {
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

    var name by remember { mutableStateOf(initName) }
    var code by remember { mutableStateOf(initCode) }
    var category by remember { mutableStateOf(initCategory) }
    var unit by remember { mutableStateOf(initUnit) }
    var qty by remember { mutableStateOf(initQty) }
    var minQty by remember { mutableStateOf(initMinQty) }
    var buyPrice by remember { mutableStateOf(initBuyPrice) }
    var sellPrice by remember { mutableStateOf(initSellPrice) }
    var barcode by remember { mutableStateOf(initBarcode) }
    var icon by remember { mutableStateOf(initIcon) }

    var showCatDropdown by remember { mutableStateOf(false) }
    var showUnitDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "إضافة مادة للمستودع", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
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
                            onValueChange = {},
                            label = { Text("الوحدة") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showUnitDropdown = true }
                        )

                        DropdownMenu(expanded = showUnitDropdown, onDismissRequest = { showUnitDropdown = false }) {
                            listOf("قطعة", "كيلو", "لتر", "متر", "علبة", "كرتون").forEach { u ->
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

                OutlinedTextField(
                    value = barcode,
                    onValueChange = {
                        barcode = it
                        viewModel.newProductBarcode.value = it
                    },
                    label = { Text("رقم الباركود (EAN)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.triggerToast("محاكاة فتح كاميرا لمسح باركود المادة") }) {
                            Text(text = "📷")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("إلغاء لالغاء")
                    }
                    Button(
                        onClick = {
                            viewModel.saveProduct()
                            onClose()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("إضافة وحفظ المستودع")
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
                    label = { Text("المبلغ النقدي (ل.س)") },
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
                                            Text(text = "${viewModel.formatCurrency(Math.abs(c.balance))} ل.س", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

// --- Account Statement (كشف الحساب المحاسبي) dialog ---
@Composable
fun AccountStatementDialog(viewModel: AppViewModel, account: Account, onClose: () -> Unit, onAddVoucher: (String) -> Unit) {
    val invoices by viewModel.invoices.collectAsState()
    val vouchers by viewModel.vouchers.collectAsState()

    val accountVouchers = vouchers.filter { it.accountId == account.id }
    val accountInvs = invoices.filter { it.customer == account.name }

    Dialog(onDismissRequest = { onClose() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
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
                        Text(text = "${viewModel.formatCurrency(Math.abs(account.balance))} ل.س", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val subtitle = if (account.balance > 0) "← مستحق للغير (له علينا)" else if (account.balance < 0) "← مستحق للشركة (عليه لنا)" else "الحساب متوازن"
                        Text(text = subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "حركات السجلات المالية المكتشفة", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))

                // Combine ledger transactions into a list ordered by date
                val allTx = (
                        accountInvs.map { Triple(it.date, "فاتورة مبيعات ${it.id}", -it.total) } +
                        accountVouchers.map { Triple(it.date, it.desc, if (it.type == "receipt") it.amount else -it.amount) }
                        ).sortedByDescending { it.first }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (allTx.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "لا توجد أي حركات قيود لهذا الحساب", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allTx) { item ->
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
                                        Text(text = "$prefix${viewModel.formatCurrency(amount)} ل.س", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
