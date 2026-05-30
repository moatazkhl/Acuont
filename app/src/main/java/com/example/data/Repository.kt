package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val appDao: AppDao) {

    val invoices: Flow<List<Invoice>> = appDao.getAllInvoices()
    val accounts: Flow<List<Account>> = appDao.getAllAccounts()
    val products: Flow<List<Product>> = appDao.getAllProducts()
    val vouchers: Flow<List<Voucher>> = appDao.getAllVouchers()
    val categories: Flow<List<ProductCategory>> = appDao.getAllCategories()

    suspend fun insertCategory(category: ProductCategory) = withContext(Dispatchers.IO) {
        appDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: ProductCategory) = withContext(Dispatchers.IO) {
        appDao.deleteCategory(category)
    }

    suspend fun insertInvoiceRaw(invoice: Invoice) = withContext(Dispatchers.IO) {
        appDao.insertInvoice(invoice)
    }

    suspend fun insertInvoice(
        invoice: Invoice, 
        usdRate: Double = 13700.0, 
        eurRate: Double = 14900.0, 
        sarRate: Double = 3650.0, 
        tryRate: Double = 380.0
    ) = withContext(Dispatchers.IO) {
        appDao.insertInvoice(invoice)
        // Also update the account balance reactively based on invoice type
        val accountsList = appDao.getAllAccounts().first()
        val account = accountsList.find { it.name == invoice.customer }
        if (account != null) {
            val balanceDiff = when (invoice.type) {
                "sale" -> -invoice.total // Debit customer (they owe us more)
                "purchase" -> invoice.total // Credit supplier (we owe them more)
                "return", "return_sale" -> invoice.total // Refund/return sale
                "return_purchase" -> -invoice.total // Refund/return purchase
                else -> 0.0
            }
            val rateFrom = when (invoice.currency) {
                "USD" -> usdRate
                "EUR" -> eurRate
                "SAR" -> sarRate
                "TRY" -> tryRate
                else -> 1.0
            }
            val diffInSyp = balanceDiff * rateFrom

            val rateTo = when (account.currency) {
                "USD" -> usdRate
                "EUR" -> eurRate
                "SAR" -> sarRate
                "TRY" -> tryRate
                else -> 1.0
            }
            val convertedDiff = if (rateTo != 0.0) diffInSyp / rateTo else diffInSyp
            appDao.updateAccountBalance(account.id, account.balance + convertedDiff)
        }
    }

    suspend fun deleteInvoice(invoice: Invoice) = withContext(Dispatchers.IO) {
        appDao.deleteInvoice(invoice)
    }

    suspend fun deleteInvoiceById(id: String) = withContext(Dispatchers.IO) {
        appDao.deleteInvoiceById(id)
    }

    suspend fun insertAccount(account: Account) = withContext(Dispatchers.IO) {
        appDao.insertAccount(account)
    }

    suspend fun deleteAccount(account: Account) = withContext(Dispatchers.IO) {
        appDao.deleteAccount(account)
    }

    suspend fun updateAccountBalance(id: String, newBalance: Double) = withContext(Dispatchers.IO) {
        appDao.updateAccountBalance(id, newBalance)
    }

    suspend fun insertProduct(product: Product) = withContext(Dispatchers.IO) {
        appDao.insertProduct(product)
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        appDao.deleteProduct(product)
    }

    suspend fun updateProductQuantity(id: String, newQty: Int) = withContext(Dispatchers.IO) {
        appDao.updateProductQuantity(id, newQty)
    }

    suspend fun insertVoucher(voucher: Voucher) = withContext(Dispatchers.IO) {
        appDao.insertVoucher(voucher)
        // Adjust the account's balance
        val accountsList = appDao.getAllAccounts().first()
        val account = accountsList.find { it.id == voucher.accountId }
        if (account != null) {
            val balanceDiff = when (voucher.type) {
                "receipt" -> voucher.amount  // Received cash: offsets customer debit
                "payment" -> -voucher.amount // Paid cash: offsets supplier credit
                else -> 0.0
            }
            appDao.updateAccountBalance(account.id, account.balance + balanceDiff)
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        appDao.deleteAllInvoices()
        appDao.deleteAllAccounts()
        appDao.deleteAllProducts()
        appDao.deleteAllVouchers()
        appDao.deleteAllCategories()
    }

    suspend fun loadCustomSeedData() = withContext(Dispatchers.IO) {
        // Force-seed even if data exists
        appDao.deleteAllCategories()
        val seedCategories = listOf(
            ProductCategory("other", "أخرى", "📦"),
            ProductCategory("food", "غذاء", "🍬"),
            ProductCategory("electronics", "إلكترونيات", "🔌")
        )
        seedCategories.forEach { appDao.insertCategory(it) }

        val seedAccounts = listOf(
            Account("A001", "أبو محمد التاجر", "customer", 250000.0, "0999111222", "دمشق - الميدان", "عميل قديم وممتاز", "#4a7fa5"),
            Account("A002", "شركة الفرات للتجارة", "customer", -180000.0, "0988333444", "حلب - السليمانية", "شركة توزيع رئيسية", "#2ebd7a"),
            Account("A003", "مورد السمرة للمواد الغذائية", "supplier", 650000.0, "0977555666", "حمص - باب سباع", "مورد السكر والرز الأساسي", "#e03c3c"),
            Account("A004", "مطعم الغدير السياحي", "customer", 0.0, "0966777888", "اللاذقية - الكورنيش", "مبيعات يومية نقداً", "#f5a623"),
            Account("A005", "إيجار المحل والمستودع", "expense", 0.0, "", "دمشق", "مصاريف نصف سنوية", "#9b59b6"),
            Account("A006", "رواتب موظفين وأجور شحن", "expense", 0.0, "", "دمشق", "مصاريف تشغيلية شهرية", "#e67e22")
        )
        seedAccounts.forEach { appDao.insertAccount(it) }

        val seedProducts = listOf(
            Product("P001", "سكر أبيض ناعم", "P-001", "food", "كيلو", 450, 50, 18000.0, 25000.0, "6281234567890", "🍬"),
            Product("P002", "زيت نباتي سولي", "P-002", "food", "لتر", 120, 20, 30000.0, 40000.0, "6281234567891", "🫙"),
            Product("P003", "أرز بسمتي هندي", "P-003", "food", "كيلو", 85, 30, 12000.0, 18000.0, "6281234567892", "🍚"),
            Product("P004", "مصباح LED موفر 12W", "P-004", "electronics", "قطعة", 85, 10, 45000.0, 65000.0, "6281234567893", "💡"),
            Product("P005", "كابل شحن سريع Type-C", "P-005", "electronics", "قطعة", 35, 15, 25000.0, 45000.0, "6281234567894", "🔌"),
            Product("P006", "طحين قمح بلدي", "P-006", "food", "كيلو", 600, 100, 9000.0, 13000.0, "6281234567895", "🌾")
        )
        seedProducts.forEach { appDao.insertProduct(it) }

        val seedVouchers = listOf(
            Voucher(1, "receipt", "A001", 150000.0, "دفعة نقدية على الحساب سنوية", "2026-05-22"),
            Voucher(2, "payment", "A003", 400000.0, "تسديد دفعة للمورد شحنة السكر", "2026-05-21"),
            Voucher(3, "receipt", "A002", 80000.0, "تسديد ديون فواتير مستحقة", "2026-05-20")
        )
        seedVouchers.forEach { appDao.insertVoucher(it) }

        val seedInvoices = listOf(
            Invoice(
                "INV-001",
                "sale",
                "أبو محمد التاجر",
                "2026-05-23",
                450000.0,
                85000.0,
                "saved",
                "توصيل للمحل باليد",
                """[{"name":"سكر أبيض ناعم","qty":10,"price":25000.0,"cost":18000.0},{"name":"زيت نباتي سولي","qty":5,"price":40000.0,"cost":30000.0}]"""
            ),
            Invoice(
                "INV-002",
                "sale",
                "شركة الفرات للتجارة",
                "2026-05-23",
                1250000.0,
                220000.0,
                "saved",
                "شحن شركة الفرات",
                """[{"name":"أرز بسمتي هندي","qty":50,"price":18000.0,"cost":12000.0},{"name":"سكر أبيض ناعم","qty":20,"price":25000.0,"cost":18000.0}]"""
            ),
            Invoice(
                "INV-003",
                "purchase",
                "مورد السمرة للمواد الغذائية",
                "2026-05-22",
                800000.0,
                0.0,
                "saved",
                "مشتريات طحين وسكر",
                "[]"
            ),
            Invoice(
                "INV-004",
                "sale",
                "مطعم الغدير السياحي",
                "2026-05-22",
                320000.0,
                60000.0,
                "saved",
                "تسليم فوري للمطبخ",
                "[]"
            ),
            Invoice(
                "INV-005",
                "return",
                "أبو محمد التاجر",
                "2026-05-21",
                75000.0,
                -12000.0,
                "saved",
                "مرتجع سكر غير مطابق",
                "[]"
            ),
            Invoice(
                "INV-006",
                "sale",
                "مطعم الغدير السياحي",
                "2026-05-21",
                180000.0,
                35000.0,
                "draft",
                "مسودة مراجعة لاحقاً",
                "[]"
            )
        )
        seedInvoices.forEach { appDao.insertInvoice(it) }
    }

    suspend fun prepopulateIfNeeded() = withContext(Dispatchers.IO) {
        val existingCats = appDao.getAllCategories().first()
        if (existingCats.isEmpty()) {
            val seedCategories = listOf(
                ProductCategory("other", "أخرى", "📦"),
                ProductCategory("food", "غذاء", "🍬"),
                ProductCategory("electronics", "إلكترونيات", "🔌")
            )
            seedCategories.forEach { appDao.insertCategory(it) }
        }

        val existingAccounts = appDao.getAllAccounts().first()
        if (existingAccounts.isEmpty()) {
            // Seed Accounts
            val seedAccounts = listOf(
                Account("A001", "أبو محمد التاجر", "customer", 250000.0, "0999111222", "دمشق - الميدان", "عميل قديم وممتاز", "#4a7fa5"),
                Account("A002", "شركة الفرات للتجارة", "customer", -180000.0, "0988333444", "حلب - السليمانية", "شركة توزيع رئيسية", "#2ebd7a"),
                Account("A003", "مورد السمرة للمواد الغذائية", "supplier", 650000.0, "0977555666", "حمص - باب سباع", "مورد السكر والرز الأساسي", "#e03c3c"),
                Account("A004", "مطعم الغدير السياحي", "customer", 0.0, "0966777888", "اللاذقية - الكورنيش", "مبيعات يومية نقداً", "#f5a623"),
                Account("A005", "إيجار المحل والمستودع", "expense", 0.0, "", "دمشق", "مصاريف نصف سنوية", "#9b59b6"),
                Account("A006", "رواتب موظفين وأجور شحن", "expense", 0.0, "", "دمشق", "مصاريف تشغيلية شهرية", "#e67e22")
            )
            seedAccounts.forEach { appDao.insertAccount(it) }

            // Seed Products
            val seedProducts = listOf(
                Product("P001", "سكر أبيض ناعم", "P-001", "food", "كيلو", 450, 50, 18000.0, 25000.0, "6281234567890", "🍬"),
                Product("P002", "زيت نباتي سولي", "P-002", "food", "لتر", 120, 20, 30000.0, 40000.0, "6281234567891", "🫙"),
                Product("P003", "أرز بسمتي هندي", "P-003", "food", "كيلو", 8, 30, 12000.0, 18000.0, "6281234567892", "🍚"),
                Product("P004", "مصباح LED موفر 12W", "P-004", "electronics", "قطعة", 85, 10, 45000.0, 65000.0, "6281234567893", "💡"),
                Product("P005", "كابل شحن سريع Type-C", "P-005", "electronics", "قطعة", 3, 15, 25000.0, 45000.0, "6281234567894", "🔌"),
                Product("P006", "طحين قمح بلدي", "P-006", "food", "كيلو", 600, 100, 9000.0, 13000.0, "6281234567895", "🌾")
            )
            seedProducts.forEach { appDao.insertProduct(it) }

            // Seed Vouchers
            val seedVouchers = listOf(
                Voucher(1, "receipt", "A001", 150000.0, "دفعة نقدية على الحساب سنوية", "2026-05-22"),
                Voucher(2, "payment", "A003", 400000.0, "تسديد دفعة للمورد شحنة السكر", "2026-05-21"),
                Voucher(3, "receipt", "A002", 80000.0, "تسديد ديون فواتير مستحقة", "2026-05-20")
            )
            seedVouchers.forEach { appDao.insertVoucher(it) }

            // Seed Invoices
            val seedInvoices = listOf(
                Invoice(
                    "INV-001",
                    "sale",
                    "أبو محمد التاجر",
                    "2026-05-23",
                    450000.0,
                    85000.0,
                    "saved",
                    "توصيل للمحل باليد",
                    """[{"name":"سكر أبيض ناعم","qty":10,"price":25000.0,"cost":18000.0},{"name":"زيت نباتي سولي","qty":5,"price":40000.0,"cost":30000.0}]"""
                ),
                Invoice(
                    "INV-002",
                    "sale",
                    "شركة الفرات للتجارة",
                    "2026-05-23",
                    1250000.0,
                    220000.0,
                    "saved",
                    "شحن شركة الفرات",
                    """[{"name":"أرز بسمتي هندي","qty":50,"price":18000.0,"cost":12000.0},{"name":"سكر أبيض ناعم","qty":20,"price":25000.0,"cost":18000.0}]"""
                ),
                Invoice(
                    "INV-003",
                    "purchase",
                    "مورد السمرة للمواد الغذائية",
                    "2026-05-22",
                    800000.0,
                    0.0,
                    "saved",
                    "مشتريات طحين وسكر",
                    "[]"
                ),
                Invoice(
                    "INV-004",
                    "sale",
                    "مطعم الغدير السياحي",
                    "2026-05-22",
                    320000.0,
                    60000.0,
                    "saved",
                    "تسليم فوري للمطبخ",
                    "[]"
                ),
                Invoice(
                    "INV-005",
                    "return",
                    "أبو محمد التاجر",
                    "2026-05-21",
                    75000.0,
                    -12000.0,
                    "saved",
                    "مرتجع سكر غير مطابق",
                    "[]"
                ),
                Invoice(
                    "INV-006",
                    "sale",
                    "مطعم الغدير السياحي",
                    "2026-05-21",
                    180000.0,
                    35000.0,
                    "draft",
                    "مسودة مراجعة لاحقاً",
                    "[]"
                )
            )
            seedInvoices.forEach { appDao.insertInvoice(it) }
        }
    }
}
