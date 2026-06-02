package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

@Serializable
data class DatabaseBackup(
    val companies: List<CompanyEntity>,
    val currencies: List<CurrencyEntity>,
    val accounts: List<AccountEntity>,
    val products: List<ProductEntity>,
    val invoices: List<InvoiceEntity>,
    val vouchers: List<VoucherEntity>,
    val attendance: List<AttendanceEntity>,
    val manufacturing: List<ManufacturingEntity>
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = Repository(db)

    // Admin Auth State
    val isLoggedIn = MutableStateFlow(false)
    val adminPhone = "9933210618"
    val adminPassword = "123456"

    // Active Company Mode
    val allCompanies = repository.allCompaniesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCompany = MutableStateFlow<CompanyEntity?>(null)

    // Reactive listings filtered by active company
    private val _currencies = MutableStateFlow<List<CurrencyEntity>>(emptyList())
    val currencies: StateFlow<List<CurrencyEntity>> = _currencies.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _products = MutableStateFlow<List<ProductEntity>>(emptyList())
    val products: StateFlow<List<ProductEntity>> = _products.asStateFlow()

    private val _invoices = MutableStateFlow<List<InvoiceEntity>>(emptyList())
    val invoices: StateFlow<List<InvoiceEntity>> = _invoices.asStateFlow()

    private val _vouchers = MutableStateFlow<List<VoucherEntity>>(emptyList())
    val vouchers: StateFlow<List<VoucherEntity>> = _vouchers.asStateFlow()

    private val _attendance = MutableStateFlow<List<AttendanceEntity>>(emptyList())
    val attendance: StateFlow<List<AttendanceEntity>> = _attendance.asStateFlow()

    private val _manufacturing = MutableStateFlow<List<ManufacturingEntity>>(emptyList())
    val manufacturing: StateFlow<List<ManufacturingEntity>> = _manufacturing.asStateFlow()

    // Loading states
    val isEvaluatingReport = MutableStateFlow(false)
    val aiResponse = MutableStateFlow("")

    init {
        // Automatically link company components whenever the active company shifts
        viewModelScope.launch {
            activeCompany.collect { company ->
                if (company != null) {
                    launch { repository.getCurrenciesForCompanyFlow(company.id).collect { _currencies.value = it } }
                    launch { repository.getAccountsForCompanyFlow(company.id).collect { _accounts.value = it } }
                    launch { repository.getProductsForCompanyFlow(company.id).collect { _products.value = it } }
                    launch { repository.getInvoicesForCompanyFlow(company.id).collect { _invoices.value = it } }
                    launch { repository.getVouchersForCompanyFlow(company.id).collect { _vouchers.value = it } }
                    launch { repository.getAttendanceForCompanyFlow(company.id).collect { _attendance.value = it } }
                    launch { repository.getManufacturingForCompanyFlow(company.id).collect { _manufacturing.value = it } }
                } else {
                    _currencies.value = emptyList()
                    _accounts.value = emptyList()
                    _products.value = emptyList()
                    _invoices.value = emptyList()
                    _vouchers.value = emptyList()
                    _attendance.value = emptyList()
                    _manufacturing.value = emptyList()
                }
            }
        }

        // Fill initial mock business data if the table is completely empty
        viewModelScope.launch {
            allCompanies.collect { list ->
                if (list.isEmpty()) {
                    createInitialSetup()
                } else if (activeCompany.value == null) {
                    activeCompany.value = list.first()
                }
            }
        }
    }

    private suspend fun createInitialSetup() {
        val cid = repository.insertCompany(CompanyEntity(name = "شركة الأمل للتطبيقات المحاسبية", currencyLocal = "SYP")).toInt()
        
        // Add currencies
        repository.insertCurrency(CurrencyEntity(companyId = cid, code = "USD", name = "دولار أمريكي", rateToLocal = 15000.0))
        repository.insertCurrency(CurrencyEntity(companyId = cid, code = "EUR", name = "يورو أوروبي", rateToLocal = 16200.0))
        repository.insertCurrency(CurrencyEntity(companyId = cid, code = "SAR", name = "ريال سعودي", rateToLocal = 4000.0))
        repository.insertCurrency(CurrencyEntity(companyId = cid, code = "AED", name = "درهم إماراتي", rateToLocal = 4080.0))
        repository.insertCurrency(CurrencyEntity(companyId = cid, code = "SYP", name = "ليرة سورية (المحلية)", rateToLocal = 1.0))

        // Create standard accounts
        repository.insertAccount(AccountEntity(companyId = cid, name = "مستودع شركة الأمل الرئيسي", type = "مورد", currencyCode = "SYP", phone = "0933111222"))
        repository.insertAccount(AccountEntity(companyId = cid, name = "الزبون التجاري الأول - محلي", type = "زبون", currencyCode = "SYP", phone = "0944333222"))
        repository.insertAccount(AccountEntity(companyId = cid, name = "العميل الدولي - دبي", type = "زبون", currencyCode = "AED", phone = "009715566"))
        repository.insertAccount(AccountEntity(companyId = cid, name = "مصاريف إيجار ورواتب عادية", type = "مصاريف", currencyCode = "SYP"))
        
        // Add initial products
        repository.insertProduct(ProductEntity(companyId = cid, name = "مادة كيميائية أولية أ", category = "مواد أولية", unit = "كغ", costPrice = 25000.0, sellingPrice = 30000.0, barcode = "1001", stockQuantity = 100.0))
        repository.insertProduct(ProductEntity(companyId = cid, name = "مادة كيميائية أولية ب", category = "مواد أولية", unit = "لتر", costPrice = 45000.0, sellingPrice = 52000.0, barcode = "1002", stockQuantity = 80.0))
        repository.insertProduct(ProductEntity(companyId = cid, name = "منتج منظف لوكس فاخر جاهز", category = "منتجات تامة الصنع", unit = "عبوة", costPrice = 12000.0, sellingPrice = 18000.0, barcode = "5001", stockQuantity = 12.0, lowStockThreshold = 18.0))
    }

    fun login(phone: String, pass: String): Boolean {
        if (phone == adminPhone && pass == adminPassword) {
            isLoggedIn.value = true
            return true
        }
        return false
    }

    fun logout() {
        isLoggedIn.value = false
    }

    // Company CRUD
    fun addCompany(name: String, currencyLocal: String) {
        viewModelScope.launch {
            val id = repository.insertCompany(CompanyEntity(name = name, currencyLocal = currencyLocal)).toInt()
            // Add native currency
            repository.insertCurrency(CurrencyEntity(companyId = id, code = currencyLocal, name = "العملة المحلية ($currencyLocal)", rateToLocal = 1.0))
        }
    }

    fun updateCompany(company: CompanyEntity) {
        viewModelScope.launch {
            repository.updateCompany(company)
            if (activeCompany.value?.id == company.id) {
                activeCompany.value = company
            }
        }
    }

    fun deleteCompany(company: CompanyEntity) {
        viewModelScope.launch {
            repository.deleteCompany(company)
            if (activeCompany.value?.id == company.id) {
                activeCompany.value = allCompanies.value.firstOrNull { it.id != company.id }
            }
        }
    }

    fun selectCompany(company: CompanyEntity) {
        activeCompany.value = company
    }

    // Currency CRUD
    fun addCurrency(code: String, name: String, rateToLocal: Double) {
        val cid = activeCompany.value?.id ?: return
        viewModelScope.launch {
            repository.insertCurrency(CurrencyEntity(companyId = cid, code = code, name = name, rateToLocal = rateToLocal))
        }
    }

    fun updateCurrency(currency: CurrencyEntity) {
        viewModelScope.launch {
            repository.updateCurrency(currency)
        }
    }

    fun deleteCurrency(currency: CurrencyEntity) {
        viewModelScope.launch {
            repository.deleteCurrency(currency)
        }
    }

    // Account CRUD
    fun addAccount(name: String, type: String, currencyCode: String, phone: String, notes: String) {
        val cid = activeCompany.value?.id ?: return
        viewModelScope.launch {
            repository.insertAccount(AccountEntity(companyId = cid, name = name, type = type, currencyCode = currencyCode, phone = phone, notes = notes))
        }
    }

    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
        }
    }

    // Product CRUD
    fun addProduct(name: String, category: String, unit: String, costPrice: Double, sellingPrice: Double, barcode: String, stockQuantity: Double, lowStock: Double) {
        val cid = activeCompany.value?.id ?: return
        viewModelScope.launch {
            repository.insertProduct(ProductEntity(
                companyId = cid, name = name, category = category, unit = unit,
                costPrice = costPrice, sellingPrice = sellingPrice, barcode = barcode,
                stockQuantity = stockQuantity, lowStockThreshold = lowStock
            ))
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

    // Invoice CRUD and sequential number helper
    fun addInvoice(type: String, accountId: Int, currencyCode: String, exchangeRate: Double, discount: Double, totalAmtLoc: Double, totalAmtFor: Double, itemsList: List<InvoiceItem>) {
        val cid = activeCompany.value?.id ?: return
        viewModelScope.launch {
            val count = invoices.value.filter { it.type == type }.size + 1
            val serializedItems = Json.encodeToString(itemsList)
            val invoice = InvoiceEntity(
                companyId = cid,
                invoiceNumber = count,
                type = type,
                accountId = accountId,
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                discount = discount,
                totalAmountLocal = totalAmtLoc,
                totalAmountForeign = totalAmtFor,
                detailsJson = serializedItems
            )
            repository.insertInvoice(invoice)

            // Adjust inventory stock based on type
            itemsList.forEach { i ->
                val p = products.value.find { it.name == i.name }
                if (p != null) {
                    val scale = when (type) {
                        "مبيع", "إتلاف" -> -i.quantity
                        "شراء" -> i.quantity
                        "مردود مبيعات" -> i.quantity
                        "مردود مشتريات" -> -i.quantity
                        else -> 0.0
                    }
                    val updatedStock = p.stockQuantity + scale
                    repository.updateProduct(p.copy(stockQuantity = if (updatedStock < 0) 0.0 else updatedStock))
                }
            }
        }
    }

    fun updateInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            repository.updateInvoice(invoice)
        }
    }

    fun deleteInvoice(invoice: InvoiceEntity) {
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
            // Note: In real life inventory would be reversed, but here we keep simulation robust and keep deletion straightforward.
        }
    }

    // Voucher CRUD
    fun addVoucher(type: String, accountId: Int, amtFor: Double, code: String, rate: Double, notes: String) {
        val cid = activeCompany.value?.id ?: return
        viewModelScope.launch {
            val voucher = VoucherEntity(
                companyId = cid,
                type = type,
                accountId = accountId,
                amountForeign = amtFor,
                currencyCode = code,
                exchangeRate = rate,
                amountLocal = amtFor * rate,
                notes = notes
            )
            repository.insertVoucher(voucher)
        }
    }

    fun updateVoucher(voucher: VoucherEntity) {
        viewModelScope.launch {
            repository.updateVoucher(voucher)
        }
    }

    fun deleteVoucher(voucher: VoucherEntity) {
        viewModelScope.launch {
            repository.deleteVoucher(voucher)
        }
    }

    // Attendance CRUD
    fun addAttendance(employeeName: String, date: String, status: String, notes: String) {
        val cid = activeCompany.value?.id ?: return
        viewModelScope.launch {
            repository.insertAttendance(AttendanceEntity(companyId = cid, employeeName = employeeName, date = date, status = status, notes = notes))
        }
    }

    fun updateAttendance(attendance: AttendanceEntity) {
        viewModelScope.launch {
            repository.updateAttendance(attendance)
        }
    }

    fun deleteAttendance(attendance: AttendanceEntity) {
        viewModelScope.launch {
            repository.deleteAttendance(attendance)
        }
    }

    // Manufacturing operations (assembling outputs)
    fun addManufacturingOperation(itemName: String, qty: Double, rawMats: List<RawMaterial>, addCosts: Double) {
        val cid = activeCompany.value?.id ?: return
        viewModelScope.launch {
            // raw materials cost estimation
            var rawCost = 0.0
            rawMats.forEach { mat ->
                rawCost += mat.quantity * mat.unitPrice
                // decrease raw materials stock from our items database
                val p = products.value.find { it.name == mat.name }
                if (p != null) {
                    val updatedStock = p.stockQuantity - mat.quantity
                    repository.updateProduct(p.copy(stockQuantity = if (updatedStock < 0) 0.0 else updatedStock))
                }
            }

            val totalCost = rawCost + addCosts
            val serializedMats = Json.encodeToString(rawMats)

            val mOperation = ManufacturingEntity(
                companyId = cid,
                itemName = itemName,
                quantityResult = qty,
                rawMaterialsJson = serializedMats,
                additionalCosts = addCosts,
                totalCostLocal = totalCost
            )
            repository.insertManufacturing(mOperation)

            // Add or increment the finished product in stock using totalCost/qty as its new unit cost price
            val costPerUnit = if (qty > 0) totalCost / qty else 0.0
            val pOut = products.value.find { it.name == itemName }
            if (pOut != null) {
                val updatedStock = pOut.stockQuantity + qty
                repository.updateProduct(pOut.copy(stockQuantity = updatedStock, costPrice = costPerUnit))
            } else {
                repository.insertProduct(ProductEntity(
                    companyId = cid,
                    name = itemName,
                    category = "جاهز للتسويق",
                    unit = "قطعة",
                    costPrice = costPerUnit,
                    sellingPrice = costPerUnit * 1.3, // Markup 30% dynamically
                    stockQuantity = qty
                ))
            }
        }
    }

    fun deleteManufacturing(m: ManufacturingEntity) {
        viewModelScope.launch {
            repository.deleteManufacturing(m)
        }
    }

    // Dynamic account balance calculatons. Returns balance scaled to Account's core currency code!
    fun getAccountBalance(accountId: Int): Double {
        val account = accounts.value.find { it.id == accountId } ?: return 0.0
        val accInv = invoices.value.filter { it.accountId == accountId }
        val accVou = vouchers.value.filter { it.accountId == accountId }

        var balanceLocal = 0.0

        if (account.type == "زبون") {
            // Sales (+), Returns (-), Receipt Voucher (-), Payment Voucher (+)
            accInv.forEach { inv ->
                val order = when (inv.type) {
                    "مبيع" -> inv.totalAmountLocal
                    "مردود مبيعات" -> -inv.totalAmountLocal
                    else -> 0.0
                }
                balanceLocal += order
            }
            accVou.forEach { vou ->
                val order = when (vou.type) {
                    "قبض" -> -vou.amountLocal
                    "دفع" -> vou.amountLocal
                    else -> 0.0
                }
                balanceLocal += order
            }
        } else if (account.type == "مورد") {
            // Purchase (+), Returns (-), Payment (-), Receipt (+)
            accInv.forEach { inv ->
                val order = when (inv.type) {
                    "شراء" -> inv.totalAmountLocal
                    "مردود مشتريات" -> -inv.totalAmountLocal
                    else -> 0.0
                }
                balanceLocal += order
            }
            accVou.forEach { vou ->
                val order = when (vou.type) {
                    "دفع" -> -vou.amountLocal
                    "قبض" -> vou.amountLocal
                    else -> 0.0
                }
                balanceLocal += order
            }
        } else if (account.type == "مصاريف") {
            // Payment vouchers (+)
            accVou.forEach { vou ->
                if (vou.type == "دفع") balanceLocal += vou.amountLocal
            }
        } else if (account.type == "ايرادات") {
            // Receipt vouchers (+)
            accVou.forEach { vou ->
                if (vou.type == "قبض") balanceLocal += vou.amountLocal
            }
        }

        // Convert balance to account currency
        val rate = currencies.value.find { it.code == account.currencyCode }?.rateToLocal ?: 1.0
        return if (rate > 0) balanceLocal / rate else balanceLocal
    }

    // Dynamic Profit and Loss Calculation (أرباح وخسائر)
    // Profits: sales, revenues. Expenses: cost of goods sold, expenses, damaged goods.
    fun getDynamicProfitAndLoss(): Map<String, Double> {
        var salesLocal = 0.0
        var salesReturnLocal = 0.0
        var purchaseLocal = 0.0
        var purchaseReturnLocal = 0.0
        var damageLocal = 0.0

        invoices.value.forEach { inv ->
            when (inv.type) {
                "مبيع" -> salesLocal += inv.totalAmountLocal
                "مردود مبيعات" -> salesReturnLocal += inv.totalAmountLocal
                "شراء" -> purchaseLocal += inv.totalAmountLocal
                "مردود مشتريات" -> purchaseReturnLocal += inv.totalAmountLocal
                "إتلاف" -> damageLocal += inv.totalAmountLocal
            }
        }

        var directExpensesLocal = 0.0
        var otherRevenuesLocal = 0.0

        vouchers.value.forEach { vou ->
            val acc = accounts.value.find { it.id == vou.accountId }
            if (acc != null) {
                if (acc.type == "مصاريف" && vou.type == "دفع") {
                    directExpensesLocal += vou.amountLocal
                }
                if (acc.type == "ايرادات" && vou.type == "قبض") {
                    otherRevenuesLocal += vou.amountLocal
                }
            }
        }

        // Cost of goods sold: estimated by quantities of sales * item.costPrice
        var costOfGoodsSold = 0.0
        invoices.value.filter { it.type == "مبيع" }.forEach { inv ->
            try {
                val items = Json.decodeFromString<List<InvoiceItem>>(inv.detailsJson)
                items.forEach { item ->
                    val origCost = products.value.find { it.name == item.name }?.costPrice ?: 0.0
                    costOfGoodsSold += item.quantity * origCost
                }
            } catch (e: Exception) {}
        }

        val netSales = salesLocal - salesReturnLocal
        val grossProfit = netSales - costOfGoodsSold
        val netProfit = (grossProfit + otherRevenuesLocal) - (directExpensesLocal + damageLocal)

        return mapOf(
            "sales" to salesLocal,
            "cogs" to costOfGoodsSold,
            "revenues" to otherRevenuesLocal,
            "expenses" to directExpensesLocal,
            "damage" to damageLocal,
            "netSales" to netSales,
            "grossProfit" to grossProfit,
            "netProfit" to netProfit
        )
    }

    // Backup current company schema as json format
    fun exportBackup(context: Context, targetUri: Uri): Boolean {
        return try {
            val backup = DatabaseBackup(
                companies = allCompanies.value,
                currencies = currencies.value,
                accounts = accounts.value,
                products = products.value,
                invoices = invoices.value,
                vouchers = vouchers.value,
                attendance = attendance.value,
                manufacturing = manufacturing.value
            )
            val jsonString = Json.encodeToString(backup)
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                output.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // Import full database schema
    suspend fun importBackup(context: Context, sourceUri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return false
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val jsonString = reader.readText()
            inputStream.close()

            val backup = Json.decodeFromString<DatabaseBackup>(jsonString)

            // Re-populate rooms sequentially
            backup.companies.forEach { repository.insertCompany(it) }
            backup.currencies.forEach { repository.insertCurrency(it) }
            backup.accounts.forEach { repository.insertAccount(it) }
            backup.products.forEach { repository.insertProduct(it) }
            backup.invoices.forEach { repository.insertInvoice(it) }
            backup.vouchers.forEach { repository.insertVoucher(it) }
            backup.attendance.forEach { repository.insertAttendance(it) }
            backup.manufacturing.forEach { repository.insertManufacturing(it) }

            // Trigger reset state
            if (backup.companies.isNotEmpty()) {
                activeCompany.value = backup.companies.first()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // Export report structure to standard readable CSV
    fun exportReportToCsv(context: Context, targetUri: Uri): Boolean {
        return try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("النوع,التفاصيل,المبلغ المحلي,المبلغ الأجنبي,العملة,التاريخ\n")

            // Invoices lines
            invoices.value.forEach { i ->
                val accName = accounts.value.find { it.id == i.accountId }?.name ?: "مجهول"
                csvBuilder.append("فاتورة ${i.type},رقم ${i.invoiceNumber} - جهة ${accName},${i.totalAmountLocal},${i.totalAmountForeign},${i.currencyCode},${i.date}\n")
            }

            // Vouchers lines
            vouchers.value.forEach { v ->
                val accName = accounts.value.find { it.id == v.accountId }?.name ?: "مجهول"
                csvBuilder.append("سند ${v.type},(${v.notes}) - جهة ${accName},${v.amountLocal},${v.amountForeign},${v.currencyCode},${v.date}\n")
            }

            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                output.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // Invoke Gemini intelligent financial advisory
    fun requestAiConsultation() {
        val cid = activeCompany.value ?: return
        viewModelScope.launch {
            isEvaluatingReport.value = true
            aiResponse.value = "جاري تجميع الحسابات وإحصائيات المخزون وتفاصيل الفواتير لتحليلها بواسطة الذكاء الاصطناعي..."

            val pl = getDynamicProfitAndLoss()
            
            val totalInvoicesCount = invoices.value.size
            val lowStockProducts = products.value.filter { it.stockQuantity <= it.lowStockThreshold }
            val clients = accounts.value.filter { it.type == "زبون" }
            val suppliers = accounts.value.filter { it.type == "مورد" }

            val prompt = """
                تحليل مالي مفصل للشركة النالية: ${cid.name}
                العملة المحلية الأساسية: ${cid.currencyLocal}
                
                إحصائيات المبيعات والأرباح:
                - إجمالي المبيعات الإجمالي: ${pl["sales"]} ${cid.currencyLocal}
                - العائد الصافي للمبيعات: ${pl["netSales"]} ${cid.currencyLocal}
                - تكلفة البضاعة المباعة المقدّرة: ${pl["cogs"]} ${cid.currencyLocal}
                - المصاريف التشغيلية المدفوعة: ${pl["expenses"]} ${cid.currencyLocal}
                - قيمة المواد التالفة أو المعدومة: ${pl["damage"]} ${cid.currencyLocal}
                - أية إيرادات خارجية غير تشغيلية: ${pl["revenues"]} ${cid.currencyLocal}
                - الأرباح الصافية الاجمالية للشركة: ${pl["netProfit"]} ${cid.currencyLocal}
                
                إحصائيات الفواتير والمخزون:
                - إجمالي عدد الفواتير المسجلة: $totalInvoicesCount
                - عدد السلع التي توشك مخازنها على النفاد (تنبيه نقص المخزون): ${lowStockProducts.size}
                - قائمة بأسماء السلع قليلة المخزون: ${lowStockProducts.joinToString { "${it.name} (المتبقي: ${it.stockQuantity} ${it.unit})" }}
                
                العملاء والموردين:
                - عدد العملاء الناشطين (زبائن): ${clients.size}
                - عدد الموردين المعتمدين: ${suppliers.size}

                تقديم:
                1. تقييم تفصيلي وسريع وموجز للربحية والأداء العام للشركة.
                2. توصية محددة للتصرف حيال السلع منخفضة المخزون أو السلع المبيعة.
                3. تقديم 3 نصائح عملية واضحة جداً لزيادة هامش الربح الإجمالي وتقليل التكاليف الإدارية والتشغيلية أو إدارة الديون والعملات الأجنبية.
                بثقة واحترافية وبأسلوب عربي راقٍ ومنظم مع تحفيز ريادي ممتاز!
            """.trimIndent()

            val response = GeminiService.generateAiFinancialReport(prompt)
            aiResponse.value = response
            isEvaluatingReport.value = false
        }
    }
}
