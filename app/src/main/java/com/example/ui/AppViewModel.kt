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

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(db.appDao())

    init {
        // Run database seating on startup
        viewModelScope.launch {
            repository.prepopulateIfNeeded()
        }
    }

    // --- Core States ---
    val invoices: StateFlow<List<Invoice>> = repository.invoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<Account>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = repository.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vouchers: StateFlow<List<Voucher>> = repository.vouchers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _companyName = MutableStateFlow("شركة النور للتجارة")
    val companyName: StateFlow<String> = _companyName.asStateFlow()

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

    // New Product Form State
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

    // New Account Form State
    val newAccountName = MutableStateFlow("")
    val newAccountType = MutableStateFlow("customer")
    val newAccountBalance = MutableStateFlow("")
    val newAccountPhone = MutableStateFlow("")
    val newAccountAddress = MutableStateFlow("")
    val newAccountNotes = MutableStateFlow("")

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
        if (name.isNotBlank()) {
            _companyName.value = name
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

        val newAcc = Account(
            id = "A" + System.currentTimeMillis().toString().takeLast(6),
            name = name,
            type = newAccountType.value,
            balance = bal,
            phone = newAccountPhone.value,
            address = newAccountAddress.value,
            notes = newAccountNotes.value,
            color = randomColor
        )

        viewModelScope.launch {
            repository.insertAccount(newAcc)
            // Empty the form
            newAccountName.value = ""
            newAccountBalance.value = ""
            newAccountPhone.value = ""
            newAccountAddress.value = ""
            newAccountNotes.value = ""
            triggerToast("تم إضافة الحساب بنجاح ✓")
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
        val bar = newProductBarcode.value.ifBlank { System.currentTimeMillis().toString().takeLast(13) }

        val newProd = Product(
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

        viewModelScope.launch {
            repository.insertProduct(newProd)
            // Clean fields
            newProductName.value = ""
            newProductCode.value = ""
            newProductQty.value = ""
            newProductMinQty.value = ""
            newProductBuyPrice.value = ""
            newProductSellPrice.value = ""
            newProductBarcode.value = ""
            newProductIcon.value = "📦"
            triggerToast("تم إضافة المادة بنجاح ✓")
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
            type = "sale",
            customer = cust.name,
            date = dateVal,
            total = total,
            profit = profit,
            status = status,
            notes = invoiceNotes.value,
            itemsJson = invStr
        )

        viewModelScope.launch {
            repository.insertInvoice(newInvoice)
            // Deduct from product quantities
            products.value.forEach { product ->
                val matchedItem = items.find { it.name == product.name }
                if (matchedItem != null) {
                    val remainingStock = maxOf(0, product.qty - matchedItem.qty)
                    repository.updateProductQuantity(product.id, remainingStock)
                }
            }

            tempInvoiceItems.value = emptyList()
            selectedInvoiceCustomer.value = null
            invoiceNotes.value = ""
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
                        repository.updateProductQuantity(product.id, product.qty + matchedItem.qty)
                    }
                }
                // Reverse account balance adjustment
                val accList = accounts.value
                val acc = accList.find { it.name == invoice.customer }
                if (acc != null) {
                    val reversedDiff = when (invoice.type) {
                        "sale" -> invoice.total
                        "purchase" -> -invoice.total
                        "return" -> -invoice.total
                        else -> 0.0
                    }
                    repository.updateAccountBalance(acc.id, acc.balance + reversedDiff)
                }
            }
            triggerToast("تم حذف الفاتورة")
        }
    }

    // --- Invoice Item Management ---
    fun addProductToInvoiceForm(product: Product) {
        val currentItems = tempInvoiceItems.value.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.name == product.name }
        if (existingIndex >= 0) {
            val oldItem = currentItems[existingIndex]
            currentItems[existingIndex] = oldItem.copy(qty = oldItem.qty + 1)
        } else {
            currentItems.add(
                InvoiceItem(
                    name = product.name,
                    qty = 1,
                    price = product.sellPrice,
                    cost = product.buyPrice
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
