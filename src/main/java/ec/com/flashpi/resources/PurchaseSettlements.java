package ec.com.flashpi.resources;

import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.models.CreatePurchaseSettlement;

/** Purchase settlements (liquidaciones de compra). */
public final class PurchaseSettlements extends DocumentIssuer<CreatePurchaseSettlement> {
  /**
   * Created by the client: use {@code flashpi.purchaseSettlements()}.
   *
   * @param engine the client's engine
   */
  public PurchaseSettlements(Engine engine) {
    super(engine, "/v1/purchase-settlements", CreatePurchaseSettlement::getOptions);
  }
}
