package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies ORDER BY name ASC")
    fun getAllCompaniesFlow(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun getCompanyById(id: Int): CompanyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: CompanyEntity): Long

    @Update
    suspend fun updateCompany(company: CompanyEntity)

    @Delete
    suspend fun deleteCompany(company: CompanyEntity)
}

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE companyId = :companyId ORDER BY code ASC")
    fun getCurrenciesForCompanyFlow(companyId: Int): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE companyId = :companyId")
    suspend fun getCurrenciesForCompany(companyId: Int): List<CurrencyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrency(currency: CurrencyEntity)

    @Update
    suspend fun updateCurrency(currency: CurrencyEntity)

    @Delete
    suspend fun deleteCurrency(currency: CurrencyEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE companyId = :companyId ORDER BY name ASC")
    fun getAccountsForCompanyFlow(companyId: Int): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE companyId = :companyId")
    suspend fun getAccountsForCompany(companyId: Int): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE companyId = :companyId ORDER BY name ASC")
    fun getProductsForCompanyFlow(companyId: Int): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE companyId = :companyId")
    suspend fun getProductsForCompany(companyId: Int): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices WHERE companyId = :companyId ORDER BY date DESC")
    fun getInvoicesForCompanyFlow(companyId: Int): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE companyId = :companyId")
    suspend fun getInvoicesForCompany(companyId: Int): List<InvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity)

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)
}

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers WHERE companyId = :companyId ORDER BY date DESC")
    fun getVouchersForCompanyFlow(companyId: Int): Flow<List<VoucherEntity>>

    @Query("SELECT * FROM vouchers WHERE companyId = :companyId")
    suspend fun getVouchersForCompany(companyId: Int): List<VoucherEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: VoucherEntity)

    @Update
    suspend fun updateVoucher(voucher: VoucherEntity)

    @Delete
    suspend fun deleteVoucher(voucher: VoucherEntity)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE companyId = :companyId ORDER BY date DESC, employeeName ASC")
    fun getAttendanceForCompanyFlow(companyId: Int): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE companyId = :companyId")
    suspend fun getAttendanceForCompany(companyId: Int): List<AttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceEntity)

    @Update
    suspend fun updateAttendance(attendance: AttendanceEntity)

    @Delete
    suspend fun deleteAttendance(attendance: AttendanceEntity)
}

@Dao
interface ManufacturingDao {
    @Query("SELECT * FROM manufacturing WHERE companyId = :companyId ORDER BY date DESC")
    fun getManufacturingForCompanyFlow(companyId: Int): Flow<List<ManufacturingEntity>>

    @Query("SELECT * FROM manufacturing WHERE companyId = :companyId")
    suspend fun getManufacturingForCompany(companyId: Int): List<ManufacturingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManufacturing(manufacturing: ManufacturingEntity)

    @Update
    suspend fun updateManufacturing(manufacturing: ManufacturingEntity)

    @Delete
    suspend fun deleteManufacturing(manufacturing: ManufacturingEntity)
}

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouses WHERE companyId = :companyId ORDER BY name ASC")
    fun getWarehousesForCompanyFlow(companyId: Int): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM warehouses WHERE companyId = :companyId")
    suspend fun getWarehousesForCompany(companyId: Int): List<WarehouseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehouse(warehouse: WarehouseEntity)

    @Update
    suspend fun updateWarehouse(warehouse: WarehouseEntity)

    @Delete
    suspend fun deleteWarehouse(warehouse: WarehouseEntity)
}
