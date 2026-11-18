package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.data.local.PreferencesManager
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TicketUtils {

    /**
     * Genera el bitmap del ticket de venta adaptado dinámicamente al ancho del papel térmico:
     * - 58 mm: 384 px de ancho (portátil / BLE Fun Print / mini)
     * - 80 mm: 576 px de ancho (mostrador / USB / POS estándar)
     */
    fun generateTicketBitmap(
        context: Context,
        prefs: PreferencesManager,
        sale: Sale,
        items: List<SaleItem>,
        customWidthPx: Int? = null
    ): Bitmap {
        val is80mm = customWidthPx == 576 || (customWidthPx == null && prefs.printerPaperWidth.contains("80"))
        val width = if (is80mm) 576 else 384
        val baseLineHeight = if (is80mm) 30 else 24
        val height = (if (is80mm) 950 else 800) + (items.size * baseLineHeight)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = if (is80mm) 18f else 15f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        var y = if (is80mm) 48f else 38f
        val xCenter = width / 2f
        val xLeft = if (is80mm) 28f else 18f
        val xRight = width - (if (is80mm) 28f else 18f)

        // Header - Fiscal vs. Internal Control Notice
        paint.isFakeBoldText = true
        if (prefs.isFiscalPrinter) {
            paint.textSize = if (is80mm) 16f else 13f
            val brand = prefs.fiscalPrinterBrand.ifBlank { "FISCAL" }
            canvas.drawText("SENIAT - FACTURA FISCAL ($brand)", xCenter, y, paint)
            y += if (is80mm) 20f else 16f
            val fiscalInvoiceStr = String.format(Locale.getDefault(), "COMPROBANTE FISCAL N°: %08d", sale.id)
            canvas.drawText(fiscalInvoiceStr, xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        } else {
            paint.textSize = if (is80mm) 14f else 11f
            canvas.drawText("SOLO PARA USO INTERNO - CONTROL DE VENTAS.", xCenter, y, paint)
            y += if (is80mm) 18f else 15f
            canvas.drawText("NO VÁLIDO COMO FACTURA FISCAL", xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        }
        paint.isFakeBoldText = false

        // Header - Store Info
        paint.textSize = if (is80mm) 25f else 19f
        paint.isFakeBoldText = true
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }, xCenter, y, paint)
        y += if (is80mm) 30f else 24f

        paint.textSize = if (is80mm) 16f else 13f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress, xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", xCenter, y, paint)
            y += if (is80mm) 24f else 18f
        }
        y += if (is80mm) 10f else 6f

        // Document Info
        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${sdf.format(Date(sale.timestamp))}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        paint.isFakeBoldText = true
        canvas.drawText("Factura N°: ${sale.id}", xLeft, y, paint)
        paint.isFakeBoldText = false
        y += if (is80mm) 22f else 18f

        val cashierDisplay = sale.cashierName.ifBlank { "Cajero" }
        val registerDisplay = sale.cashRegisterNumber.ifBlank { prefs.cashRegisterNumber.ifBlank { "01" } }
        canvas.drawText("Caja: $registerDisplay  •  Cajero(a): $cashierDisplay", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("Cliente: ${sale.customerName.ifBlank { "Consumidor Final" }}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("CI/RIF: ${sale.customerDocument.ifBlank { "V-00000000" }}", xLeft, y, paint)
        y += if (is80mm) 26f else 22f

        val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0

        // Column Titles
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 16f else 13f
        canvas.drawText("Cant / Descripción", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total Bs.", xRight, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += if (is80mm) 10f else 8f
        paint.isFakeBoldText = false
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        // Product Items Detail (Strictly in Bolívares)
        for (item in items) {
            val exemptTag = if (item.isTaxExempt) " (E)" else ""
            val line = "${item.quantity}x ${item.productName}$exemptTag"
            canvas.drawText(line, xLeft, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            val itemTotalBs = item.priceUsd * rate * item.quantity
            canvas.drawText(String.format("Bs. %.2f", itemTotalBs), xRight, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += if (is80mm) 24f else 19f
        }

        y += if (is80mm) 8f else 6f
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = if (is80mm) 16f else 13f

        val baseTaxableBs = sale.baseTaxableUsd * rate
        val baseExemptBs = sale.baseExemptUsd * rate
        val ivaBs = sale.ivaUsd * rate
        val igtfBs = if (sale.igtfBs > 0.001) sale.igtfBs else sale.igtfUsd * rate
        val totalBsCalculated = if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * rate

        canvas.drawText("Tasa BCV del Día: Bs. ${String.format("%.2f", rate)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        canvas.drawText("Base Imponible (16%): Bs. ${String.format("%.2f", baseTaxableBs)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        if (sale.discountUsd > 0.001) {
            val discountBs = sale.discountUsd * rate
            canvas.drawText("Descuento (pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        canvas.drawText("IVA (16%): Bs. ${String.format("%.2f", ivaBs)}", xRight, y, paint)
        y += if (is80mm) 22f else 18f
        if (igtfBs > 0.001) {
            canvas.drawText("IGTF (3% Divisas): Bs. ${String.format("%.2f", igtfBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.retencionIvaUsd > 0.001) {
            val retIvaBs = sale.retencionIvaUsd * rate
            canvas.drawText("Retención IVA: -Bs. ${String.format("%.2f", retIvaBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.retencionIslrUsd > 0.001) {
            val retIslrBs = sale.retencionIslrUsd * rate
            canvas.drawText("Retención ISLR: -Bs. ${String.format("%.2f", retIslrBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        y += if (is80mm) 6f else 4f

        paint.textSize = if (is80mm) 20f else 16f
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL FACTURA: Bs. ${String.format("%.2f", totalBsCalculated)}", xRight, y, paint)
        y += if (is80mm) 28f else 24f

        paint.isFakeBoldText = false
        paint.textSize = if (is80mm) 16f else 13f
        if (sale.paymentCashUsd > 0.001) {
            val cashBs = sale.paymentCashUsd * rate
            canvas.drawText("Efectivo Recibido: Bs. ${String.format("%.2f", cashBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.paymentMobileBs > 0) {
            canvas.drawText("Pago Móvil: Bs. ${String.format("%.2f", sale.paymentMobileBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.paymentCardBs > 0) {
            canvas.drawText("Punto / Tarjeta: Bs. ${String.format("%.2f", sale.paymentCardBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (sale.paymentCreditUsd > 0.001) {
            val creditBs = sale.paymentCreditUsd * rate
            val creditStatus = if (sale.isCreditPaid) "PAGADO" else "PENDIENTE POR COBRAR"
            val pendingCreditBs = (sale.paymentCreditUsd - sale.creditPaidAmountUsd).coerceAtLeast(0.0) * rate
            canvas.drawText("Crédito Concedido: Bs. ${String.format("%.2f", creditBs)}", xRight, y, paint)
            y += if (is80mm) 22f else 18f
            paint.isFakeBoldText = true
            canvas.drawText("Estado Crédito: $creditStatus", xRight, y, paint)
            paint.isFakeBoldText = false
            y += if (is80mm) 22f else 18f
            if (!sale.isCreditPaid) {
                canvas.drawText("Saldo Pendiente: Bs. ${String.format("%.2f", pendingCreditBs)}", xRight, y, paint)
                y += if (is80mm) 22f else 18f
            }
        }

        y += if (is80mm) 26f else 20f
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (is80mm) 16f else 13f
        canvas.drawText("¡Gracias por su compra!", xCenter, y, paint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, (y + 35).toInt())
    }

    /**
     * Genera el bitmap de la Nota de Entrega adaptado dinámicamente al ancho térmico (58mm o 80mm).
     */
    fun generateDeliveryNoteBitmap(
        context: Context,
        prefs: PreferencesManager,
        note: DeliveryNote,
        items: List<DeliveryNoteItem>,
        customWidthPx: Int? = null
    ): Bitmap {
        val is80mm = customWidthPx == 576 || (customWidthPx == null && prefs.printerPaperWidth.contains("80"))
        val width = if (is80mm) 576 else 384
        val baseLineHeight = if (is80mm) 28 else 22
        val height = (if (is80mm) 920 else 780) + (items.size * baseLineHeight)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = if (is80mm) 18f else 15f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }

        var y = if (is80mm) 44f else 36f
        val xCenter = width / 2f
        val xLeft = if (is80mm) 28f else 18f
        val xRight = width - (if (is80mm) 28f else 18f)

        // Header
        paint.textSize = if (is80mm) 24f else 19f
        paint.isFakeBoldText = true
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }, xCenter, y, paint)
        y += if (is80mm) 28f else 22f

        paint.textSize = if (is80mm) 16f else 13f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress, xCenter, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", xCenter, y, paint)
            y += if (is80mm) 24f else 18f
        }
        y += if (is80mm) 10f else 6f

        // Document Banner
        paint.color = Color.DKGRAY
        paint.textSize = if (is80mm) 18f else 14f
        paint.isFakeBoldText = true
        canvas.drawText("--- NOTA DE ENTREGA ---", xCenter, y, paint)
        y += if (is80mm) 22f else 18f
        paint.textSize = if (is80mm) 14f else 11f
        paint.isFakeBoldText = false
        canvas.drawText("(DOCUMENTO NO FISCAL - CONTROL INTERNO / DESPACHO)", xCenter, y, paint)
        paint.color = Color.BLACK
        y += if (is80mm) 28f else 22f

        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${sdf.format(Date(note.timestamp))}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        paint.isFakeBoldText = true
        canvas.drawText("N° Control: ${note.noteNumber}", xLeft, y, paint)
        paint.isFakeBoldText = false
        y += if (is80mm) 22f else 18f

        val noteCashier = note.cashierName.ifBlank { "Cajero" }
        val noteRegister = note.cashRegisterNumber.ifBlank { prefs.cashRegisterNumber.ifBlank { "01" } }
        canvas.drawText("Caja: $noteRegister  •  Despachado por: $noteCashier", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("Cliente: ${note.customerName.ifBlank { "Cliente General" }}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        canvas.drawText("CI/RIF: ${note.customerDocument.ifBlank { "V-00000000" }}", xLeft, y, paint)
        y += if (is80mm) 22f else 18f

        if (note.recipientName.isNotBlank()) {
            canvas.drawText("Receptor: ${note.recipientName}", xLeft, y, paint)
            y += if (is80mm) 22f else 18f
        }
        if (note.reasonOrObservation.isNotBlank()) {
            canvas.drawText("Motivo: ${note.reasonOrObservation}", xLeft, y, paint)
            y += if (is80mm) 22f else 18f
        }
        y += if (is80mm) 10f else 6f

        // Column Titles
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 16f else 13f
        canvas.drawText("Cant. / Descripción", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Ref. Total", xRight, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += if (is80mm) 10f else 8f
        paint.isFakeBoldText = false
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        // Items
        for (item in items) {
            val line = "${item.quantity}x ${item.productName}"
            canvas.drawText(line, xLeft, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            val itemTotalUsd = item.priceUsd * item.quantity
            canvas.drawText("\$${String.format("%.2f", itemTotalUsd)}", xRight, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += if (is80mm) 24f else 19f
        }

        y += if (is80mm) 8f else 6f
        canvas.drawLine(xLeft, y, xRight, y, paint)
        y += if (is80mm) 24f else 19f

        // Totals
        paint.textAlign = Paint.Align.RIGHT
        paint.isFakeBoldText = true
        paint.textSize = if (is80mm) 18f else 14f
        canvas.drawText("Valor Ref. Total: \$${String.format("%.2f", note.totalEstimatedUsd)} USD", xRight, y, paint)
        y += if (is80mm) 24f else 19f
        paint.textSize = if (is80mm) 15f else 12f
        paint.isFakeBoldText = false
        canvas.drawText("Valor Ref. en Bs: Bs. ${String.format("%.2f", note.totalEstimatedBs)} (Tasa: ${String.format("%.2f", note.rateBcv)})", xRight, y, paint)
        y += if (is80mm) 38f else 30f

        // Signatures area
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (is80mm) 14f else 11f
        val sigLineLeft = xLeft + 15f
        val sigLineRight = xCenter - 10f
        val sig2LineLeft = xCenter + 10f
        val sig2LineRight = xRight - 15f

        canvas.drawLine(sigLineLeft, y, sigLineRight, y, paint)
        canvas.drawLine(sig2LineLeft, y, sig2LineRight, y, paint)
        y += if (is80mm) 20f else 16f
        canvas.drawText("Entregado Por", (sigLineLeft + sigLineRight) / 2f, y, paint)
        canvas.drawText("Recibido Conforme", (sig2LineLeft + sig2LineRight) / 2f, y, paint)
        y += if (is80mm) 28f else 22f

        paint.textSize = if (is80mm) 13f else 10f
        canvas.drawText("La firma confirma la recepción a satisfacción de la mercancía.", xCenter, y, paint)

        return Bitmap.createBitmap(bitmap, 0, 0, width, (y + 35).toInt())
    }

    fun saveDeliveryNoteBitmapToFile(context: Context, bitmap: Bitmap, noteId: Int): File? {
        return try {
            val file = File(context.cacheDir, "nota_entrega_$noteId.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera un ticket en formato de imagen cuadrada perfectamente estructurada para enviar por WhatsApp:
     * - Encabezado destacado: "DOCUMENTO NO VÁLIDO COMO FACTURA FISCAL"
     * - Información de la empresa (Nombre, RIF, Teléfono, Dirección)
     * - Logo de la empresa en la esquina paralela si fue cargado por el usuario
     * - Tabla organizada: Producto | Cant. | P. Unit (Sin IVA) | Total Bs.
     * - Resumen de Totales: Subtotal sin IVA, Descuento, Base Imponible, IVA (16%), Total a pagar con IVA.
     */
    fun generateWhatsAppSquareTicketBitmap(
        context: Context,
        prefs: PreferencesManager,
        sale: Sale,
        items: List<SaleItem>
    ): Bitmap {
        val width = 800
        val headerHeight = 280
        val itemRowHeight = 42
        val tableHeaderHeight = 44
        val totalsHeight = 240
        val contentHeight = headerHeight + tableHeaderHeight + (items.size * itemRowHeight) + totalsHeight + 60
        val height = contentHeight.coerceAtLeast(800)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean off-white background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        // Draw Outer Container Card
        val cardMargin = 20f
        val cardPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, cardPaint)
        canvas.drawRoundRect(cardMargin, cardMargin, width - cardMargin, height - cardMargin, 20f, 20f, borderPaint)

        var y = 50f

        // 1. Non-Fiscal Banner Header
        val bannerPaint = Paint().apply {
            color = Color.parseColor("#FEF2F2")
            style = Paint.Style.FILL
        }
        val bannerBorderPaint = Paint().apply {
            color = Color.parseColor("#FCA5A5")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 42f, 10f, 10f, bannerPaint)
        canvas.drawRoundRect(40f, y, width - 40f, y + 42f, 10f, 10f, bannerBorderPaint)

        paint.color = Color.parseColor("#991B1B")
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("DOCUMENTO NO VÁLIDO COMO FACTURA FISCAL", width / 2f, y + 27f, paint)

        y += 65f

        // 2. Company Info (Left Column) vs. Logo (Right Column / Parallel Corner)
        val leftX = 45f
        val logoWidth = 130f
        val logoHeight = 130f
        val logoX = width - 45f - logoWidth
        val logoY = y

        // Try to load store logo if user uploaded one
        var hasLogo = false
        if (prefs.storeLogoPath.isNotBlank()) {
            try {
                val logoFile = File(prefs.storeLogoPath)
                if (logoFile.exists()) {
                    val originalBitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                    if (originalBitmap != null) {
                        val scaledLogo = Bitmap.createScaledBitmap(originalBitmap, logoWidth.toInt(), logoHeight.toInt(), true)
                        canvas.drawBitmap(scaledLogo, logoX, logoY, null)
                        hasLogo = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!hasLogo) {
            // Draw logo placeholder box
            val placeholderPaint = Paint().apply {
                color = Color.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val placeholderBorder = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderPaint)
            canvas.drawRoundRect(logoX, logoY, logoX + logoWidth, logoY + logoHeight, 16f, 16f, placeholderBorder)

            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 12f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("LOGO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) - 5f, paint)
            canvas.drawText("COMERCIO", logoX + (logoWidth / 2f), logoY + (logoHeight / 2f) + 12f, paint)
        }

        // Store Details Text
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val storeNameText = prefs.storeName.ifBlank { "MI COMERCIO POS" }.take(22)
        canvas.drawText(storeNameText, leftX, y + 25f, paint)

        paint.textSize = 15f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#334155")
        canvas.drawText("RIF: ${prefs.storeRif.ifBlank { "J-00000000-0" }}", leftX, y + 52f, paint)

        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress.take(30), leftX, y + 74f, paint)
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", leftX, y + 96f, paint)
        }

        y += 140f

        // 3. Document Meta Info Box
        val metaPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + 55f, 10f, 10f, metaPaint)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.timestamp))

        paint.textSize = 14f
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Ticket N°: #${sale.id}", leftX + 10f, y + 24f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("Fecha: $dateStr", leftX + 10f, y + 44f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.parseColor("#0F172A")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val customerDisplay = sale.customerName.ifBlank { "Consumidor Final" }.take(22)
        canvas.drawText("Cliente: $customerDisplay", width - 50f, y + 24f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#64748B")
        val docDisplay = sale.customerDocument.ifBlank { "V-00000000" }
        canvas.drawText("CI/RIF: $docDisplay", width - 50f, y + 44f, paint)

        y += 70f

        // 4. Products Table Header
        val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0

        val tableHeaderPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(40f, y, width - 40f, y + tableHeaderHeight, 8f, 8f, tableHeaderPaint)

        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Producto", leftX + 10f, y + 28f, paint)

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Cant.", 380f, y + 28f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("P. Unit (Sin IVA)", 580f, y + 28f, paint)
        canvas.drawText("Total Bs.", width - 50f, y + 28f, paint)

        y += tableHeaderHeight + 8f

        // Products Rows
        paint.color = Color.parseColor("#1E293B")
        paint.typeface = Typeface.DEFAULT
        val rowLinePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        for (item in items) {
            val itemPriceBsTotal = item.priceUsd * rate
            val unitPriceNoTaxBs = if (!item.isTaxExempt) itemPriceBsTotal / 1.16 else itemPriceBsTotal
            val lineTotalBs = itemPriceBsTotal * item.quantity

            val exemptMark = if (item.isTaxExempt) " (E)" else ""
            val productNameDisplay = "${item.productName.take(20)}$exemptMark"

            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14f
            canvas.drawText(productNameDisplay, leftX + 10f, y + 26f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", 380f, y + 26f, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format("Bs. %.2f", unitPriceNoTaxBs), 580f, y + 26f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(String.format("Bs. %.2f", lineTotalBs), width - 50f, y + 26f, paint)

            y += itemRowHeight
            canvas.drawLine(40f, y, width - 40f, y, rowLinePaint)
            y += 4f
        }

        y += 15f

        // 5. Totals Breakdown Box
        val subtotalBs = sale.baseTaxableUsd * rate
        val baseExemptBs = sale.baseExemptUsd * rate
        val discountBs = sale.discountUsd * rate
        val ivaBs = sale.ivaUsd * rate
        val totalBs = if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * rate

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 14f
        paint.color = Color.parseColor("#475569")

        canvas.drawText("Base Imponible (Sin IVA): Bs. ${String.format("%.2f", subtotalBs)}", width - 50f, y, paint)
        y += 22f

        if (discountBs > 0.001) {
            paint.color = Color.parseColor("#059669")
            canvas.drawText("Descuento (Pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", width - 50f, y, paint)
            y += 22f
            paint.color = Color.parseColor("#475569")
        }

        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", width - 50f, y, paint)
            y += 22f
        }

        canvas.drawText("IVA Fiscal (16%): Bs. ${String.format("%.2f", ivaBs)}", width - 50f, y, paint)
        y += 28f

        // Grand Total Box
        val totalBoxPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(width - 420f, y, width - 40f, y + 54f, 12f, 12f, totalBoxPaint)

        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL: Bs. ${String.format("%.2f", totalBs)}", width - 60f, y + 34f, paint)

        y += 68f

        paint.color = Color.parseColor("#64748B")
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Equivalente USD (Tasa BCV Bs. ${String.format("%.2f", rate)}): $ ${String.format("%.2f", sale.totalUsd)}", width - 50f, y, paint)

        return bitmap
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, saleId: Int): File? {
        return try {
            val file = File(context.cacheDir, "ticket_$saleId.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun shareFile(context: Context, file: File, mimeType: String, packageName: String? = null) {
        val uri = getUriForFile(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            packageName?.let { setPackage(it) }
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Compartir ticket"))
        } catch (e: Exception) {
            context.startActivity(Intent.createChooser(intent.apply { setPackage(null) }, "Compartir ticket"))
        }
    }

    /**
     * Revisa si el cliente tiene un número telefónico registrado en la aplicación.
     * De tenerlo, abre directamente WhatsApp enviando la imagen del ticket a dicho destinatario.
     * Si no tiene número o falla la integración directa, abre el selector habitual de WhatsApp.
     */
    fun shareWhatsAppFile(
        context: Context,
        file: File,
        phone: String? = null,
        captionText: String = "Comprobante de venta"
    ) {
        val uri = getUriForFile(context, file)
        val cleanPhone = phone?.replace(Regex("[^0-9]"), "") ?: ""
        val formattedPhone = when {
            cleanPhone.startsWith("58") -> cleanPhone
            cleanPhone.startsWith("0") -> "58" + cleanPhone.substring(1)
            cleanPhone.length == 10 -> "58" + cleanPhone
            cleanPhone.isNotBlank() -> "58" + cleanPhone
            else -> ""
        }

        if (formattedPhone.isNotBlank()) {
            val jid = "$formattedPhone@s.whatsapp.net"
            var sent = false
            // 1. Intentar WhatsApp Estándar con JID directo
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra("jid", jid)
                    putExtra(Intent.EXTRA_TEXT, captionText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                context.startActivity(intent)
                sent = true
            } catch (e: Exception) {
                // 2. Intentar WhatsApp Business con JID directo
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra("jid", jid)
                        putExtra(Intent.EXTRA_TEXT, captionText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.whatsapp.w4b")
                    }
                    context.startActivity(intent)
                    sent = true
                } catch (e2: Exception) {
                    sent = false
                }
            }

            if (sent) {
                android.widget.Toast.makeText(context, "Enviando ticket directo a WhatsApp ($formattedPhone)", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                shareFile(context, file, "image/png", "com.whatsapp")
            }
        } else {
            shareFile(context, file, "image/png", "com.whatsapp")
        }
    }
}
