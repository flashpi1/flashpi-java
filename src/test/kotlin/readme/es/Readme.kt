package readme.es

import ec.com.flashpi.Flashpi
import ec.com.flashpi.models.CreateInvoice
import ec.com.flashpi.models.DocumentItem
import ec.com.flashpi.models.DocumentOptions
import ec.com.flashpi.models.DocumentParty
import ec.com.flashpi.models.DocumentPayment
import ec.com.flashpi.models.DocumentStatus
import ec.com.flashpi.models.DocumentTax
import ec.com.flashpi.models.IdType
import ec.com.flashpi.models.PaymentMethod
import ec.com.flashpi.models.TaxType

fun main() {
    Flashpi().use { flashpi ->
        val invoice = flashpi.invoices().create(
            CreateInvoice.builder()
                .issuerId(System.getenv("FLASHPI_ISSUER_ID"))
                .establishment("001")
                .emissionPoint("001")
                .customer(DocumentParty.builder().idType(IdType.FINAL_CONSUMER).build())
                .items(
                    listOf(
                        DocumentItem.builder()
                            .code("P-001")
                            .description("Servicio de prueba")
                            .quantity("1")
                            .unitPrice("1.00")
                            .taxes(listOf(DocumentTax.builder().type(TaxType.VAT).rate("15").build()))
                            .build(),
                    ),
                )
                .payments(listOf(DocumentPayment.builder().method(PaymentMethod.CASH).amount("1.15").build()))
                .options(DocumentOptions.builder().waitForAuthorization(true).build())
                .build(),
        )

        when (invoice.status) {
            DocumentStatus.AUTHORIZED -> println("Autorizada: ${invoice.accessKey}")
            DocumentStatus.REJECTED -> println("Rechazada: ${invoice.sriMessages}")
            else -> println("Estado: ${invoice.status}")
        }

        for (document in flashpi.documents().list()) {
            println(document.accessKey)
        }
    }
}
