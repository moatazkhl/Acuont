package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "products")
@Serializable
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val barcode: String = "",
    val category: String = "عام",
    val quantity: Double = 0.0,
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val minLimit: Double = 0.0,
    val shelfLocation: String = ""
)

@Entity(tableName = "invoices")
@Serializable
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceNumber: String,
    val type: String, // "SALE" (بيع) or "PURCHASE" (شراء)
    val date: Long, // Timestamp in ms
    val partyName: String, // Customer or Supplier Name
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val status: String = "PAID", // "PAID", "PARTIAL", "UNPAID"
    val notes: String = ""
)

@Entity(tableName = "invoice_items")
@Serializable
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double
)

@Entity(tableName = "vouchers")
@Serializable
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val voucherNumber: String,
    val type: String, // "RECEIPT" (قبض) or "PAYMENT" (صرف)
    val date: Long, // Timestamp in ms
    val partyName: String, // Customer, Employee, etc.
    val amount: Double,
    val notes: String = ""
)

@Serializable
data class AppBackupData(
    val version: Int = 1,
    val backupTimestamp: Long,
    val products: List<Product> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val invoiceItems: List<InvoiceItem> = emptyList(),
    val vouchers: List<Voucher> = emptyList()
)

data class AdminUserItem(
    val name: String,
    val phone: String,
    val status: String // "TRIAL", "PENDING", "ACTIVE"
)
