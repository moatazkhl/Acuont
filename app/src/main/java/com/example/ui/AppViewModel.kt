package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

// Helper Extensions for precise day-boundary date filtering
fun Long.getStartOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = this
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun Long.getEndOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = this
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: Repository
    private val prefs = application.getSharedPreferences("smart_accountant_prefs", Context.MODE_PRIVATE)

    // --- Premium & Auth Session ---
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedInPhone = MutableStateFlow("")
    val loggedInPhone: StateFlow<String> = _loggedInPhone.asStateFlow()

    private val _loggedInName = MutableStateFlow("")
    val loggedInName: StateFlow<String> = _loggedInName.asStateFlow()

    private val _loggedInRole = MutableStateFlow("USER") // USER or ADMIN
    val loggedInRole: StateFlow<String> = _loggedInRole.asStateFlow()

    private val _activeUserStatus = MutableStateFlow("TRIAL") // TRIAL, PENDING, ACTIVE
    val activeUserStatus: StateFlow<String> = _activeUserStatus.asStateFlow()

    private val _registeredUsers = MutableStateFlow<List<AdminUserItem>>(emptyList())
    val registeredUsers: StateFlow<List<AdminUserItem>> = _registeredUsers.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = Repository(db)

        // Seed Admin Account
        if (!prefs.contains("user_password_admin")) {
            prefs.edit()
                .putString("user_name_admin", "مدير التطبيق")
                .putString("user_password_admin", "admin")
                .putString("user_status_admin", "ACTIVE")
                .apply()
        }

        // Seed Sample Trial User
        if (!prefs.contains("user_password_0938385157")) {
            prefs.edit()
                .putString("user_name_0938385157", "معتز")
                .putString("user_password_0938385157", "123456")
                .putString("user_status_0938385157", "TRIAL")
                .putString("users_list", "0938385157")
                .apply()
        }

        // Restore Session
        val loggedIn = prefs.getBoolean("session_logged_in", false)
        _isLoggedIn.value = loggedIn
        if (loggedIn) {
            val phone = prefs.getString("session_phone", "") ?: ""
            val name = prefs.getString("session_name", "") ?: ""
            val role = prefs.getString("session_role", "USER") ?: "USER"

            _loggedInPhone.value = phone
            _loggedInName.value = name
            _loggedInRole.value = role

            if (role == "ADMIN") {
                _activeUserStatus.value = "ACTIVE"
            } else {
                val status = prefs.getString("user_status_$phone", "TRIAL") ?: "TRIAL"
                _activeUserStatus.value = status
            }
        }

        refreshRegisteredUsers()
    }

    // --- Date Filter State ---
    private val _startDate = MutableStateFlow(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) // Default: past 30 days
    val startDate: StateFlow<Long> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow(System.currentTimeMillis())
    val endDate: StateFlow<Long> = _endDate.asStateFlow()

    fun updateDateRange(start: Long, end: Long) {
        _startDate.value = start
        _endDate.value = end
    }

    // --- Products (Warehouse) ---
    val allProducts: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Invoices ---
    val allInvoices: StateFlow<List<Invoice>> = repository.allInvoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Vouchers ---
    val allVouchers: StateFlow<List<Voucher>> = repository.allVouchers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- FILTERED DATA (With Accurate Date Range) ---
    val filteredInvoices: StateFlow<List<Invoice>> = combine(allInvoices, _startDate, _endDate) { list, start, end ->
        val s = start.getStartOfDay()
        val e = end.getEndOfDay()
         list.filter { it.date in s..e }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVouchers: StateFlow<List<Voucher>> = combine(allVouchers, _startDate, _endDate) { list, start, end ->
        val s = start.getStartOfDay()
        val e = end.getEndOfDay()
        list.filter { it.date in s..e }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- FINANCIAL METRICS WITH DATE FILTERING ---
    val metrics: StateFlow<FinancialMetrics> = combine(filteredInvoices, filteredVouchers) { invoices, vouchers ->
        var totalSales = 0.0
        var totalPurchases = 0.0
        var totalPayments = 0.0 // صرف
        var totalReceipts = 0.0 // قبض

        invoices.forEach {
            if (it.type == "SALE") totalSales += it.totalAmount
            else if (it.type == "PURCHASE") totalPurchases += it.totalAmount
        }

        vouchers.forEach {
            if (it.type == "PAYMENT") totalPayments += it.amount
            else if (it.type == "RECEIPT") totalReceipts += it.amount
        }

        val netProfit = totalSales - totalPurchases + totalReceipts - totalPayments
        val inventoryValue = calculateInventoryValue() // will be accessed programmatically
        FinancialMetrics(
            sales = totalSales,
            purchases = totalPurchases,
            payments = totalPayments,
            receipts = totalReceipts,
            netProfit = netProfit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialMetrics())

    private fun calculateInventoryValue(): Double {
        return allProducts.value.sumOf { it.quantity * it.purchasePrice }
    }

    // --- Product CRUD Actions ---
    fun addProduct(product: Product) {
        viewModelScope.launch { repository.insertProduct(product) }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch { repository.updateProduct(product) }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch { repository.deleteProduct(product) }
    }

    // --- Invoice actions with stock auto-adjust ---
    fun addInvoice(invoice: Invoice, items: List<InvoiceItem>) {
        viewModelScope.launch {
            repository.insertInvoice(invoice, items)
        }
    }

    fun deleteInvoice(invoice: Invoice) {
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
        }
    }

    fun getItemsForInvoice(invoiceId: Int): Flow<List<InvoiceItem>> {
        return repository.getItemsForInvoice(invoiceId)
    }

    // --- Voucher actions ---
    fun addVoucher(voucher: Voucher) {
        viewModelScope.launch { repository.insertVoucher(voucher) }
    }

    fun deleteVoucher(voucher: Voucher) {
        viewModelScope.launch { repository.deleteVoucher(voucher) }
    }

    // --- AI REPORT GENERATION STATE ---
    private val _aiReport = MutableStateFlow("")
    val aiReport: StateFlow<String> = _aiReport.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    fun generateAiReportAsync() {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiReport.value = "جاري الاتصال بـ Gemini وتحليل البيانات الحالية..."

            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
            val m = metrics.value
            val pCount = allProducts.value.size
            val lowStockProducts = allProducts.value.filter { it.quantity <= it.minLimit }
            val lowStockText = lowStockProducts.joinToString("\n") { "  - ${it.name}: الكمية المتوفرة (${it.quantity}) والحد الأدنى (${it.minLimit})" }

            val prompt = """
                قم بعمل تقرير مالي ذكي ومفصل مستنداً على البيانات التالية للمحل التجاري:
                الفترة من: ${sdf.format(Date(startDate.value))} إلى ${sdf.format(Date(endDate.value))}
                إجمالي المبيعات: ${m.sales}
                إجمالي المشتريات: ${m.purchases}
                إجمالي المقبوضات النقية (سندات القبض): ${m.receipts}
                إجمالي المدفوعات النقية (سندات الصرف): ${m.payments}
                صافي الأرباح المقدرة للفترة: ${m.netProfit}
                إجمالي عدد المواد في المستودع: $pCount
                قيمة بضاعة المستودع المقدرة: ${calculateInventoryValue()}
                مواد شارفت على النفاد (كميتها أقل من الحد الأدنى):
                $lowStockText
                
                الرجاء إبداء الملاحظات والتوصيات التشغيلية والمالية بدقة وبلغة محاسبية سليمة.
            """.trimIndent()

            val systemInstruction = "أنت محاسب مالي خبير ومستشار استراتيجي، تجيب دوماً باللغة العربية بأسلوب منسق، مهني، مباشر مع استخدام مؤشرات ونقاط واضحة وجميلة."

            val result = GeminiService.generateReport(prompt, systemInstruction)
            _aiReport.value = result
            _isAiLoading.value = false
        }
    }

    // --- EXCEL/CSV EXPORT INTEGRITY HANDLER ---
    // Direct Excel support for Arabic language! We must prepend the UTF-8 BOM byte marker (\uFEFF)
    // so Excel instantly opens this text as fully encoded Arabic without making it scrambled (Gibberish)
    fun exportReportToCsv(context: Context, uri: Uri): Boolean {
        return try {
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
            if (outputStream == null) return false

            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
            val sDateString = sdf.format(Date(startDate.value))
            val eDateString = sdf.format(Date(endDate.value))

            // Build beautifully structured report
            val sb = StringBuilder()
            
            // Add UTF-8 BOM index so MS Excel reads Arabic text correctly
            sb.append("\uFEFF")

            // Title section
            sb.append("\"المحاسب الذكي - التقرير المالي المنسق\"" + "\n")
            sb.append("\"تاريخ التقرير:\",\"من $sDateString إلى $eDateString\"\n")
            sb.append("\n")

            // Visual Summary metrics table
            sb.append("\"الملخص المالي الأساسي:\",\"القيمة\"\n")
            sb.append("\"إجمالي المبيعات:\",\"${metrics.value.sales}\"\n")
            sb.append("\"إجمالي المشتريات:\",\"${metrics.value.purchases}\"\n")
            sb.append("\"سندات القبض:\",\"${metrics.value.receipts}\"\n")
            sb.append("\"سندات الصرف:\",\"${metrics.value.payments}\"\n")
            sb.append("\"صافي الربح والخسارة:\",\"${metrics.value.netProfit}\"\n")
            sb.append("\"قيمة المخزون الحالي (سعر الشراء):\",\"${calculateInventoryValue()}\"\n")
            sb.append("\n")

            // Section 1: Stock/Warehouse List
            sb.append("\"جدول جرد المستودع الحالي:\"\n")
            sb.append("\"اسم المادة\",\"المستودع / التصنيف\",\"الكمية الحالية\",\"سعر الشراء\",\"سعر البيع\",\"القيمة الإجمالية\"\n")
            allProducts.value.forEach {
                val escapedName = it.name.replace("\"", "\"\"")
                val escapedCat = it.category.replace("\"", "\"\"")
                val totalValue = it.quantity * it.purchasePrice
                sb.append("\"$escapedName\",\"$escapedCat\",\"${it.quantity}\",\"${it.purchasePrice}\",\"${it.salePrice}\",\"$totalValue\"\n")
            }
            sb.append("\n")

            // Section 2: Sales Invoices (المدين / فواتير المبيعات للزبائن)
            sb.append("\"جدول فواتير المبيعات (مدين / مستحقات من الزبائن):\"\n")
            sb.append("\"رقم الفاتورة\",\"التاريخ\",\"الزبون / العميل\",\"المبلغ الإجمالي (ر.س)\",\"ملاحظات\"\n")
            filteredInvoices.value.filter { it.type == "SALE" }.forEach {
                val dateString = sdf.format(Date(it.date))
                val nameEscaped = it.partyName.replace("\"", "\"\"")
                val notesEscaped = it.notes.replace("\"", "\"\"")
                sb.append("\"${it.invoiceNumber}\",\"$dateString\",\"$nameEscaped\",\"${it.totalAmount}\",\"$notesEscaped\"\n")
            }
            sb.append("\n")

            // Section 3: Purchase Invoices (الدائن / فواتير المشتريات من الموردين)
            sb.append("\"جدول فواتير المشتريات (دائن / مستحقات للموردين):\"\n")
            sb.append("\"رقم الفاتورة\",\"التاريخ\",\"المورد\",\"المبلغ الإجمالي (ر.س)\",\"ملاحظات\"\n")
            filteredInvoices.value.filter { it.type == "PURCHASE" }.forEach {
                val dateString = sdf.format(Date(it.date))
                val nameEscaped = it.partyName.replace("\"", "\"\"")
                val notesEscaped = it.notes.replace("\"", "\"\"")
                sb.append("\"${it.invoiceNumber}\",\"$dateString\",\"$nameEscaped\",\"${it.totalAmount}\",\"$notesEscaped\"\n")
            }
            sb.append("\n")

            // Section 4: Receipt Vouchers (المدين / سندات القبض المستلمة)
            sb.append("\"جدول سندات القبض (مدين / مقبوضات نقدية من الزبائن):\"\n")
            sb.append("\"رقم السند\",\"التاريخ\",\"الجهة / الزبون\",\"المبلغ المستلم (ر.س)\",\"بيان / ملاحظات\"\n")
            filteredVouchers.value.filter { it.type == "RECEIPT" }.forEach {
                val dateString = sdf.format(Date(it.date))
                val nameEscaped = it.partyName.replace("\"", "\"\"")
                val notesEscaped = it.notes.replace("\"", "\"\"")
                sb.append("\"${it.voucherNumber}\",\"$dateString\",\"$nameEscaped\",\"${it.amount}\",\"$notesEscaped\"\n")
            }
            sb.append("\n")

            // Section 5: Payment Vouchers (الدائن / سندات الصرف المدفوعة)
            sb.append("\"جدول سندات الصرف (دائن / مدفوعات نقدية ومصاريف):\"\n")
            sb.append("\"رقم السند\",\"التاريخ\",\"الجهة المستفيدة / المورد\",\"المبلغ المدفوع (ر.س)\",\"بيان / ملاحظات\"\n")
            filteredVouchers.value.filter { it.type == "PAYMENT" }.forEach {
                val dateString = sdf.format(Date(it.date))
                val nameEscaped = it.partyName.replace("\"", "\"\"")
                val notesEscaped = it.notes.replace("\"", "\"\"")
                sb.append("\"${it.voucherNumber}\",\"$dateString\",\"$nameEscaped\",\"${it.amount}\",\"$notesEscaped\"\n")
            }

            // Write explicitly with UTF-8 encoding
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportBackup(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val products = db.productDao().getAllProductsSync()
                val invoices = db.invoiceDao().getAllInvoicesSync()
                val items = db.invoiceItemDao().getAllInvoiceItemsSync()
                val vouchers = db.voucherDao().getAllVouchersSync()

                val backupData = AppBackupData(
                    backupTimestamp = System.currentTimeMillis(),
                    products = products,
                    invoices = invoices,
                    invoiceItems = items,
                    vouchers = vouchers
                )

                val jsonString = Json.encodeToString(backupData)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonString.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "حدث خطأ أثناء التصدير")
                }
            }
        }
    }

    fun importBackup(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("تعذر قراءة ملف النسخة الاحتياطية")

                val backupData = Json.decodeFromString<AppBackupData>(jsonString)

                // Clear previous tables and insert new data sequentially
                db.productDao().deleteAllProducts()
                db.invoiceDao().deleteAllInvoices()
                db.invoiceItemDao().deleteAllInvoiceItems()
                db.voucherDao().deleteAllVouchers()

                if (backupData.products.isNotEmpty()) {
                    db.productDao().insertProductsSync(backupData.products)
                }
                if (backupData.invoices.isNotEmpty()) {
                    db.invoiceDao().insertInvoicesSync(backupData.invoices)
                }
                if (backupData.invoiceItems.isNotEmpty()) {
                    db.invoiceItemDao().insertInvoiceItemsSync(backupData.invoiceItems)
                }
                if (backupData.vouchers.isNotEmpty()) {
                    db.voucherDao().insertVouchersSync(backupData.vouchers)
                }

                launch(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "فشل استيراد النسخة الاحتياطية")
                }
            }
        }
    }

    fun refreshRegisteredUsers() {
        val listStr = prefs.getString("users_list", "") ?: ""
        val phones = if (listStr.isEmpty()) emptyList() else listStr.split(",")
        val users = phones.mapNotNull { phone ->
            val name = prefs.getString("user_name_$phone", null) ?: return@mapNotNull null
            val status = prefs.getString("user_status_$phone", "TRIAL") ?: "TRIAL"
            AdminUserItem(name = name, phone = phone, status = status)
        }
        _registeredUsers.value = users
    }

    fun login(phone: String, pass: String): Boolean {
        val trimmedPhone = phone.trim()
        val trimmedPass = pass.trim()

        val savedPass = prefs.getString("user_password_$trimmedPhone", null)
        if (savedPass != null && savedPass == trimmedPass) {
            val name = prefs.getString("user_name_$trimmedPhone", "مستخدم") ?: "مستخدم"
            val isAdm = trimmedPhone == "admin"
            val role = if (isAdm) "ADMIN" else "USER"
            val status = if (isAdm) "ACTIVE" else (prefs.getString("user_status_$trimmedPhone", "TRIAL") ?: "TRIAL")

            prefs.edit()
                .putBoolean("session_logged_in", true)
                .putString("session_phone", trimmedPhone)
                .putString("session_name", name)
                .putString("session_role", role)
                .apply()

            _isLoggedIn.value = true
            _loggedInPhone.value = trimmedPhone
            _loggedInName.value = name
            _loggedInRole.value = role
            _activeUserStatus.value = status

            refreshRegisteredUsers()
            return true
        }
        return false
    }

    fun register(name: String, phone: String, pass: String): Boolean {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        val trimmedPass = pass.trim()

        if (trimmedPhone.isEmpty() || trimmedName.isEmpty() || trimmedPass.isEmpty()) {
            return false
        }

        if (prefs.contains("user_password_$trimmedPhone")) {
            return false
        }

        val listStr = prefs.getString("users_list", "") ?: ""
        val newList = if (listStr.isEmpty()) trimmedPhone else "$listStr,$trimmedPhone"

        prefs.edit()
            .putString("user_name_$trimmedPhone", trimmedName)
            .putString("user_password_$trimmedPhone", trimmedPass)
            .putString("user_status_$trimmedPhone", "TRIAL")
            .putString("users_list", newList)
            .putBoolean("session_logged_in", true)
            .putString("session_phone", trimmedPhone)
            .putString("session_name", trimmedName)
            .putString("session_role", "USER")
            .apply()

        _isLoggedIn.value = true
        _loggedInPhone.value = trimmedPhone
        _loggedInName.value = trimmedName
        _loggedInRole.value = "USER"
        _activeUserStatus.value = "TRIAL"

        refreshRegisteredUsers()
        return true
    }

    fun logout() {
        prefs.edit()
            .putBoolean("session_logged_in", false)
            .putString("session_phone", "")
            .putString("session_name", "")
            .putString("session_role", "USER")
            .apply()

        _isLoggedIn.value = false
        _loggedInPhone.value = ""
        _loggedInName.value = ""
        _loggedInRole.value = "USER"
        _activeUserStatus.value = "TRIAL"
    }

    fun requestActivation() {
        val phone = _loggedInPhone.value
        if (phone.isNotEmpty() && phone != "admin") {
            prefs.edit().putString("user_status_$phone", "PENDING").apply()
            _activeUserStatus.value = "PENDING"
            refreshRegisteredUsers()
        }
    }

    fun changeUserStatus(phone: String, status: String) {
        prefs.edit().putString("user_status_$phone", status).apply()
        if (phone == _loggedInPhone.value) {
            _activeUserStatus.value = status
        }
        refreshRegisteredUsers()
    }
}

data class FinancialMetrics(
    val sales: Double = 0.0,
    val purchases: Double = 0.0,
    val payments: Double = 0.0,
    val receipts: Double = 0.0,
    val netProfit: Double = 0.0
)
