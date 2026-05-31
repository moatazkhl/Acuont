package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CompanyInfo(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val currency: String
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("smart_accountant_prefs", android.content.Context.MODE_PRIVATE)

    @Volatile
    private var repository: AppRepository = AppRepository(AppDatabase.getDatabase(application, "company_c_nour").appDao())

    private val _companiesList = MutableStateFlow<List<CompanyInfo>>(emptyList())
    val companiesList: StateFlow<List<CompanyInfo>> = _companiesList.asStateFlow()

    private val _activeCompanyId = MutableStateFlow("c_nour")
    val activeCompanyId: StateFlow<String> = _activeCompanyId.asStateFlow()

    private val _companyName = MutableStateFlow("شركة النور للتجارة")
    val companyName: StateFlow<String> = _companyName.asStateFlow()

    private val _companyPhone = MutableStateFlow("+963933112233")
    val companyPhone: StateFlow<String> = _companyPhone.asStateFlow()

    private val _companyAddress = MutableStateFlow("دمشق، مدحت باشا")
    val companyAddress: StateFlow<String> = _companyAddress.asStateFlow()

    private val _companyCurrency = MutableStateFlow("ل.س")
    val companyCurrency: StateFlow<String> = _companyCurrency.asStateFlow()

    // --- Core States ---
    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _vouchers = MutableStateFlow<List<Voucher>>(emptyList())
    val vouchers: StateFlow<List<Voucher>> = _vouchers.asStateFlow()

    private val _categories = MutableStateFlow<List<ProductCategory>>(emptyList())
    val categories: StateFlow<List<ProductCategory>> = _categories.asStateFlow()

    private var activeCollectionJob: kotlinx.coroutines.Job? = null

    fun getTodayDateStr(): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(java.util.Date())
        } catch (e: Exception) {
            "2026-05-31"
        }
    }

    init {
        loadCompaniesFromPrefs()
        refreshBackupList()
        initializeGoogleDrive()
    }

    private fun parseCompanyStr(str: String): CompanyInfo {
        val parts = str.split("::")
        val id = parts.getOrNull(0) ?: "c_unknown"
        val name = parts.getOrNull(1) ?: "شركة غير معروفة"
        val phone = parts.getOrNull(2) ?: "غير متوفر"
        val address = parts.getOrNull(3) ?: "غير متوفر"
        val currency = parts.getOrNull(4) ?: "ل.س"
        return CompanyInfo(id, name, phone, address, currency)
    }

    private fun serializeCompany(c: CompanyInfo): String {
        return "${c.id}::${c.name}::${c.phone}::${c.address}::${c.currency}"
    }

    fun loadCompaniesFromPrefs() {
        val defaultCompaniesVal = "c_nour::شركة النور للتجارة::+963933112233::دمشق، مدحت باشا::ل.س;;c_amal::مؤسسة الأمل الصناعية::+963944556677::حلب، المدينة الصناعية::ل.س"
        val savedStr = prefs.getString("companies_list", defaultCompaniesVal) ?: defaultCompaniesVal
        val list = savedStr.split(";;").filter { it.isNotBlank() }.map {
            parseCompanyStr(it)
        }
        _companiesList.value = list

        val activeId = prefs.getString("active_company_id", "c_nour") ?: "c_nour"
        _activeCompanyId.value = activeId

        val activeCompany = list.find { it.id == activeId } ?: list.firstOrNull() ?: CompanyInfo("c_nour", "شركة النور للتجارة", "+963933112233", "دمشق، مدحت باشا", "ل.س")
        _companyName.value = activeCompany.name
        _companyPhone.value = activeCompany.phone
        _companyAddress.value = activeCompany.address
        _companyCurrency.value = activeCompany.currency

        switchCompanyDb(activeCompany.id, activeCompany.name, isStartUp = true)
    }

    fun switchCompanyDb(companyId: String, companyNameStr: String, isStartUp: Boolean = false) {
        val app = getApplication<Application>()
        val db = AppDatabase.getDatabase(app, "company_$companyId")
        val repo = AppRepository(db.appDao())
        repository = repo

        _activeCompanyId.value = companyId
        
        val matched = _companiesList.value.find { it.id == companyId }
        if (matched != null) {
            _companyName.value = matched.name
            _companyPhone.value = matched.phone
            _companyAddress.value = matched.address
            _companyCurrency.value = matched.currency
        } else {
            _companyName.value = companyNameStr
        }

        prefs.edit()
            .putString("active_company_id", companyId)
            .apply()

        viewModelScope.launch {
            repo.prepopulateIfNeeded()
        }

        activeCollectionJob?.cancel()
        activeCollectionJob = viewModelScope.launch {
            launch {
                repo.invoices.collect {
                    _invoices.value = it
                }
            }
            launch {
                repo.accounts.collect {
                    _accounts.value = it
                }
            }
            launch {
                repo.products.collect {
                    _products.value = it
                }
            }
            launch {
                repo.vouchers.collect {
                    _vouchers.value = it
                }
            }
            launch {
                repo.categories.collect {
                    _categories.value = it
                }
            }
        }

        if (!isStartUp) {
            triggerToast("تم الانتقال بنجاح إلى شركة: $companyNameStr ✓")
        }
    }

    fun createNewCompany(name: String, phone: String, address: String, currency: String) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        val trimmedAddress = address.trim()
        val trimmedCurrency = currency.trim()

        if (trimmedName.isBlank()) {
            triggerToast("الرجاء إدخال اسم الشركة")
            return
        }
        if (trimmedPhone.isBlank()) {
            triggerToast("الرجاء إدخال رقم الهاتف")
            return
        }
        if (trimmedAddress.isBlank()) {
            triggerToast("الرجاء إدخال عنوان الشركة")
            return
        }
        if (trimmedCurrency.isBlank()) {
            triggerToast("الرجاء إدخال العملة الافتراضية")
            return
        }

        val newId = "c_" + System.currentTimeMillis().toString().takeLast(6)
        val newCompany = CompanyInfo(newId, trimmedName, trimmedPhone, trimmedAddress, trimmedCurrency)

        val currentList = _companiesList.value.toMutableList()
        currentList.add(newCompany)
        _companiesList.value = currentList

        val savedStr = currentList.joinToString(";;") { serializeCompany(it) }
        prefs.edit().putString("companies_list", savedStr).apply()

        val app = getApplication<Application>()
        viewModelScope.launch {
            val newDb = AppDatabase.getDatabase(app, "company_$newId")
            val newRepo = AppRepository(newDb.appDao())
            newRepo.prepopulateIfNeeded()
        }

        triggerToast("تم تأسيس شركة: $trimmedName بنجاح ✓")
    }

    fun deleteCompany(companyId: String) {
        if (_companiesList.value.size <= 1) {
            triggerToast("لا يمكن حذف الشركة الوحيدة المتبقية!")
            return
        }
        val currentList = _companiesList.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == companyId }
        if (index >= 0) {
            val removed = currentList.removeAt(index)
            _companiesList.value = currentList

            val savedStr = currentList.joinToString(";;") { serializeCompany(it) }
            prefs.edit().putString("companies_list", savedStr).apply()

            if (_activeCompanyId.value == companyId) {
                val fallback = currentList.first()
                switchCompanyDb(fallback.id, fallback.name)
            }

            val app = getApplication<Application>()
            try {
                app.deleteDatabase("company_$companyId")
                app.deleteDatabase("company_$companyId-journal")
                app.deleteDatabase("company_$companyId-shm")
                app.deleteDatabase("company_$companyId-wal")
            } catch (e: Exception) {
                // Ignore silent cleanup errors
            }

            triggerToast("تم حذف شركة '${removed.name}' ودواوينها المالية ✓")
        }
    }

    fun renameCompany(companyId: String, newName: String) {
        val currentList = _companiesList.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == companyId }
        if (index >= 0) {
            val old = currentList[index]
            updateCompanyDetails(companyId, newName, old.phone, old.address, old.currency)
        }
    }

    fun updateCompanyDetails(companyId: String, name: String, phone: String, address: String, currency: String) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        val trimmedAddress = address.trim()
        val trimmedCurrency = currency.trim()

        if (trimmedName.isBlank()) {
            triggerToast("اسم الشركة لا يمكن أن يكون فارغاً")
            return
        }
        if (trimmedPhone.isBlank()) {
            triggerToast("رقم الهاتف لا يمكن أن يكون فارغاً")
            return
        }
        if (trimmedAddress.isBlank()) {
            triggerToast("عنوان الشركة لا يمكن أن يكون فارغاً")
            return
        }
        if (trimmedCurrency.isBlank()) {
            triggerToast("العملة الافتراضية لا يمكن أن تكون فارغة")
            return
        }

        val currentList = _companiesList.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == companyId }
        if (index >= 0) {
            val updated = CompanyInfo(companyId, trimmedName, trimmedPhone, trimmedAddress, trimmedCurrency)
            currentList[index] = updated
            _companiesList.value = currentList

            val savedStr = currentList.joinToString(";;") { serializeCompany(it) }
            prefs.edit().putString("companies_list", savedStr).apply()

            if (_activeCompanyId.value == companyId) {
                _companyName.value = trimmedName
                _companyPhone.value = trimmedPhone
                _companyAddress.value = trimmedAddress
                _companyCurrency.value = trimmedCurrency
            }
            triggerToast("تم تعديل وحفظ بيانات الشركة: $trimmedName ✓")
        }
    }

    // --- UI Navigation and Filtering ---
    private val _currentTab = MutableStateFlow("invoices") // invoices, accounts, products, reports, settings
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _invoiceFilter = MutableStateFlow("all") // all, sale, purchase, return
    val invoiceFilter: StateFlow<String> = _invoiceFilter.asStateFlow()

    private val _accountFilter = MutableStateFlow("all") // all, customer, supplier, expense, other
    val accountFilter: StateFlow<String> = _accountFilter.asStateFlow()

    private val _productFilter = MutableStateFlow("all") // all, food, electronics, other
    val productFilter: StateFlow<String> = _productFilter.asStateFlow()

    // --- Search Queries ---
    private val _invoiceSearch = MutableStateFlow("")
    val invoiceSearch: StateFlow<String> = _invoiceSearch.asStateFlow()

    private val _accountSearch = MutableStateFlow("")
    val accountSearch: StateFlow<String> = _accountSearch.asStateFlow()

    private val _productSearch = MutableStateFlow("")
    val productSearch: StateFlow<String> = _productSearch.asStateFlow()

    // --- Customization Settings ---
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _useArabicNumerals = MutableStateFlow(true)
    val useArabicNumerals: StateFlow<Boolean> = _useArabicNumerals.asStateFlow()



    private val _vatRate = MutableStateFlow(0) // 0%, 5%, 10%, 15%
    val vatRate: StateFlow<Int> = _vatRate.asStateFlow()

    private val _decimalPoints = MutableStateFlow(0) // 0, 2, 3
    val decimalPoints: StateFlow<Int> = _decimalPoints.asStateFlow()

    // --- Currency Exchange ---
    val rateUSD = MutableStateFlow(13700.0)
    val rateEUR = MutableStateFlow(14900.0)
    val rateSAR = MutableStateFlow(3650.0)
    val rateTRY = MutableStateFlow(380.0)

    val exchangeRates: StateFlow<List<ExchangeRate>> = repository.exchangeRates.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveExchangeRateForDate(date: String, rateUSD: Double, rateEUR: Double, rateSAR: Double, rateTRY: Double) {
        viewModelScope.launch {
            repository.insertExchangeRate(
                ExchangeRate(
                    date = date,
                    rateUSD = rateUSD,
                    rateEUR = rateEUR,
                    rateSAR = rateSAR,
                    rateTRY = rateTRY
                )
            )
            triggerToast("تم حفظ أسعار الصرف لتاريخ $date بنجاح ✓")
        }
    }

    // --- Active Document Creation States ---
    // New Invoice Form State
    val tempInvoiceItems = MutableStateFlow<List<InvoiceItem>>(emptyList())
    val selectedInvoiceCustomer = MutableStateFlow<Account?>(null)
    val selectedInvoiceDate = MutableStateFlow("")
    val invoiceNotes = MutableStateFlow("")
    val invoiceCurrency = MutableStateFlow("ل.س")
    val invoiceType = MutableStateFlow("sale")
    val invoicePaymentType = MutableStateFlow("cash") // "cash" (نقدي), "credit" (آجل)
    val invoicePaidAmount = MutableStateFlow("")
    val invoiceDiscount = MutableStateFlow("")
    val invoiceTax = MutableStateFlow("")
    val editingInvoice = MutableStateFlow<Invoice?>(null)

    fun loadInvoiceForEditing(invoice: Invoice) {
        editingInvoice.value = invoice
        invoiceType.value = invoice.type
        selectedInvoiceCustomer.value = accounts.value.find { it.name == invoice.customer }
        selectedInvoiceDate.value = invoice.date
        invoiceNotes.value = invoice.notes
        invoiceCurrency.value = invoice.currency
        invoicePaymentType.value = invoice.paymentType
        invoicePaidAmount.value = if (invoice.paidAmount > 0.0) invoice.paidAmount.toInt().toString() else ""
        invoiceDiscount.value = if (invoice.discount > 0.0) invoice.discount.toInt().toString() else ""
        
        val itemsList = deserializeItems(invoice.itemsJson)
        val subtotal = itemsList.sumOf { it.qty * it.price }
        val netAmount = subtotal - (invoice.discount)
        val computedTaxPercent = if (netAmount > 0.0) {
            (invoice.tax / netAmount) * 100.0
        } else {
            0.0
        }
        invoiceTax.value = if (computedTaxPercent > 0.0) {
            if (computedTaxPercent % 1.0 == 0.0) {
                computedTaxPercent.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", computedTaxPercent)
            }
        } else {
            ""
        }
        
        tempInvoiceItems.value = itemsList
    }

    fun clearInvoiceForm() {
        editingInvoice.value = null
        tempInvoiceItems.value = emptyList()
        selectedInvoiceCustomer.value = null
        selectedInvoiceDate.value = getTodayDateStr() // Default current local date
        invoiceNotes.value = ""
        invoiceCurrency.value = "ل.س"
        invoiceType.value = "sale"
        invoicePaymentType.value = "cash"
        invoicePaidAmount.value = ""
        invoiceDiscount.value = ""
        invoiceTax.value = ""
    }

    // New Product Form State
    val editingProduct = MutableStateFlow<Product?>(null)
    val newProductName = MutableStateFlow("")
    val newProductCode = MutableStateFlow("")
    val newProductCategory = MutableStateFlow("other")
    val newProductUnit = MutableStateFlow("قطعة")
    val newProductQty = MutableStateFlow("")
    val newProductMinQty = MutableStateFlow("")
    val newProductBuyPrice = MutableStateFlow("")
    val newProductSellPrice = MutableStateFlow("")
    val newProductBarcode = MutableStateFlow("")
    val newProductIcon = MutableStateFlow("📦")

    fun startEditingProduct(product: Product) {
        editingProduct.value = product
        newProductName.value = product.name
        newProductCode.value = product.code
        newProductCategory.value = product.cat
        newProductUnit.value = product.unit
        newProductQty.value = product.qty.toString()
        newProductMinQty.value = product.minQty.toString()
        newProductBuyPrice.value = product.buyPrice.toString()
        newProductSellPrice.value = product.sellPrice.toString()
        newProductBarcode.value = product.barcode
        newProductIcon.value = product.icon
    }

    fun clearProductForm() {
        editingProduct.value = null
        newProductName.value = ""
        newProductCode.value = ""
        newProductCategory.value = "other"
        newProductUnit.value = "قطعة"
        newProductQty.value = ""
        newProductMinQty.value = ""
        newProductBuyPrice.value = ""
        newProductSellPrice.value = ""
        newProductBarcode.value = ""
        newProductIcon.value = "📦"
    }

    // New Account Form State
    val editingAccount = MutableStateFlow<Account?>(null)
    val newAccountName = MutableStateFlow("")
    val newAccountType = MutableStateFlow("customer")
    val newAccountBalance = MutableStateFlow("")
    val newAccountPhone = MutableStateFlow("")
    val newAccountAddress = MutableStateFlow("")
    val newAccountNotes = MutableStateFlow("")
    val newAccountCurrency = MutableStateFlow("ل.س")

    fun startEditingAccount(account: Account) {
        editingAccount.value = account
        newAccountName.value = account.name
        newAccountType.value = account.type
        newAccountBalance.value = account.balance.toString()
        newAccountPhone.value = account.phone
        newAccountAddress.value = account.address
        newAccountNotes.value = account.notes
        newAccountCurrency.value = account.currency
    }

    fun clearAccountForm() {
        editingAccount.value = null
        newAccountName.value = ""
        newAccountBalance.value = ""
        newAccountPhone.value = ""
        newAccountAddress.value = ""
        newAccountNotes.value = ""
        newAccountCurrency.value = _companyCurrency.value
    }

    // New Voucher Form State
    val voucherType = MutableStateFlow("receipt") // receipt / payment
    val voucherSelectedAccount = MutableStateFlow<Account?>(null)
    val voucherAmount = MutableStateFlow("")
    val voucherDesc = MutableStateFlow("")
    val voucherDate = MutableStateFlow("")

    // Calculator State
    val calcExpression = MutableStateFlow("")
    val calcResultDisplay = MutableStateFlow("0")

    // Current Account Statement Account
    val statementAccount = MutableStateFlow<Account?>(null)

    // Toast Message helper
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun triggerToast(msg: String) {
        viewModelScope.launch {
            _toastMessage.emit(msg)
        }
    }

    // --- Helpers / Utility Methods ---
    fun setTab(tab: String) {
        _currentTab.value = tab
    }

    fun setInvoiceFilter(filter: String) {
        _invoiceFilter.value = filter
    }

    fun setAccountFilter(filter: String) {
        _accountFilter.value = filter
    }

    fun setProductFilter(filter: String) {
        _productFilter.value = filter
    }

    fun setInvoiceSearch(q: String) {
        _invoiceSearch.value = q
    }

    fun setAccountSearch(q: String) {
        _accountSearch.value = q
    }

    fun setProductSearch(q: String) {
        _productSearch.value = q
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun toggleArabicNumerals() {
        _useArabicNumerals.value = !_useArabicNumerals.value
    }

    fun setCompanyName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            val activeId = _activeCompanyId.value
            val currentList = _companiesList.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == activeId }
            if (index >= 0) {
                val old = currentList[index]
                updateCompanyDetails(activeId, trimmed, old.phone, old.address, old.currency)
            } else {
                _companyName.value = trimmed
            }
        }
    }

    fun setVatRate(rate: Int) {
        _vatRate.value = rate
    }

    fun setDecimalPoints(points: Int) {
        _decimalPoints.value = points
    }

    // Numbers display format (Eastern Arabic vs Western numerals)
    fun formatCurrency(amount: Double): String {
        val formatPattern = when (_decimalPoints.value) {
            2 -> "#,##0.00"
            3 -> "#,##0.000"
            else -> "#,##0"
        }
        val defSymbols = DecimalFormatSymbols()
        if (_useArabicNumerals.value) {
            // Eastern numbers: ٠ ١ ٢ ٣ ٤ ٥ ٦ ٧ ٨ ٩
            defSymbols.zeroDigit = '٠'
            defSymbols.groupingSeparator = '٬'
            defSymbols.decimalSeparator = '٫'
        } else {
            defSymbols.zeroDigit = '0'
            defSymbols.groupingSeparator = ','
            defSymbols.decimalSeparator = '.'
        }
        val df = DecimalFormat(formatPattern, defSymbols)
        return df.format(amount)
    }

    fun formatCurrencyWithSymbol(amount: Double): String {
        return "${formatCurrency(amount)} ${_companyCurrency.value}"
    }

    fun getRateInSyp(currency: String): Double {
        return when (currency) {
            "ل.س" -> 1.0
            "USD" -> rateUSD.value
            "EUR" -> rateEUR.value
            "SAR" -> rateSAR.value
            "TRY" -> rateTRY.value
            _companyCurrency.value -> 1.0
            else -> 1.0
        }
    }

    fun getAccountBalanceInSyp(account: Account): Double {
        return account.balance * getRateInSyp(account.currency)
    }

    // --- Persistence Functions ---
    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearAllData()
            triggerToast("تم تصفية قاعدة البيانات بالكامل ✓")
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            repository.clearAllData()
            repository.loadCustomSeedData()
            triggerToast("تم توليد بيانات تجريبية محاسبية غنية بنجاح ✓")
        }
    }

    fun saveAccount() {
        val name = newAccountName.value.trim()
        if (name.isBlank()) {
            triggerToast("يرجى إدخال اسم الحساب")
            return
        }
        val colors = listOf("#4a7fa5", "#2ebd7a", "#e03c3c", "#f5a623", "#9b59b6", "#e67e22")
        val randomColor = colors.random()
        val bal = newAccountBalance.value.toDoubleOrNull() ?: 0.0

        val currentEditing = editingAccount.value
        val accountToSave = if (currentEditing != null) {
            currentEditing.copy(
                name = name,
                type = newAccountType.value,
                balance = bal,
                phone = newAccountPhone.value,
                address = newAccountAddress.value,
                notes = newAccountNotes.value,
                currency = newAccountCurrency.value
            )
        } else {
            Account(
                id = "A" + System.currentTimeMillis().toString().takeLast(6),
                name = name,
                type = newAccountType.value,
                balance = bal,
                phone = newAccountPhone.value,
                address = newAccountAddress.value,
                notes = newAccountNotes.value,
                color = randomColor,
                currency = newAccountCurrency.value
            )
        }

        viewModelScope.launch {
            repository.insertAccount(accountToSave)
            clearAccountForm()
            triggerToast(if (currentEditing != null) "تم تعديل الحساب بنجاح ✓" else "تم إضافة الحساب بنجاح ✓")
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repository.deleteAccount(account)
            triggerToast("تم حذف الحساب بنجاح ✓")
        }
    }

    fun saveProduct() {
        val name = newProductName.value.trim()
        if (name.isBlank()) {
            triggerToast("يرجى إدخال اسم المادة")
            return
        }
        val sell = newProductSellPrice.value.toDoubleOrNull() ?: 0.0
        val buy = newProductBuyPrice.value.toDoubleOrNull() ?: 0.0
        val qty = newProductQty.value.toIntOrNull() ?: 0
        val min = newProductMinQty.value.toIntOrNull() ?: 0
        
        // Auto-generate barcode if blank
        val bar = newProductBarcode.value.ifBlank {
            // Generate valid looking EAN-13 style barcode starting with 622 (Egypt/Syria regional code style or similar)
            "622" + (1000000000 + (Math.random() * 900000000).toLong()).toString()
        }

        val currentEditing = editingProduct.value
        val productToSave = if (currentEditing != null) {
            currentEditing.copy(
                name = name,
                code = newProductCode.value.ifBlank { currentEditing.code },
                cat = newProductCategory.value,
                unit = newProductUnit.value,
                qty = qty,
                minQty = min,
                buyPrice = buy,
                sellPrice = sell,
                barcode = bar,
                icon = newProductIcon.value
            )
        } else {
            Product(
                id = "P" + System.currentTimeMillis().toString().takeLast(6),
                name = name,
                code = newProductCode.value.ifBlank { "P-" + System.currentTimeMillis().toString().takeLast(4) },
                cat = newProductCategory.value,
                unit = newProductUnit.value,
                qty = qty,
                minQty = min,
                buyPrice = buy,
                sellPrice = sell,
                barcode = bar,
                icon = newProductIcon.value
            )
        }

        viewModelScope.launch {
            repository.insertProduct(productToSave)
            clearProductForm()
            triggerToast(if (currentEditing != null) "تم تعديل المادة بنجاح ✓" else "تم إضافة المادة بنجاح ✓")
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            triggerToast("تم حذف المادة بنجاح ✓")
        }
    }

    fun saveCategory(name: String, icon: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            triggerToast("اسم الفئة لا يمكن أن يكون فارغاً")
            return
        }
        val id = "cat_" + System.currentTimeMillis().toString().takeLast(6)
        viewModelScope.launch {
            repository.insertCategory(ProductCategory(id = id, name = trimmedName, icon = icon.ifBlank { "📁" }))
            triggerToast("تمت إضافة الفئة ($trimmedName) بنجاح ✓")
        }
    }

    fun deleteCategory(category: ProductCategory) {
        viewModelScope.launch {
            repository.deleteCategory(category)
            triggerToast("تم حذف الفئة (${category.name}) ✓")
        }
    }

    fun saveInvoice(status: String) {
        val cust = selectedInvoiceCustomer.value
        if (cust == null) {
            triggerToast("يرجى اختيار العميل أولاً")
            return
        }
        val items = tempInvoiceItems.value
        if (items.isEmpty()) {
            triggerToast("لم يتم إضافة مواد للفاتورة")
            return
        }

        val subtotal = items.sumOf { it.qty * it.price }
        val discountVal = invoiceDiscount.value.toDoubleOrNull() ?: 0.0
        val taxPercent = invoiceTax.value.toDoubleOrNull() ?: 0.0
        val taxVal = (subtotal - discountVal) * (taxPercent / 100.0)
        val total = subtotal - discountVal + taxVal
        val grossProfit = items.sumOf { it.qty * (it.price - it.cost) }
        val profit = grossProfit - discountVal
        val dateVal = selectedInvoiceDate.value.ifBlank { getTodayDateStr() }

        val invStr = serializeItems(items)

        val isEdit = editingInvoice.value != null
        val newInvoiceId = editingInvoice.value?.id ?: ("INV-" + System.currentTimeMillis().toString().takeLast(5))

        val newInvoice = Invoice(
            id = newInvoiceId,
            type = invoiceType.value,
            customer = cust.name,
            date = dateVal,
            total = total,
            profit = profit,
            status = status,
            notes = invoiceNotes.value,
            itemsJson = invStr,
            currency = invoiceCurrency.value,
            paymentType = invoicePaymentType.value,
            paidAmount = invoicePaidAmount.value.toDoubleOrNull() ?: 0.0,
            discount = discountVal,
            tax = taxVal
        )

        viewModelScope.launch {
            // If editing, first reverse the OLD invoice
            val oldInv = editingInvoice.value
            if (oldInv != null) {
                // Reverse old product quantities
                if (oldInv.status == "saved") {
                    val oldItems = deserializeItems(oldInv.itemsJson)
                    products.value.forEach { product ->
                        val matchedItem = oldItems.find { it.name == product.name }
                        if (matchedItem != null) {
                            val restoredQty = when (oldInv.type) {
                                "sale" -> product.qty + matchedItem.qty
                                "purchase" -> maxOf(0, product.qty - matchedItem.qty)
                                "return_sale" -> maxOf(0, product.qty - matchedItem.qty)
                                "return_purchase" -> product.qty + matchedItem.qty
                                else -> product.qty + matchedItem.qty
                            }
                            repository.updateProductQuantity(product.id, restoredQty)
                        }
                    }
                    // Reverse account balance and delete matching automatic vouchers
                    repository.reverseInvoiceBalanceEffects(
                        oldInv,
                        usdRate = rateUSD.value,
                        eurRate = rateEUR.value,
                        sarRate = rateSAR.value,
                        tryRate = rateTRY.value
                    )
                }
                // Delete the old invoice itself
                repository.deleteInvoiceById(oldInv.id)
            }

            // Insert new / updated invoice
            if (status == "saved") {
                repository.insertInvoice(
                    newInvoice,
                    usdRate = rateUSD.value,
                    eurRate = rateEUR.value,
                    sarRate = rateSAR.value,
                    tryRate = rateTRY.value
                )
            } else {
                repository.insertInvoiceRaw(newInvoice)
            }

            // Deduct or add to product quantities based on invoice type
            products.value.forEach { product ->
                val matchedItem = items.find { it.name == product.name }
                if (matchedItem != null) {
                    val remainingStock = when (invoiceType.value) {
                        "sale" -> maxOf(0, product.qty - matchedItem.qty)
                        "purchase" -> product.qty + matchedItem.qty
                        "return_sale" -> product.qty + matchedItem.qty
                        "return_purchase" -> maxOf(0, product.qty - matchedItem.qty)
                        else -> maxOf(0, product.qty - matchedItem.qty)
                    }
                    repository.updateProductQuantity(product.id, remainingStock)
                }
            }

            clearInvoiceForm()
            triggerToast(if (isEdit) "تم تعديل وحفظ الفاتورة بنجاح ✓" else if (status == "draft") "تم حفظ المسودة" else "تمت الفاتورة والطباعة ✓")
        }
    }

    fun saveVoucher() {
        val acc = voucherSelectedAccount.value
        if (acc == null) {
            triggerToast("الرجاء تحديد الحساب")
            return
        }
        val amt = voucherAmount.value.toDoubleOrNull() ?: 0.0
        if (amt <= 0) {
            triggerToast("الرجاء إدخال مبلغ صحيح")
            return
        }

        val dateVal = voucherDate.value.ifBlank { getTodayDateStr() }

        val newVoucher = Voucher(
            type = voucherType.value,
            accountId = acc.id,
            amount = amt,
            desc = voucherDesc.value.ifBlank { if (voucherType.value == "receipt") "سند قبض نقدي" else "سند صرف نقدي" },
            date = dateVal
        )

        viewModelScope.launch {
            repository.insertVoucher(newVoucher)

            // Strictly update the account balance in SQLite database
            val balanceDiff = if (newVoucher.type == "receipt") amt else -amt
            repository.updateAccountBalance(acc.id, acc.balance + balanceDiff)

            voucherAmount.value = ""
            voucherDesc.value = ""
            voucherSelectedAccount.value = null
            triggerToast("تم حفظ السند المحاسبي ✓")
        }
    }

    fun deleteInvoiceCascaded(invoice: Invoice) {
        viewModelScope.launch {
            repository.deleteInvoiceById(invoice.id)
            // Restore product quantities if saved
            if (invoice.status == "saved") {
                val items = deserializeItems(invoice.itemsJson)
                products.value.forEach { product ->
                    val matchedItem = items.find { it.name == product.name }
                    if (matchedItem != null) {
                        val restoredQty = when (invoice.type) {
                            "sale" -> product.qty + matchedItem.qty
                            "purchase" -> maxOf(0, product.qty - matchedItem.qty)
                            "return_sale" -> maxOf(0, product.qty - matchedItem.qty)
                            "return_purchase" -> product.qty + matchedItem.qty
                            else -> product.qty + matchedItem.qty
                        }
                        repository.updateProductQuantity(product.id, restoredQty)
                    }
                }
                // Reverse account balance adjustment with cross-currency conversion
                repository.reverseInvoiceBalanceEffects(
                    invoice,
                    usdRate = rateUSD.value,
                    eurRate = rateEUR.value,
                    sarRate = rateSAR.value,
                    tryRate = rateTRY.value
                )
            }
            triggerToast("تم حذف الفاتورة بنجاح ✓")
        }
    }

    // --- Invoice Item Management ---
    fun changeInvoiceCurrency(newCurrency: String) {
        val oldCurrency = invoiceCurrency.value
        if (oldCurrency == newCurrency) return
        
        invoiceCurrency.value = newCurrency
        
        // Convert existing item prices and costs from oldCurrency to base (SYP), then to newCurrency
        val oldRate = getRateInSyp(oldCurrency)
        val newRate = getRateInSyp(newCurrency)
        
        val currentItems = tempInvoiceItems.value.map { item ->
            val priceInSyp = item.price * oldRate
            val costInSyp = item.cost * oldRate
            
            val priceInNew = if (newRate != 0.0) priceInSyp / newRate else priceInSyp
            val costInNew = if (newRate != 0.0) costInSyp / newRate else costInSyp
            
            item.copy(price = priceInNew, cost = costInNew)
        }
        tempInvoiceItems.value = currentItems
    }

    fun addProductToInvoiceForm(product: Product) {
        val currentItems = tempInvoiceItems.value.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.name == product.name }
        
        val rate = getRateInSyp(invoiceCurrency.value)
        val rawPrice = if (invoiceType.value == "purchase" || invoiceType.value == "return_purchase") {
            product.buyPrice
        } else {
            product.sellPrice
        }
        val convertedPrice = if (rate != 0.0) rawPrice / rate else rawPrice
        val convertedCost = if (rate != 0.0) product.buyPrice / rate else product.buyPrice

        if (existingIndex >= 0) {
            val oldItem = currentItems[existingIndex]
            currentItems[existingIndex] = oldItem.copy(qty = oldItem.qty + 1)
        } else {
            currentItems.add(
                InvoiceItem(
                    name = product.name,
                    qty = 1,
                    price = convertedPrice,
                    cost = convertedCost
                )
            )
        }
        tempInvoiceItems.value = currentItems
    }

    fun updateInvoiceItemFormQty(index: Int, delta: Int) {
        val currentItems = tempInvoiceItems.value.toMutableList()
        if (index in currentItems.indices) {
            val item = currentItems[index]
            val newQty = maxOf(1, item.qty + delta)
            currentItems[index] = item.copy(qty = newQty)
            tempInvoiceItems.value = currentItems
        }
    }

    fun setInvoiceItemFormQty(index: Int, qty: Int) {
        val currentItems = tempInvoiceItems.value.toMutableList()
        if (index in currentItems.indices) {
            val item = currentItems[index]
            val newQty = maxOf(1, qty)
            currentItems[index] = item.copy(qty = newQty)
            tempInvoiceItems.value = currentItems
        }
    }

    fun setInvoiceItemFormPrice(index: Int, price: Double) {
        val currentItems = tempInvoiceItems.value.toMutableList()
        if (index in currentItems.indices) {
            val item = currentItems[index]
            val newPrice = maxOf(0.0, price)
            currentItems[index] = item.copy(price = newPrice)
            tempInvoiceItems.value = currentItems
        }
    }

    fun removeInvoiceItemForm(index: Int) {
        val currentItems = tempInvoiceItems.value.toMutableList()
        if (index in currentItems.indices) {
            currentItems.removeAt(index)
            tempInvoiceItems.value = currentItems
        }
    }

    // --- Calculator Engine ---
    fun clickCalcBtn(char: String) {
        val currentExpr = calcExpression.value
        when (char) {
            "C" -> {
                calcExpression.value = ""
                calcResultDisplay.value = "0"
            }
            "=" -> {
                if (currentExpr.isNotBlank()) {
                    try {
                        val expressionToEvaluate = currentExpr
                            .replace("×", "*")
                            .replace("÷", "/")
                            .replace("−", "-")
                        val result = evaluateSimpleExpression(expressionToEvaluate)
                        calcResultDisplay.value = formatCurrency(result)
                        calcExpression.value = result.toString()
                    } catch (e: Exception) {
                        calcResultDisplay.value = "خطأ"
                    }
                }
            }
            "±" -> {
                val resVal = calcResultDisplay.value.replace(Regex("[^0-9.-]"), "").toDoubleOrNull() ?: 0.0
                val inverted = -resVal
                calcResultDisplay.value = formatCurrency(inverted)
                calcExpression.value = inverted.toString()
            }
            "%" -> {
                val resVal = calcResultDisplay.value.replace(Regex("[^0-9.-]"), "").toDoubleOrNull() ?: 0.0
                val percentage = resVal / 100.0
                calcResultDisplay.value = formatCurrency(percentage)
                calcExpression.value = percentage.toString()
            }
            else -> {
                val isOperator = char in listOf("+", "−", "×", "÷")
                if (isOperator) {
                    calcExpression.value = currentExpr + char
                } else {
                    calcExpression.value = currentExpr + char
                    // Quickly evaluate intermediate results
                    try {
                        val toEval = calcExpression.value
                            .replace("×", "*")
                            .replace("÷", "/")
                            .replace("−", "-")
                        val intermediateVal = evaluateSimpleExpression(toEval)
                        calcResultDisplay.value = formatCurrency(intermediateVal)
                    } catch (e: Exception) {
                        // Keep typing
                    }
                }
            }
        }
    }

    private fun evaluateSimpleExpression(expr: String): Double {
        // Safe lightweight arithmetic evaluator using standard split-parse
        val tokens = expr.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
        if (tokens.isEmpty()) return 0.0
        try {
            var result = tokens[0].trim().toDoubleOrNull() ?: 0.0
            var i = 1
            while (i < tokens.size - 1) {
                val op = tokens[i].trim()
                val nextVal = tokens[i + 1].trim().toDoubleOrNull() ?: 0.0
                result = when (op) {
                    "+" -> result + nextVal
                    "-" -> result - nextVal
                    "*" -> result * nextVal
                    "/" -> if (nextVal != 0.0) result / nextVal else 0.0
                    else -> result
                }
                i += 2
            }
            return result
        } catch (e: Exception) {
            return 0.0
        }
    }

    // --- Invoice Item Serialization Helpers ---
    private fun serializeItems(items: List<InvoiceItem>): String {
        return items.joinToString("##") { "${it.name}||${it.qty}||${it.price}||${it.cost}" }
    }

    fun deserializeItems(value: String): List<InvoiceItem> {
        if (value.isBlank()) return emptyList()
        return try {
            value.split("##").filter { it.isNotBlank() }.map { itemStr ->
                val parts = itemStr.split("||")
                InvoiceItem(
                    name = parts[0],
                    qty = parts[1].toInt(),
                    price = parts[2].toDouble(),
                    cost = parts[3].toDouble()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Google Drive Backup & Restore Integrations ---
    private val _googleAccountEmail = MutableStateFlow<String?>(null)
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _isGoogleDriveLinked = MutableStateFlow(false)
    val isGoogleDriveLinked: StateFlow<Boolean> = _isGoogleDriveLinked.asStateFlow()

    private val _cloudBackups = MutableStateFlow<List<GoogleDriveHelper.CloudBackupItem>>(emptyList())
    val cloudBackups: StateFlow<List<GoogleDriveHelper.CloudBackupItem>> = _cloudBackups.asStateFlow()

    private val _isLoadingCloudBackups = MutableStateFlow(false)
    val isLoadingCloudBackups: StateFlow<Boolean> = _isLoadingCloudBackups.asStateFlow()

    private val _isSyncingToCloud = MutableStateFlow(false)
    val isSyncingToCloud: StateFlow<Boolean> = _isSyncingToCloud.asStateFlow()

    private val _googleAuthIntentToResolve = MutableStateFlow<android.content.Intent?>(null)
    val googleAuthIntentToResolve: StateFlow<android.content.Intent?> = _googleAuthIntentToResolve.asStateFlow()

    val googleDriveAuthCallback = object : GoogleDriveHelper.AuthCallback {
        override fun onAuthRequired(intent: android.content.Intent) {
            _googleAuthIntentToResolve.value = intent
        }
        override fun onError(message: String) {
            triggerToast(message)
        }
    }

    fun clearGoogleAuthIntent() {
        _googleAuthIntentToResolve.value = null
    }

    fun initializeGoogleDrive() {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(app)
                if (account != null && account.email != null) {
                    _googleAccountEmail.value = account.email
                    _isGoogleDriveLinked.value = true
                    refreshCloudBackups()
                } else {
                    _googleAccountEmail.value = null
                    _isGoogleDriveLinked.value = false
                    _cloudBackups.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun linkGoogleAccount(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        viewModelScope.launch {
            if (account.email != null) {
                _googleAccountEmail.value = account.email
                _isGoogleDriveLinked.value = true
                triggerToast("تم ربط حسابك ${account.email} بنجاح!")
                refreshCloudBackups()
            }
        }
    }

    fun unlinkGoogleAccount() {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                ).build()
                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(app, gso)
                client.signOut().addOnCompleteListener {
                    _googleAccountEmail.value = null
                    _isGoogleDriveLinked.value = false
                    _cloudBackups.value = emptyList()
                    triggerToast("تم تسجيل الخروج وفصل حساب Google Drive بنجاح.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _googleAccountEmail.value = null
                _isGoogleDriveLinked.value = false
                _cloudBackups.value = emptyList()
            }
        }
    }

    fun refreshCloudBackups() {
        val email = _googleAccountEmail.value ?: return
        viewModelScope.launch {
            _isLoadingCloudBackups.value = true
            try {
                val list = GoogleDriveHelper.listBackups(
                    context = getApplication(),
                    accountEmail = email,
                    callback = googleDriveAuthCallback
                )
                _cloudBackups.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingCloudBackups.value = false
            }
        }
    }

    fun backupCurrentDbToCloud() {
        val email = _googleAccountEmail.value
        if (email == null) {
            triggerToast("برجاء ربط حساب Google Drive أولاً للحفظ السحابي.")
            return
        }

        viewModelScope.launch {
            _isSyncingToCloud.value = true
            _isLoadingCloudBackups.value = true
            try {
                val app = getApplication<Application>()
                val currentCompanyId = _activeCompanyId.value
                val currentCompanyName = _companyName.value

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val timestamp = sdf.format(java.util.Date())

                val dbFileName = "company_$currentCompanyId.db"
                val dbFile = app.getDatabasePath(dbFileName)

                if (!dbFile.exists()) {
                    triggerToast("لا يوجد قاعدة بيانات حالية لنسخها سحابياً.")
                    return@launch
                }

                val cleanCompanyName = currentCompanyName.replace("/", "_").replace("\\", "_").replace(" ", "_")
                val backupFileName = "Backup_${cleanCompanyName}_${currentCompanyId}_${timestamp}.db"

                val success = GoogleDriveHelper.uploadFile(
                    context = app,
                    accountEmail = email,
                    file = dbFile,
                    remoteName = backupFileName,
                    callback = googleDriveAuthCallback
                )

                if (success) {
                    triggerToast("تم رفع النسخة الاحتياطية بنجاح إلى حسابك المتصل على Google Drive ☁️")
                    refreshCloudBackups()
                } else {
                    triggerToast("فشل رفع النسخة الاحتياطية سحابياً")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                triggerToast("خطأ أثناء الرفع السحابي: ${e.message}")
            } finally {
                _isSyncingToCloud.value = false
                _isLoadingCloudBackups.value = false
            }
        }
    }

    fun restoreDbFromCloud(cloudItem: GoogleDriveHelper.CloudBackupItem) {
        val email = _googleAccountEmail.value ?: return
        viewModelScope.launch {
            _isSyncingToCloud.value = true
            try {
                val app = getApplication<Application>()
                
                // We download the cloud file temporarily, then restore it using standard restoreDatabase
                val tempDir = app.cacheDir
                val tempFile = java.io.File(tempDir, cloudItem.name)
                
                val success = GoogleDriveHelper.downloadFile(
                    context = app,
                    accountEmail = email,
                    fileId = cloudItem.id,
                    destFile = tempFile,
                    callback = googleDriveAuthCallback
                )

                if (success && tempFile.exists()) {
                    // Call our local restore method!
                    val restoreSuccess = restoreDatabase(tempFile)
                    tempFile.delete() // clean up
                    if (restoreSuccess) {
                        triggerToast("تم تنزيل واستعادة النسخة الاحتياطية السحابية بنجاح 🔄")
                    }
                } else {
                    triggerToast("فشل تنزيل ملف النسخة الاحتياطية من Google Drive")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                triggerToast("خطأ أثناء الاستعادة السحابية: ${e.message}")
            } finally {
                _isSyncingToCloud.value = false
            }
        }
    }

    fun deleteCloudBackup(cloudItem: GoogleDriveHelper.CloudBackupItem) {
        val email = _googleAccountEmail.value ?: return
        viewModelScope.launch {
            _isLoadingCloudBackups.value = true
            try {
                val success = GoogleDriveHelper.deleteFile(
                    context = getApplication(),
                    accountEmail = email,
                    fileId = cloudItem.id,
                    callback = googleDriveAuthCallback
                )
                if (success) {
                    triggerToast("تم حذف النسخة الاحتياطية السحابية نهائياً بنجاح 🗑️")
                    refreshCloudBackups()
                } else {
                    triggerToast("فشل حذف النسخة الاحتياطية السحابية")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                triggerToast("خطأ أثناء الحذف السحابي: ${e.message}")
            } finally {
                _isLoadingCloudBackups.value = false
            }
        }
    }

    // --- Backup & Restore Infrastructure ---

    data class BackupItem(
        val file: java.io.File,
        val companyName: String,
        val companyId: String,
        val dateDisplay: String,
        val timestamp: String
    )

    private val _availableBackups = MutableStateFlow<List<BackupItem>>(emptyList())
    val availableBackups: StateFlow<List<BackupItem>> = _availableBackups.asStateFlow()

    fun refreshBackupList() {
        try {
            val app = getApplication<Application>()
            val internalBackupDir = app.getExternalFilesDir("Backups") ?: java.io.File(app.filesDir, "Backups")
            val filesList = mutableListOf<BackupItem>()

            // Read from internal app directory (always safe & permission-free)
            if (internalBackupDir.exists()) {
                internalBackupDir.listFiles { _, name -> name.startsWith("Backup_") && name.endsWith(".db") }?.forEach { file ->
                    val item = parseBackupFile(file)
                    if (item != null) filesList.add(item)
                }
            }

            // Read from public directory downloads/SmartAccountant_Backups
            try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val externalBackupDir = java.io.File(downloadDir, "SmartAccountant_Backups")
                if (externalBackupDir.exists()) {
                    externalBackupDir.listFiles { _, name -> name.startsWith("Backup_") && name.endsWith(".db") }?.forEach { file ->
                        val item = parseBackupFile(file)
                        if (item != null && !filesList.any { it.file.name == file.name }) {
                            filesList.add(item)
                        }
                    }
                }
            } catch (ex: Exception) {
                // Ignore download reading restrictions if blocked by Android OS
            }

            // Sort newest first
            filesList.sortByDescending { it.timestamp }
            _availableBackups.value = filesList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseBackupFile(file: java.io.File): BackupItem? {
        return try {
            val fileName = file.name
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val parts = nameWithoutExt.split("_")
            if (parts.size < 4) return null

            var dateIndex = -1
            for (i in parts.indices) {
                if (parts[i].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    dateIndex = i
                    break
                }
            }
            if (dateIndex == -1 || dateIndex < 2) return null

            val companyId = parts[dateIndex - 1]
            val companyNameParsed = parts.subList(1, dateIndex - 1).joinToString(" ").replace("_", " ")
            val datePart = parts[dateIndex]
            val timePart = if (dateIndex + 1 < parts.size) parts[dateIndex + 1] else ""

            val displayTime = if (timePart.isNotBlank()) {
                val subTime = timePart.replace("-", ":")
                "$datePart | $subTime"
            } else {
                datePart
            }

            val rawTimestamp = "${datePart}_${timePart}"
            BackupItem(file, companyNameParsed, companyId, displayTime, rawTimestamp)
        } catch (e: Exception) {
            null
        }
    }

    fun backupCurrentDatabase(): Boolean {
        return try {
            val app = getApplication<Application>()
            val currentCompanyId = _activeCompanyId.value
            val currentCompanyName = _companyName.value

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val timestamp = sdf.format(java.util.Date())

            val dbFileName = "company_$currentCompanyId.db"
            val dbFile = app.getDatabasePath(dbFileName)

            if (!dbFile.exists()) {
                triggerToast("لا يوجد ملف قاعدة بيانات حالي للنسخ في الوقت الحالي.")
                return false
            }

            val internalBackupDir = app.getExternalFilesDir("Backups") ?: java.io.File(app.filesDir, "Backups")
            if (!internalBackupDir.exists()) internalBackupDir.mkdirs()

            val cleanCompanyName = currentCompanyName.replace("/", "_").replace("\\", "_").replace(" ", "_")
            val backupFileNamePattern = "Backup_${cleanCompanyName}_${currentCompanyId}_${timestamp}"

            // Copy to Internal Sandboxed storage
            val destDbFileInternal = java.io.File(internalBackupDir, "$backupFileNamePattern.db")
            dbFile.copyTo(destDbFileInternal, overwrite = true)

            val walFile = java.io.File(dbFile.parent, "$dbFileName-wal")
            if (walFile.exists()) {
                val destWalFileInternal = java.io.File(internalBackupDir, "$backupFileNamePattern.db-wal")
                walFile.copyTo(destWalFileInternal, overwrite = true)
            }
            val shmFile = java.io.File(dbFile.parent, "$dbFileName-shm")
            if (shmFile.exists()) {
                val destShmFileInternal = java.io.File(internalBackupDir, "$backupFileNamePattern.db-shm")
                shmFile.copyTo(destShmFileInternal, overwrite = true)
            }

            // Copy to Shared Download folder for absolute visibility to the user under "SmartAccountant_Backups"
            try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val externalBackupDir = java.io.File(downloadDir, "SmartAccountant_Backups")
                if (!externalBackupDir.exists()) externalBackupDir.mkdirs()

                val destDbFileExternal = java.io.File(externalBackupDir, "$backupFileNamePattern.db")
                dbFile.copyTo(destDbFileExternal, overwrite = true)

                if (walFile.exists()) {
                    val destWalFileExternal = java.io.File(externalBackupDir, "$backupFileNamePattern.db-wal")
                    walFile.copyTo(destWalFileExternal, overwrite = true)
                }
                if (shmFile.exists()) {
                    val destShmFileExternal = java.io.File(externalBackupDir, "$backupFileNamePattern.db-shm")
                    shmFile.copyTo(destShmFileExternal, overwrite = true)
                }
            } catch (ex: Exception) {
                // Secondary directory file copy skipped if forbidden by partition Scoped Storage rules
            }

            refreshBackupList()
            triggerToast("تم إنشاء نسخة احتياطية محلية باسم: $backupFileNamePattern في مجلد التطبيق.")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            triggerToast("خطأ أثناء إنشاء النسخة الاحتياطية: ${e.message}")
            false
        }
    }

    fun restoreDatabase(backupFile: java.io.File): Boolean {
        return try {
            val app = getApplication<Application>()
            val fileName = backupFile.name
            if (!fileName.startsWith("Backup_") || !fileName.endsWith(".db")) {
                triggerToast("اسم ملف النسخة الاحتياطية غير صالح!")
                return false
            }

            val nameWithoutExt = fileName.substringBeforeLast(".")
            val parts = nameWithoutExt.split("_")
            if (parts.size < 4) {
                triggerToast("اسم ملف النسخة غير مطابق للنموذج المعتمد!")
                return false
            }

            var dateIndex = -1
            for (i in parts.indices) {
                if (parts[i].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    dateIndex = i
                    break
                }
            }

            if (dateIndex == -1 || dateIndex < 2) {
                triggerToast("صيغة تاريخ النسخة الاحتياطية غير مدعومة!")
                return false
            }

            val companyId = parts[dateIndex - 1]
            val companyNameParsed = parts.subList(1, dateIndex - 1).joinToString(" ").replace("_", " ")

            val dbFileName = "company_$companyId.db"
            val targetDbFile = app.getDatabasePath(dbFileName)

            // 1. Close database gracefully
            AppDatabase.closeAndRemoveDatabase("company_$companyId")

            // 2. Clear old transactional log files
            val targetWalFile = java.io.File(targetDbFile.parent, "$dbFileName-wal")
            if (targetWalFile.exists()) targetWalFile.delete()
            val targetShmFile = java.io.File(targetDbFile.parent, "$dbFileName-shm")
            if (targetShmFile.exists()) targetShmFile.delete()

            // 3. Copy DB and WAL/SHM backups
            backupFile.copyTo(targetDbFile, overwrite = true)

            val backupParentDir = backupFile.parentFile
            val backupWal = java.io.File(backupParentDir, "$nameWithoutExt.db-wal")
            if (backupWal.exists()) {
                val destWal = java.io.File(targetDbFile.parent, "$dbFileName-wal")
                backupWal.copyTo(destWal, overwrite = true)
            }

            val backupShm = java.io.File(backupParentDir, "$nameWithoutExt.db-shm")
            if (backupShm.exists()) {
                val destShm = java.io.File(targetDbFile.parent, "$dbFileName-shm")
                backupShm.copyTo(destShm, overwrite = true)
            }

            // 4. Ensure company exists in preferences metadata
            val list = _companiesList.value.toMutableList()
            if (!list.any { it.id == companyId }) {
                val newComp = CompanyInfo(companyId, companyNameParsed, "", "", "ل.س")
                list.add(newComp)
                _companiesList.value = list
                val savedStr = list.joinToString(";;") { serializeCompany(it) }
                prefs.edit().putString("companies_list", savedStr).apply()
            }

            // 5. Instantly switch to the restored company DB
            switchCompanyDb(companyId, companyNameParsed)

            triggerToast("تمت استعادة النسخة الاحتياطية بنجاح للشركة: $companyNameParsed")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            triggerToast("فشلت استعادة النسخة: ${e.message}")
            false
        }
    }

    fun deleteBackup(backupItem: BackupItem): Boolean {
        return try {
            val file = backupItem.file
            val parent = file.parentFile
            val nameNoExt = file.name.substringBeforeLast(".")

            if (file.exists()) file.delete()

            val wal = java.io.File(parent, "$nameNoExt.db-wal")
            if (wal.exists()) wal.delete()
            val shm = java.io.File(parent, "$nameNoExt.db-shm")
            if (shm.exists()) shm.delete()

            // Delete mirror too
            try {
                val app = getApplication<Application>()
                val internalBackupDir = app.getExternalFilesDir("Backups") ?: java.io.File(app.filesDir, "Backups")
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val externalBackupDir = java.io.File(downloadDir, "SmartAccountant_Backups")

                val peerDir = if (parent.absolutePath.contains("Backups")) externalBackupDir else internalBackupDir
                val peerFile = java.io.File(peerDir, file.name)
                if (peerFile.exists()) peerFile.delete()

                val peerWal = java.io.File(peerDir, "$nameNoExt.db-wal")
                if (peerWal.exists()) peerWal.delete()
                val peerShm = java.io.File(peerDir, "$nameNoExt.db-shm")
                if (peerShm.exists()) peerShm.delete()
            } catch (ex: Exception) {}

            refreshBackupList()
            triggerToast("تم حذف النسخة الاحتياطية بنجاح.")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            triggerToast("فشل حذف الملف: ${e.message}")
            false
        }
    }
}
