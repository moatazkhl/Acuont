package com.example.data

import kotlinx.coroutines.flow.Flow

class Repository(private val database: AppDatabase) {

    private val userDao = database.userDao()
    private val productDao = database.productDao()
    private val invoiceDao = database.invoiceDao()
    private val voucherDao = database.voucherDao()

    // Flow getters for reactive updates
    val allUsersFlow: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
    val allProductsFlow: Flow<List<ProductEntity>> = productDao.allProductsFlow // Wait, check Daos.kt: fun getAllProductsFlow()
    val allProductsFlowFromDao: Flow<List<ProductEntity>> = productDao.getAllProductsFlow()
    val allInvoicesFlow: Flow<List<InvoiceEntity>> = invoiceDao.getAllInvoicesFlow()
    val allVouchersFlow: Flow<List<VoucherEntity>> = voucherDao.getAllVouchersFlow()

    // Users Actions
    suspend fun getAllUsers(): List<UserEntity> = userDao.getAllUsers()
    suspend fun getUserByPhone(phone: String): UserEntity? = userDao.getUserByPhone(phone)
    suspend fun insertUser(user: UserEntity): Boolean {
        val result = userDao.insertUser(user)
        return result != -1L
    }
    suspend fun updateUser(user: UserEntity) = userDao.updateUser(user)
    suspend fun updateUserStatus(phone: String, status: String) = userDao.updateUserStatus(phone, status)
    suspend fun deleteUser(phone: String) = userDao.deleteUserByPhone(phone)

    // Products Actions
    suspend fun getAllProducts(): List<ProductEntity> = productDao.getAllProducts()
    suspend fun insertProduct(product: ProductEntity): Boolean {
        return productDao.insertProduct(product) != -1L
    }
    suspend fun updateProduct(product: ProductEntity) = productDao.updateProduct(product)
    suspend fun deleteProduct(product: ProductEntity) = productDao.deleteProduct(product)
    suspend fun clearProducts() = productDao.clearAllProducts()

    // Invoices Actions
    suspend fun getAllInvoices(): List<InvoiceEntity> = invoiceDao.getAllInvoices()
    suspend fun insertInvoice(invoice: InvoiceEntity): Boolean {
        return invoiceDao.insertInvoice(invoice) != -1L
    }
    suspend fun deleteInvoice(invoice: InvoiceEntity) = invoiceDao.deleteInvoice(invoice)
    suspend fun clearInvoices() = invoiceDao.clearAllInvoices()

    // Vouchers Actions
    suspend fun getAllVouchers(): List<VoucherEntity> = voucherDao.getAllVouchers()
    suspend fun insertVoucher(voucher: VoucherEntity): Boolean {
        return voucherDao.insertVoucher(voucher) != -1L
    }
    suspend fun deleteVoucher(voucher: VoucherEntity) = voucherDao.deleteVoucher(voucher)
    suspend fun clearVouchers() = voucherDao.clearAllVouchers()

    // Seed database with default admin & demo users for fast testing & evaluation
    suspend fun seedDatabase() {
        val existingAdmin = userDao.getUserByPhone("admin")
        if (existingAdmin == null) {
            userDao.insertUser(
                UserEntity(
                    name = "المدير العام",
                    phone = "admin",
                    password = "admin",
                    status = "ACTIVE",
                    role = "ADMIN"
                )
            )
        }

        val existingUser = userDao.getUserByPhone("0938385157")
        if (existingUser == null) {
            userDao.insertUser(
                UserEntity(
                    name = "موزع تجريبي",
                    phone = "0938385157",
                    password = "123456",
                    status = "TRIAL",
                    role = "USER"
                )
            )
        }
    }
}
