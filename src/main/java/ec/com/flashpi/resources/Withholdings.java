package ec.com.flashpi.resources;

import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.models.CreateWithholding;

/** Withholding receipts (comprobantes de retención). */
public final class Withholdings extends DocumentIssuer<CreateWithholding> {
  /**
   * Created by the client: use {@code flashpi.withholdings()}.
   *
   * @param engine the client's engine
   */
  public Withholdings(Engine engine) {
    super(engine, "/v1/withholdings", CreateWithholding::getOptions);
  }
}
