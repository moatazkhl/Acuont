package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "users")
@Serializable
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String, // will act as unique identifier
    val password: String,
    val status: String, // "TRIAL", "PENDING", "ACTIVE"
    val role: String // "USER", "ADMIN"
)

@Entity(tableName = "products")
@Serializable
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: Double,
    val purchasePrice: Double,
    val sellingPrice: Double
)

@Entity(tableName = "invoices")
@Serializable
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "SALE", "PURCHASE", "RETURN"
    val documentNumber: String,
    val customerName: String,
    val dateMillis: Long,
    val totalAmount: Double,
    val detailsJson: String // Serialized array of products, e.g., [{"productName":"أقلام","quantity":5.0,"unitPrice":300.0}]
)

@Entity(tableName = "vouchers")
@Serializable
data class VoucherEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "RECEIPT", "PAYMENT"
    val documentNumber: String,
    val partyName: String,
    val dateMillis: Long,
    val amount: Double,
    val notes: String
)

@Serializable
data class InvoiceItem(
    val productName: String,
    val quantity: Double,
    val unitPrice: Double
)

@Serializable
data class FinancialMetrics(
    val totalPurchases: Double,
    val totalSales: Double,
    val totalReturns: Double,
    val totalReceipts: Double,
    val totalPayments: Double,
    val warehouseValue: Double,
    val estimatedProfit: Double,
    val netCashFlow: Double
)

@Serializable
data class AppBackupData(
    val users: List<UserEntity>,
    val products: List<ProductEntity>,
    val invoices: List<InvoiceEntity>,
    val vouchers: List<VoucherEntity>
)
