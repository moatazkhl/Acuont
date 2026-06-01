package com.example.data

import kotlinx.coroutines.flow.Flow

class Repository(private val db: AppDatabase) {
    private val productDao = db.productDao()
    private val invoiceDao = db.invoiceDao()
    private val invoiceItemDao = db.invoiceItemDao()
    private val voucherDao = db.voucherDao()

    // --- Products ---
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    suspend fun getProductById(id: Int): Product? = productDao.getProductById(id)
    suspend fun getProductByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)
    suspend fun insertProduct(product: Product): Long = productDao.insertProduct(product)
    suspend fun updateProduct(product: Product) = productDao.updateProduct(product)
    suspend fun deleteProduct(product: Product) = productDao.deleteProduct(product)

    // --- Invoices & Stock Adjustment ---
    val allInvoices: Flow<List<Invoice>> = invoiceDao.getAllInvoices()

    fun getInvoicesByType(type: String): Flow<List<Invoice>> = invoiceDao.getInvoicesByType(type)

    fun getItemsForInvoice(invoiceId: Int): Flow<List<InvoiceItem>> = invoiceItemDao.getItemsForInvoice(invoiceId)

    suspend fun insertInvoice(invoice: Invoice, items: List<InvoiceItem>): Long {
        val invoiceId = invoiceDao.insertInvoice(invoice).toInt()
        val itemsWithId = items.map { it.copy(invoiceId = invoiceId) }
        invoiceItemDao.insertInvoiceItems(itemsWithId)

        // Adjust product stock
        for (item in itemsWithId) {
            val delta = if (invoice.type == "SALE") -item.quantity else item.quantity
            productDao.adjustProductQuantity(item.productId, delta)
        }
        return invoiceId.toLong()
    }

    suspend fun deleteInvoice(invoice: Invoice) {
        val items = invoiceItemDao.getItemsForInvoiceSync(invoice.id)
        for (item in items) {
            // Reversing the invoice effect:
            // if was SALE (sold, stock decreased), we add back. If was PURCHASE (bought, stock increased), we subtract.
            val delta = if (invoice.type == "SALE") item.quantity else -item.quantity
            productDao.adjustProductQuantity(item.productId, delta)
        }
        invoiceItemDao.deleteItemsForInvoice(invoice.id)
        invoiceDao.deleteInvoice(invoice)
    }

    // --- Vouchers ---
    val allVouchers: Flow<List<Voucher>> = voucherDao.getAllVouchers()

    fun getVouchersByType(type: String): Flow<List<Voucher>> = voucherDao.getVouchersByType(type)

    suspend fun insertVoucher(voucher: Voucher): Long = voucherDao.insertVoucher(voucher)
    suspend fun deleteVoucher(voucher: Voucher) = voucherDao.deleteVoucher(voucher)
}
