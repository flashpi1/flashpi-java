package ec.com.flashpi.resources;

import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.models.CreateInvoice;

/** Invoices (facturas). */
public final class Invoices extends DocumentIssuer<CreateInvoice> {
  /**
   * Created by the client: use {@code flashpi.invoices()}.
   *
   * @param engine the client's engine
   */
  public Invoices(Engine engine) {
    super(engine, "/v1/invoices", CreateInvoice::getOptions);
  }
}
