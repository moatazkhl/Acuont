package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET status = :status WHERE phone = :phone")
    suspend fun updateUserStatus(phone: String, status: String)

    @Query("DELETE FROM users WHERE phone = :phone")
    suspend fun deleteUserByPhone(phone: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAllProducts(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY dateMillis DESC")
    fun getAllInvoicesFlow(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices")
    suspend fun getAllInvoices(): List<InvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices")
    suspend fun clearAllInvoices()
}

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers ORDER BY dateMillis DESC")
    fun getAllVouchersFlow(): Flow<List<VoucherEntity>>

    @Query("SELECT * FROM vouchers")
    suspend fun getAllVouchers(): List<VoucherEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: VoucherEntity): Long

    @Delete
    suspend fun deleteVoucher(voucher: VoucherEntity)

    @Query("DELETE FROM vouchers")
    suspend fun clearAllVouchers()
}
