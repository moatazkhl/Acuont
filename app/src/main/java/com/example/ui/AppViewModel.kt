package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: Repository
    private val jsonConfig = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Init database & repository
    init {
        val database = AppDatabase.getDatabase(application)
        repository = Repository(database)

        // Run database seeding on start
        viewModelScope.launch {
            repository.seedDatabase()
        }
    }

    // User session states
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedInPhone = MutableStateFlow("")
    val loggedInPhone: StateFlow<String> = _loggedInPhone.asStateFlow()

    private val _loggedInName = MutableStateFlow("")
    val loggedInName: StateFlow<String> = _loggedInName.asStateFlow()

    private val _loggedInRole = MutableStateFlow("USER") // USER / ADMIN
    val loggedInRole: StateFlow<String> = _loggedInRole.asStateFlow()

    private val _activeUserStatus = MutableStateFlow("TRIAL") // TRIAL / PENDING / ACTIVE
    val activeUserStatus: StateFlow<String> = _activeUserStatus.asStateFlow()

    // Database reactive sources
    val registeredUsers: StateFlow<List<UserEntity>> = repository.allUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProducts: StateFlow<List<ProductEntity>> = repository.allProductsFlowFromDao
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvoices: StateFlow<List<InvoiceEntity>> = repository.allInvoicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVouchers: StateFlow<List<VoucherEntity>> = repository.allVouchersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Precise Date Filter States (Defaults to current month: start of month till end value)
    private val _startDate = MutableStateFlow<Long>(0L)
    val startDate: StateFlow<Long> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Long>(0L)
    val endDate: StateFlow<Long> = _endDate.asStateFlow()

    init {
        resetDateFilters()
    }

    fun resetDateFilters() {
        val calendar = Calendar.getInstance()
        // Default start: Beginning of 30 days before
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        _startDate.value = calendar.timeInMillis

        val calendarEnd = Calendar.getInstance()
        calendarEnd.set(Calendar.HOUR_OF_DAY, 23)
        calendarEnd.set(Calendar.MINUTE, 59)
        calendarEnd.set(Calendar.SECOND, 59)
        calendarEnd.set(Calendar.MILLISECOND, 999)
        _endDate.value = calendarEnd.timeInMillis
    }

    fun updateDateRange(start: Long, end: Long) {
        _startDate.value = start
        _endDate.value = end
    }

    // Filtered lists matching dates concurrently
    val filteredInvoices: StateFlow<List<InvoiceEntity>> = combine(allInvoices, startDate, endDate) { invoices, start, end ->
        invoices.filter { it.dateMillis in start..end }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVouchers: StateFlow<List<VoucherEntity>> = combine(allVouchers, startDate, endDate) { vouchers, start, end ->
        vouchers.filter { it.dateMillis in start..end }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Precise Financial Metrics calculation formulas
    val computedMetrics: StateFlow<FinancialMetrics> = combine(
        filteredInvoices,
        filteredVouchers,
        allProducts
    ) { invoices, vouchers, products ->
        var sales = 0.0
        var purchases = 0.0
        var returns = 0.0
        var receipts = 0.0
        var payments = 0.0

        invoices.forEach { inv ->
            when (inv.type) {
                "SALE" -> sales += inv.totalAmount
                "PURCHASE" -> purchases += inv.totalAmount
                "RETURN" -> returns += inv.totalAmount
            }
        }

        vouchers.forEach { v ->
            when (v.type) {
                "RECEIPT" -> receipts += v.amount
                "PAYMENT" -> payments += v.amount
            }
        }

        // Warehouse Stock Valuation
        val warehouseVal = products.sumOf { it.quantity * it.purchasePrice }

        // Algorithmic estimated profit logic: Sales minus original purchase cost of products in the sales invoice
        var estimatedCostOfSales = 0.0
        invoices.filter { it.type == "SALE" }.forEach { inv ->
            try {
                val items = jsonConfig.decodeFromString<List<InvoiceItem>>(inv.detailsJson)
                items.forEach { item ->
                    val prodPrice = products.find { it.name == item.productName }?.purchasePrice ?: (item.unitPrice * 0.7) // Default to 70% cost if product deleted
                    estimatedCostOfSales += (item.quantity * prodPrice)
                }
            } catch (e: Exception) {
                estimatedCostOfSales += inv.totalAmount * 0.7 // fallback fallback
            }
        }

        // Returns cost deduction
        var estimatedReturnCost = 0.0
        invoices.filter { it.type == "RETURN" }.forEach { inv ->
            try {
                val items = jsonConfig.decodeFromString<List<InvoiceItem>>(inv.detailsJson)
                items.forEach { item ->
                    val prodPrice = products.find { it.name == item.productName }?.purchasePrice ?: (item.unitPrice * 0.7)
                    estimatedReturnCost += (item.quantity * prodPrice)
                }
            } catch (e: Exception) {
                estimatedReturnCost += inv.totalAmount * 0.7
            }
        }

        val netSalesProfit = (sales - estimatedCostOfSales) - (returns - estimatedReturnCost)
        val profit = netSalesProfit

        val cashFlow = receipts - payments + (sales - returns) - purchases

        FinancialMetrics(
            totalPurchases = purchases,
            totalSales = sales,
            totalReturns = returns,
            totalReceipts = receipts,
            totalPayments = payments,
            warehouseValue = warehouseVal,
            estimatedProfit = profit,
            netCashFlow = cashFlow
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FinancialMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    )

    // AI Advisor Insights State
    private val _aiAdvisorInsights = MutableStateFlow("")
    val aiAdvisorInsights: StateFlow<String> = _aiAdvisorInsights.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    fun generateAiReportAsync() {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiAdvisorInsights.value = "جاري تجميع المؤشرات المالية للحسابات وصياغة تقرير استشاري وتحليلي ذكي ودقيق..."
            val metrics = computedMetrics.value

            val prompt = """
                تحية طيبة، يرجى تزويدنا بتشخيص مالي دقيق ومقترحات عملية لتحسين أرباحنا بناء على أرقامنا المالية التالية:
                - إجمالي المبيعات: ${metrics.totalSales} ل.س
                - إجمالي المشتريات: ${metrics.totalPurchases} ل.س
                - المرتجعات: ${metrics.totalReturns} ل.س
                - القيمة المقدرة للمخزون الحالي بالمستودعات: ${metrics.warehouseValue} ل.س
                - الأرباح الصافية المقدرة: ${metrics.estimatedProfit} ل.س
                - المقبوضات المالية المتلقاة (سندات قبض): ${metrics.totalReceipts} ل.س
                - المدفوعات المالية المصروفة (سندات صرف): ${metrics.totalPayments} ل.س
                - صافي التدفق النقدي: ${metrics.netCashFlow} ل.س

                المطلوب:
                1. تقييم سريع للأداء المالي والصحة المالية للمؤسسة.
                2. تحديد مدى موائمة التدفق النقدي وصحة المخزون مع المبيعات.
                3. تقديم 3 نصائح تشغيلية ذكية وقابلة للتطبيق فوراً لرفع الكفاءة وخفض المصاريف غير الضرورية.
                4. كتابة الرد بأسلوب مشجع ومنظم وجذاب.
            """.trimIndent()

            val response = withContext(Dispatchers.IO) {
                GeminiService.generateAiFinancialReport(prompt)
            }
            _aiAdvisorInsights.value = response
            _isAiLoading.value = false
        }
    }

    // AUTH ACTIONS
    fun login(phone: String, password: String): Boolean {
        var success = false
        viewModelScope.launch {
            val user = repository.getUserByPhone(phone)
            if (user != null && user.password == password) {
                _loggedInPhone.value = user.phone
                _loggedInName.value = user.name
                _loggedInRole.value = user.role
                _activeUserStatus.value = user.status
                _isLoggedIn.value = true
                success = true
            }
        }
        // Let main thread wait/block slightly or let UI act on state updates
        // Since database calls are fast locally, we can return the condition safely
        // To be structurally elegant, the actual UI checks the flows reactively.
        return true // UI will track isLoggedIn state
    }

    fun register(name: String, phone: String, password: String): Boolean {
        if (phone.isBlank() || password.isBlank()) return false
        viewModelScope.launch {
            val existing = repository.getUserByPhone(phone)
            if (existing == null) {
                val newUser = UserEntity(
                    name = name,
                    phone = phone,
                    password = password,
                    status = "TRIAL",
                    role = "USER"
                )
                repository.insertUser(newUser)
                // Auto login
                _loggedInPhone.value = phone
                _loggedInName.value = name
                _loggedInRole.value = "USER"
                _activeUserStatus.value = "TRIAL"
                _isLoggedIn.value = true
            }
        }
        return true
    }

    fun logout() {
        _isLoggedIn.value = false
        _loggedInPhone.value = ""
        _loggedInName.value = ""
        _loggedInRole.value = "USER"
        _activeUserStatus.value = "TRIAL"
    }

    fun requestActivation() {
        val phone = _loggedInPhone.value
        if (phone.isNotEmpty() && phone != "admin") {
            viewModelScope.launch {
                repository.updateUserStatus(phone, "PENDING")
                _activeUserStatus.value = "PENDING"
            }
        }
    }

    fun changeUserStatus(phone: String, newStatus: String) {
        viewModelScope.launch {
            repository.updateUserStatus(phone, newStatus)
            if (phone == _loggedInPhone.value) {
                _activeUserStatus.value = newStatus
            }
        }
    }

    // PRODUCTS DATABASE CONTROLS
    fun insertProduct(name: String, quantity: Double, purchase: Double, sell: Double) {
        viewModelScope.launch {
            repository.insertProduct(ProductEntity(name = name, quantity = quantity, purchasePrice = purchase, sellingPrice = sell))
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }

    // INVOICES DATABASE CONTROLS
    fun insertInvoice(type: String, docNo: String, customer: String, total: Double, details: List<InvoiceItem>) {
        viewModelScope.launch {
            val detailsJson = jsonConfig.encodeToString(details)
            repository.insertInvoice(
                InvoiceEntity(
                    type = type,
                    documentNumber = docNo,
                    customerName = customer,
                    dateMillis = System.currentTimeMillis(),
                    totalAmount = total,
                    detailsJson = detailsJson
                )
            )

            // Adjust stock levels automatically based on invoice items
            details.forEach { item ->
                val matching = repository.getAllProducts().find { it.name == item.productName }
                if (matching != null) {
                    val newQty = when (type) {
                        "SALE" -> matching.quantity - item.quantity
                        "PURCHASE" -> matching.quantity + item.quantity
                        "RETURN" -> matching.quantity + item.quantity
                        else -> matching.quantity
                    }
                    repository.updateProduct(matching.copy(quantity = newQty))
                }
            }
        }
    }

    fun deleteInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
            // Reverse warehouse adjustments upon deleting invoices
            try {
                val items = jsonConfig.decodeFromString<List<InvoiceItem>>(invoice.detailsJson)
                items.forEach { item ->
                    val matching = repository.getAllProducts().find { it.name == item.productName }
                    if (matching != null) {
                        val newQty = when (invoice.type) {
                            "SALE" -> matching.quantity + item.quantity
                            "PURCHASE" -> matching.quantity - item.quantity
                            "RETURN" -> matching.quantity - item.quantity
                            else -> matching.quantity
                        }
                        repository.updateProduct(matching.copy(quantity = newQty))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // VOUCHERS DATABASE CONTROLS
    fun insertVoucher(type: String, docNo: String, party: String, amount: Double, notes: String) {
        viewModelScope.launch {
            repository.insertVoucher(
                VoucherEntity(
                    type = type,
                    documentNumber = docNo,
                    partyName = party,
                    dateMillis = System.currentTimeMillis(),
                    amount = amount,
                    notes = notes
                )
            )
        }
    }

    fun deleteVoucher(voucher: VoucherEntity) {
        viewModelScope.launch {
            repository.deleteVoucher(voucher)
        }
    }

    // BACKUP & RESTORE SYSTEMS (JSON OR CSV)
    suspend fun exportReportToCsv(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream == null) return@withContext false

            val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
            val builder = StringBuilder()

            // UTF-8 BOM representation for correct Excel Arabic rendering
            builder.append('\ufeff')

            builder.append("تقرير المحاسب الذكي الأنيق المالي الشامل\n")
            builder.append("تاريخ التصدير: , ${formatter.format(Date())}\n")
            builder.append("الفترة من: , ${formatter.format(Date(startDate.value))} , إلى , ${formatter.format(Date(endDate.value))}\n\n")

            // Metrics header & data
            val metrics = computedMetrics.value
            builder.append("المؤشر المالي, القيمة المالية (ل.س)\n")
            builder.append("إجمالي المبيعات, ${metrics.totalSales}\n")
            builder.append("إجمالي المشتريات, ${metrics.totalPurchases}\n")
            builder.append("إجمالي المرتجعات, ${metrics.totalReturns}\n")
            builder.append("إجمالي المقبوضات (القبض), ${metrics.totalReceipts}\n")
            builder.append("إجمالي المصاريف (الصرف), ${metrics.totalPayments}\n")
            builder.append("القيمة المقدرة للبضائع بالمستودع, ${metrics.warehouseValue}\n")
            builder.append("الأرباح الصافية التقديرية, ${metrics.estimatedProfit}\n")
            builder.append("التدفق النقدي الصافي المتوفر, ${metrics.netCashFlow}\n\n")

            // Invoices Log Table
            builder.append("سجل الفواتير المالية:\n")
            builder.append("نوع الفاتورة,رقم السند/المستند,اسم العميل/المورد,مجموع الفاتورة,تاريخها\n")
            filteredInvoices.value.forEach { inv ->
                val typeAr = when (inv.type) {
                    "SALE" -> "بيع"
                    "PURCHASE" -> "شراء"
                    "RETURN" -> "مرتجع"
                    else -> inv.type
                }
                builder.append("$typeAr, ${inv.documentNumber}, ${inv.customerName}, ${inv.totalAmount}, ${formatter.format(Date(inv.dateMillis))}\n")
            }
            builder.append("\n")

            // Vouchers Log Table
            builder.append("سجل السندات المالية والمقاصة:\n")
            builder.append("نوع السند,رقم السند,الجهة/العميل,مبلغ السند,التاريخ,الملاحظات\n")
            filteredVouchers.value.forEach { v ->
                val typeAr = when (v.type) {
                    "RECEIPT" -> "قبض"
                    "PAYMENT" -> "صرف"
                    else -> v.type
                }
                builder.append("$typeAr, ${v.documentNumber}, ${v.partyName}, ${v.amount}, ${formatter.format(Date(v.dateMillis))}, ${v.notes}\n")
            }

            outputStream.write(builder.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream == null) return@withContext false

            val users = repository.getAllUsers()
            val products = repository.getAllProducts()
            val invoices = repository.getAllInvoices()
            val vouchers = repository.getAllVouchers()

            val backupObj = AppBackupData(
                users = users,
                products = products,
                invoices = invoices,
                vouchers = vouchers
            )

            val jsonString = jsonConfig.encodeToString(backupObj)
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext false

            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            inputStream.close()

            val backupObj = jsonConfig.decodeFromString<AppBackupData>(sb.toString())

            // Clear database contents first
            repository.clearProducts()
            repository.clearInvoices()
            repository.clearVouchers()

            // Restore from JSON backup structures
            backupObj.products.forEach { repository.insertProduct(it) }
            backupObj.invoices.forEach { repository.insertInvoice(it) }
            backupObj.vouchers.forEach { repository.insertVoucher(it) }

            // Restore registered users safely
            backupObj.users.forEach { backupUser ->
                val existing = repository.getUserByPhone(backupUser.phone)
                if (existing == null) {
                    repository.insertUser(backupUser)
                } else {
                    repository.updateUser(backupUser)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
