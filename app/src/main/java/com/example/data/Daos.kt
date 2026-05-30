package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Invoices
    @Query("SELECT * FROM invoices ORDER BY date DESC, id DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: String)

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    // Accounts
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("UPDATE accounts SET balance = :newBalance WHERE id = :id")
    suspend fun updateAccountBalance(id: String, newBalance: Double)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    // Products
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("UPDATE products SET qty = :newQty WHERE id = :id")
    suspend fun updateProductQuantity(id: String, newQty: Int)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    // Categories
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ProductCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ProductCategory)

    @Delete
    suspend fun deleteCategory(category: ProductCategory)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // Vouchers
    @Query("SELECT * FROM vouchers ORDER BY date DESC, id DESC")
    fun getAllVouchers(): Flow<List<Voucher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: Voucher)

    @Delete
    suspend fun deleteVoucher(voucher: Voucher)

    @Query("DELETE FROM vouchers")
    suspend fun deleteAllVouchers()

    // Exchange Rates
    @Query("SELECT * FROM exchange_rates ORDER BY date DESC")
    fun getAllExchangeRates(): Flow<List<ExchangeRate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRate(rate: ExchangeRate)

    @Query("SELECT * FROM exchange_rates WHERE date = :date LIMIT 1")
    suspend fun getExchangeRateByDate(date: String): ExchangeRate?

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAllExchangeRates()
}
