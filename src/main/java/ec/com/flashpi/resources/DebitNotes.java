package ec.com.flashpi.resources;

import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.models.CreateDebitNote;

/** Debit notes (notas de débito). */
public final class DebitNotes extends DocumentIssuer<CreateDebitNote> {
  /**
   * Created by the client: use {@code flashpi.debitNotes()}.
   *
   * @param engine the client's engine
   */
  public DebitNotes(Engine engine) {
    super(engine, "/v1/debit-notes", CreateDebitNote::getOptions);
  }
}
