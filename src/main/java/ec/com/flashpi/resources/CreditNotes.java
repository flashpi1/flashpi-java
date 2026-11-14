package ec.com.flashpi.resources;

import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.models.CreateCreditNote;

/** Credit notes (notas de crédito). */
public final class CreditNotes extends DocumentIssuer<CreateCreditNote> {
  /**
   * Created by the client: use {@code flashpi.creditNotes()}.
   *
   * @param engine the client's engine
   */
  public CreditNotes(Engine engine) {
    super(engine, "/v1/credit-notes", CreateCreditNote::getOptions);
  }
}
