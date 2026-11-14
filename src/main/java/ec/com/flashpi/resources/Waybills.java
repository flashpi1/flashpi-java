package ec.com.flashpi.resources;

import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.models.CreateWaybill;

/** Waybills (guías de remisión). */
public final class Waybills extends DocumentIssuer<CreateWaybill> {
  /**
   * Created by the client: use {@code flashpi.waybills()}.
   *
   * @param engine the client's engine
   */
  public Waybills(Engine engine) {
    super(engine, "/v1/waybills", CreateWaybill::getOptions);
  }
}
