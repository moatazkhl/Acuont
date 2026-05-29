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

    private var activeCollectionJob: kotlinx.coroutines.Job? = null

    init {
        loadCompaniesFromPrefs()
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

    // --- Active Document Creation States ---
    // New Invoice Form State
    val tempInvoiceItems = MutableStateFlow<List<InvoiceItem>>(emptyList())
    val selectedInvoiceCustomer = MutableStateFlow<Account?>(null)
    val selectedInvoiceDate = MutableStateFlow("")
    val invoiceNotes = MutableStateFlow("")
    val invoiceCurrency = MutableStateFlow("ل.س")
    val invoiceType = MutableStateFlow("sale")

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

        val total = items.sumOf { it.qty * it.price }
        val profit = items.sumOf { it.qty * (it.price - it.cost) }
        val dateVal = selectedInvoiceDate.value.ifBlank { "2026-05-23" }

        val invStr = serializeItems(items)

        val newInvoice = Invoice(
            id = "INV-" + System.currentTimeMillis().toString().takeLast(5),
            type = invoiceType.value,
            customer = cust.name,
            date = dateVal,
            total = total,
            profit = profit,
            status = status,
            notes = invoiceNotes.value,
            itemsJson = invStr,
            currency = invoiceCurrency.value
        )

        viewModelScope.launch {
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

            tempInvoiceItems.value = emptyList()
            selectedInvoiceCustomer.value = null
            invoiceNotes.value = ""
            invoiceCurrency.value = "ل.س"
            invoiceType.value = "sale"
            triggerToast(if (status == "draft") "تم حفظ المسودة" else "تمت الفاتورة والطباعة ✓")
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

        val dateVal = voucherDate.value.ifBlank { "2026-05-23" }

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
                val accList = accounts.value
                val acc = accList.find { it.name == invoice.customer }
                if (acc != null) {
                    val rawReversedDiff = when (invoice.type) {
                        "sale" -> invoice.total
                        "purchase" -> -invoice.total
                        "return", "return_sale" -> -invoice.total
                        "return_purchase" -> invoice.total
                        else -> 0.0
                    }
                    val rateFrom = getRateInSyp(invoice.currency)
                    val diffInSyp = rawReversedDiff * rateFrom
                    val rateTo = getRateInSyp(acc.currency)
                    val reversedDiff = if (rateTo != 0.0) diffInSyp / rateTo else diffInSyp
                    repository.updateAccountBalance(acc.id, acc.balance + reversedDiff)
                }
            }
            triggerToast("تم حذف الفاتورة")
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
}
