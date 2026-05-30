package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey val id: String, // e.g. "INV-001"
    val type: String,          // "sale" (مبيعات), "purchase" (مشتريات), "return" (مرتجع)
    val customer: String,      // Customer/Supplier name
    val date: String,          // ISO date format "YYYY-MM-DD"
    val total: Double,
    val profit: Double,
    val status: String,        // "draft" (مسودة), "saved" (حفظ وطباعة)
    val notes: String,
    val itemsJson: String,      // Serialized JSON array of invoice items
    val currency: String = "ل.س"
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String, // e.g. "A001"
    val name: String,
    val type: String,          // "customer" (عميل), "supplier" (مورد), "expense" (مصروف), "other" (أخرى)
    val balance: Double,       // Net balance (positive: Arabic له / negative: Arabic عليه)
    val phone: String,
    val address: String,
    val notes: String,
    val color: String,          // Card color hex string
    val currency: String = "ل.س"
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String, // e.g. "P001"
    val name: String,
    val code: String,
    val cat: String,           // ID of the ProductCategory (e.g. "food", "electronics", or custom code)
    val unit: String,          // e.g., "كيلو", "قطعة"
    val qty: Int,
    val minQty: Int,
    val buyPrice: Double,
    val sellPrice: Double,
    val barcode: String,
    val icon: String           // Emoji icon
)

@Entity(tableName = "categories")
data class ProductCategory(
    @PrimaryKey val id: String, // e.g., "food", "electronics", or auto-generated "CAT-123"
    val name: String,           // e.g., "غذاء"
    val icon: String = "📁"     // Emoji/Icon
)

@Entity(tableName = "vouchers")
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,          // "receipt" (سند قبض), "payment" (سند صرف)
    val accountId: String,     // Target account ID
    val amount: Double,
    val desc: String,
    val date: String
)

data class InvoiceItem(
    val name: String,
    val qty: Int,
    val price: Double,
    val cost: Double
)
