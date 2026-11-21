package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.local.PreferencesManager
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptPrinter(private val context: Context, private val prefs: PreferencesManager) {

    fun generateReceiptPdf(sale: Sale, items: List<SaleItem>): File? {
        val pdfDocument = PdfDocument()
        val height = 660 + (items.size * 20)
        val pageInfo = PdfDocument.PageInfo.Builder(300, height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.color = Color.BLACK
        paint.textSize = 9f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        var y = 25f
        val xCenter = 150f

        // Header - Fiscal vs. Internal Control Notice
        paint.isFakeBoldText = true
        if (prefs.isFiscalPrinter) {
            paint.textSize = 10f
            val brand = prefs.fiscalPrinterBrand.ifBlank { "FISCAL" }
            canvas.drawText("SENIAT - FACTURA FISCAL ($brand)", xCenter, y, paint)
            y += 12f
            val fiscalInvoiceStr = String.format(Locale.getDefault(), "COMPROBANTE FISCAL N°: %08d", sale.id)
            canvas.drawText(fiscalInvoiceStr, xCenter, y, paint)
            y += 16f
        } else {
            paint.textSize = 9f
            canvas.drawText("SOLO PARA USO INTERNO - CONTROL DE VENTAS.", xCenter, y, paint)
            y += 12f
            canvas.drawText("NO VÁLIDO COMO FACTURA FISCAL", xCenter, y, paint)
            y += 18f
        }

        // Header
        paint.textSize = 14f
        canvas.drawText(prefs.storeName, xCenter, y, paint)
        y += 18
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif}", xCenter, y, paint)
        y += 14
        canvas.drawText(prefs.storeAddress, xCenter, y, paint)
        y += 14
        canvas.drawText("Telf: ${prefs.storePhone}", xCenter, y, paint)
        y += 18

        paint.textAlign = Paint.Align.LEFT
        val xLeft = 10f
        val xRight = 290f

        // Date and Customer
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${sdf.format(Date(sale.timestamp))}", xLeft, y, paint)
        y += 14
        canvas.drawText("Factura N°: ${sale.id}", xLeft, y, paint)
        y += 14
        val cashierDisplay = sale.cashierName.ifBlank { "Cajero" }
        val registerDisplay = sale.cashRegisterNumber.ifBlank { prefs.cashRegisterNumber.ifBlank { "01" } }
        canvas.drawText("Caja: $registerDisplay  •  Cajero(a): $cashierDisplay", xLeft, y, paint)
        y += 14
        canvas.drawText("Cliente: ${sale.customerName}", xLeft, y, paint)
        y += 14
        canvas.drawText("CI/RIF: ${sale.customerDocument}", xLeft, y, paint)
        y += 18

        val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0

        // Items header
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Producto", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total Bs.", xRight, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += 10
        paint.isFakeBoldText = false
        canvas.drawLine(10f, y, 290f, y, paint)
        y += 14

        for (item in items) {
            val exemptTag = if (item.isTaxExempt) " (E)" else ""
            val line = "${item.quantity}x ${item.productName}$exemptTag"
            canvas.drawText(line, xLeft, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            val itemTotalBs = item.priceUsd * rate * item.quantity
            canvas.drawText(String.format("Bs. %.2f", itemTotalBs), xRight, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 14
        }
        canvas.drawLine(10f, y, 290f, y, paint)
        y += 14

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 10f

        val baseTaxableBs = sale.baseTaxableUsd * rate
        val baseExemptBs = sale.baseExemptUsd * rate
        val ivaBs = sale.ivaUsd * rate
        val igtfBs = if (sale.igtfBs > 0.001) sale.igtfBs else sale.igtfUsd * rate
        val totalBsCalculated = if (sale.totalBs > 0.001) sale.totalBs else sale.totalUsd * rate

        canvas.drawText("Tasa BCV del Día: Bs. ${String.format("%.2f", rate)}", xRight, y, paint)
        y += 14
        canvas.drawText("Subtotal Gravable (16%): Bs. ${String.format("%.2f", baseTaxableBs)}", xRight, y, paint)
        y += 14
        if (sale.discountUsd > 0.001) {
            val discountBs = sale.discountUsd * rate
            canvas.drawText("Descuento (pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", xRight, y, paint)
            y += 14
        }
        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", xRight, y, paint)
            y += 14
        }
        canvas.drawText("IVA (16%): Bs. ${String.format("%.2f", ivaBs)}", xRight, y, paint)
        y += 14
        if (igtfBs > 0.001) {
            canvas.drawText("IGTF (3% Divisas): Bs. ${String.format("%.2f", igtfBs)}", xRight, y, paint)
            y += 14
        }
        if (sale.retencionIvaUsd > 0.001) {
            val retIvaBs = sale.retencionIvaUsd * rate
            canvas.drawText("Retención IVA: -Bs. ${String.format("%.2f", retIvaBs)}", xRight, y, paint)
            y += 14
        }
        if (sale.retencionIslrUsd > 0.001) {
            val retIslrBs = sale.retencionIslrUsd * rate
            canvas.drawText("Retención ISLR: -Bs. ${String.format("%.2f", retIslrBs)}", xRight, y, paint)
            y += 14
        }
        y += 2

        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL FACTURA: Bs. ${String.format("%.2f", totalBsCalculated)}", xRight, y, paint)
        y += 18

        paint.isFakeBoldText = false
        paint.textSize = 10f
        if (sale.paymentCashUsd > 0.001) {
            val cashBs = sale.paymentCashUsd * rate
            canvas.drawText("Efectivo Recibido: Bs. ${String.format("%.2f", cashBs)}", xRight, y, paint)
            y += 14
        }
        if (sale.paymentMobileBs > 0) {
            canvas.drawText("Pago Móvil: Bs. ${String.format("%.2f", sale.paymentMobileBs)}", xRight, y, paint)
            y += 14
        }
        if (sale.paymentCardBs > 0) {
            canvas.drawText("Punto / Tarjeta: Bs. ${String.format("%.2f", sale.paymentCardBs)}", xRight, y, paint)
            y += 14
        }
        if (sale.paymentCasheaUsd > 0.001) {
            val casheaBs = sale.paymentCasheaUsd * rate
            canvas.drawText("Cashea: Bs. ${String.format("%.2f", casheaBs)}", xRight, y, paint)
            y += 14
        }
        if (sale.paymentCreditUsd > 0.001) {
            val creditBs = sale.paymentCreditUsd * rate
            val creditStatus = if (sale.isCreditPaid) "PAGADO" else "PENDIENTE"
            val pendingCreditBs = (sale.paymentCreditUsd - sale.creditPaidAmountUsd).coerceAtLeast(0.0) * rate
            canvas.drawText("Crédito Concedido: Bs. ${String.format("%.2f", creditBs)}", xRight, y, paint)
            y += 14
            paint.isFakeBoldText = true
            canvas.drawText("Estado Crédito: $creditStatus", xRight, y, paint)
            paint.isFakeBoldText = false
            y += 14
            if (!sale.isCreditPaid) {
                canvas.drawText("Saldo Pendiente: Bs. ${String.format("%.2f", pendingCreditBs)}", xRight, y, paint)
                y += 14
            }
        }

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("¡Gracias por su compra!", xCenter, y, paint)

        pdfDocument.finishPage(page)

        return try {
            val file = File(context.cacheDir, "ticket_${sale.id}.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun generateQuotePdf(quote: Quote, items: List<QuoteItem>): File? {
        val pdfDocument = PdfDocument()
        val height = 660 + (items.size * 20)
        val pageInfo = PdfDocument.PageInfo.Builder(300, height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        var y = 25f
        val xCenter = 150f

        canvas.drawText("PRESUPUESTO / COTIZACIÓN", xCenter, y, paint)
        y += 12f
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText("DOCUMENTO NO VÁLIDO COMO FACTURA FISCAL", xCenter, y, paint)
        y += 18f

        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText(prefs.storeName.ifBlank { "MI COMERCIO POS" }, xCenter, y, paint)
        y += 18f
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("RIF: ${prefs.storeRif}", xCenter, y, paint)
        y += 14f
        if (prefs.storeAddress.isNotBlank()) {
            canvas.drawText(prefs.storeAddress, xCenter, y, paint)
            y += 14f
        }
        if (prefs.storePhone.isNotBlank()) {
            canvas.drawText("Telf: ${prefs.storePhone}", xCenter, y, paint)
            y += 16f
        }

        paint.textAlign = Paint.Align.LEFT
        val xLeft = 10f
        val xRight = 290f

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        canvas.drawText("Emisión: ${sdf.format(Date(quote.timestamp))}", xLeft, y, paint)
        y += 14f
        if (quote.validUntil > 0L) {
            canvas.drawText("Válido hasta: ${sdfDate.format(Date(quote.validUntil))} (${quote.validityDays} días)", xLeft, y, paint)
            y += 14f
        }
        paint.isFakeBoldText = true
        canvas.drawText("Presupuesto N°: ${quote.quoteNumber.ifBlank { "PRE-${quote.id}" }}", xLeft, y, paint)
        paint.isFakeBoldText = false
        y += 14f
        canvas.drawText("Cliente: ${quote.customerName}", xLeft, y, paint)
        y += 14f
        canvas.drawText("CI/RIF: ${quote.customerDocument}", xLeft, y, paint)
        y += 18f

        val rate = if (quote.rateBcv > 0.001) quote.rateBcv else 1.0

        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("Producto", xLeft, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total Bs.", xRight, y, paint)
        paint.textAlign = Paint.Align.LEFT
        y += 10f
        paint.isFakeBoldText = false
        canvas.drawLine(10f, y, 290f, y, paint)
        y += 14f

        for (item in items) {
            val exemptTag = if (item.isTaxExempt) " (E)" else ""
            val line = "${item.quantity}x ${item.productName}$exemptTag"
            canvas.drawText(line, xLeft, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            val itemTotalBs = item.priceUsd * rate * item.quantity
            canvas.drawText(String.format("Bs. %.2f", itemTotalBs), xRight, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y += 14f
        }
        canvas.drawLine(10f, y, 290f, y, paint)
        y += 14f

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 10f

        val baseTaxableBs = quote.baseTaxableUsd * rate
        val baseExemptBs = quote.baseExemptUsd * rate
        val ivaBs = quote.ivaUsd * rate
        val totalBsCalculated = if (quote.totalBs > 0.001) quote.totalBs else quote.totalUsd * rate

        canvas.drawText("Tasa BCV del Día: Bs. ${String.format("%.2f", rate)}", xRight, y, paint)
        y += 14f
        canvas.drawText("Subtotal Gravable (16%): Bs. ${String.format("%.2f", baseTaxableBs)}", xRight, y, paint)
        y += 14f
        if (quote.discountUsd > 0.001) {
            val discountBs = quote.discountUsd * rate
            canvas.drawText("Descuento (pre-IVA): -Bs. ${String.format("%.2f", discountBs)}", xRight, y, paint)
            y += 14f
        }
        if (baseExemptBs > 0.001) {
            canvas.drawText("Monto Exento (0%): Bs. ${String.format("%.2f", baseExemptBs)}", xRight, y, paint)
            y += 14f
        }
        canvas.drawText("IVA (16%): Bs. ${String.format("%.2f", ivaBs)}", xRight, y, paint)
        y += 18f

        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("TOTAL PRESUPUESTO: Bs. ${String.format("%.2f", totalBsCalculated)}", xRight, y, paint)
        y += 16f
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Equivalente USD: \$${String.format("%.2f", quote.totalUsd)}", xRight, y, paint)
        y += 18f

        if (quote.notesOrConditions.isNotBlank()) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Obs: ${quote.notesOrConditions.take(45)}", xLeft, y, paint)
            y += 16f
        }

        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Sujeto a disponibilidad y tasa BCV vigente.", xCenter, y, paint)

        pdfDocument.finishPage(page)

        return try {
            val file = File(context.cacheDir, "presupuesto_${quote.id}.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
