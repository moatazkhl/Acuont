package com.example.data

import kotlinx.coroutines.flow.Flow

class Repository(private val db: AppDatabase) {

    // DAOs
    val companyDao = db.companyDao()
    val currencyDao = db.currencyDao()
    val accountDao = db.accountDao()
    val productDao = db.productDao()
    val invoiceDao = db.invoiceDao()
    val voucherDao = db.voucherDao()
    val attendanceDao = db.attendanceDao()
    val manufacturingDao = db.manufacturingDao()
    val warehouseDao = db.warehouseDao()

    // Companies
    val allCompaniesFlow: Flow<List<CompanyEntity>> = companyDao.getAllCompaniesFlow()
    suspend fun getCompanyById(id: Int) = companyDao.getCompanyById(id)
    suspend fun insertCompany(company: CompanyEntity) = companyDao.insertCompany(company)
    suspend fun updateCompany(company: CompanyEntity) = companyDao.updateCompany(company)
    suspend fun deleteCompany(company: CompanyEntity) = companyDao.deleteCompany(company)

    // Currencies
    fun getCurrenciesForCompanyFlow(companyId: Int) = currencyDao.getCurrenciesForCompanyFlow(companyId)
    suspend fun getCurrenciesForCompany(companyId: Int) = currencyDao.getCurrenciesForCompany(companyId)
    suspend fun insertCurrency(currency: CurrencyEntity) = currencyDao.insertCurrency(currency)
    suspend fun updateCurrency(currency: CurrencyEntity) = currencyDao.updateCurrency(currency)
    suspend fun deleteCurrency(currency: CurrencyEntity) = currencyDao.deleteCurrency(currency)

    // Accounts
    fun getAccountsForCompanyFlow(companyId: Int) = accountDao.getAccountsForCompanyFlow(companyId)
    suspend fun getAccountsForCompany(companyId: Int) = accountDao.getAccountsForCompany(companyId)
    suspend fun insertAccount(account: AccountEntity) = accountDao.insertAccount(account)
    suspend fun updateAccount(account: AccountEntity) = accountDao.updateAccount(account)
    suspend fun deleteAccount(account: AccountEntity) = accountDao.deleteAccount(account)

    // Products
    fun getProductsForCompanyFlow(companyId: Int) = productDao.getProductsForCompanyFlow(companyId)
    suspend fun getProductsForCompany(companyId: Int) = productDao.getProductsForCompany(companyId)
    suspend fun insertProduct(product: ProductEntity) = productDao.insertProduct(product)
    suspend fun updateProduct(product: ProductEntity) = productDao.updateProduct(product)
    suspend fun deleteProduct(product: ProductEntity) = productDao.deleteProduct(product)

    // Invoices
    fun getInvoicesForCompanyFlow(companyId: Int) = invoiceDao.getInvoicesForCompanyFlow(companyId)
    suspend fun getInvoicesForCompany(companyId: Int) = invoiceDao.getInvoicesForCompany(companyId)
    suspend fun insertInvoice(invoice: InvoiceEntity) = invoiceDao.insertInvoice(invoice)
    suspend fun updateInvoice(invoice: InvoiceEntity) = invoiceDao.updateInvoice(invoice)
    suspend fun deleteInvoice(invoice: InvoiceEntity) = invoiceDao.deleteInvoice(invoice)

    // Vouchers
    fun getVouchersForCompanyFlow(companyId: Int) = voucherDao.getVouchersForCompanyFlow(companyId)
    suspend fun getVouchersForCompany(companyId: Int) = voucherDao.getVouchersForCompany(companyId)
    suspend fun insertVoucher(voucher: VoucherEntity) = voucherDao.insertVoucher(voucher)
    suspend fun updateVoucher(voucher: VoucherEntity) = voucherDao.updateVoucher(voucher)
    suspend fun deleteVoucher(voucher: VoucherEntity) = voucherDao.deleteVoucher(voucher)

    // Attendance
    fun getAttendanceForCompanyFlow(companyId: Int) = attendanceDao.getAttendanceForCompanyFlow(companyId)
    suspend fun getAttendanceForCompany(companyId: Int) = attendanceDao.getAttendanceForCompany(companyId)
    suspend fun insertAttendance(attendance: AttendanceEntity) = attendanceDao.insertAttendance(attendance)
    suspend fun updateAttendance(attendance: AttendanceEntity) = attendanceDao.updateAttendance(attendance)
    suspend fun deleteAttendance(attendance: AttendanceEntity) = attendanceDao.deleteAttendance(attendance)

    // Manufacturing
    fun getManufacturingForCompanyFlow(companyId: Int) = manufacturingDao.getManufacturingForCompanyFlow(companyId)
    suspend fun getManufacturingForCompany(companyId: Int) = manufacturingDao.getManufacturingForCompany(companyId)
    suspend fun insertManufacturing(manufacturing: ManufacturingEntity) = manufacturingDao.insertManufacturing(manufacturing)
    suspend fun updateManufacturing(manufacturing: ManufacturingEntity) = manufacturingDao.updateManufacturing(manufacturing)
    suspend fun deleteManufacturing(manufacturing: ManufacturingEntity) = manufacturingDao.deleteManufacturing(manufacturing)

    // Warehouses
    fun getWarehousesForCompanyFlow(companyId: Int) = warehouseDao.getWarehousesForCompanyFlow(companyId)
    suspend fun getWarehousesForCompany(companyId: Int) = warehouseDao.getWarehousesForCompany(companyId)
    suspend fun insertWarehouse(warehouse: WarehouseEntity) = warehouseDao.insertWarehouse(warehouse)
    suspend fun updateWarehouse(warehouse: WarehouseEntity) = warehouseDao.updateWarehouse(warehouse)
    suspend fun deleteWarehouse(warehouse: WarehouseEntity) = warehouseDao.deleteWarehouse(warehouse)
}
