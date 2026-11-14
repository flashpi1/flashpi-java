package ec.com.flashpi.internal;

import java.util.List;

/**
 * The body of every list operation: one page of items and where the next one starts.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 *
 * @param <T> the items
 */
public final class ListBody<T> {
  private final List<T> data;
  private final boolean hasMore;
  private final String nextCursor;

  ListBody(List<T> data, boolean hasMore, String nextCursor) {
    this.data = data;
    this.hasMore = hasMore;
    this.nextCursor = nextCursor;
  }

  /**
   * The items.
   *
   * @return the items; unmodifiable
   */
  public List<T> data() {
    return data;
  }

  /**
   * Whether more items follow.
   *
   * @return true when they do
   */
  public boolean hasMore() {
    return hasMore;
  }

  /**
   * The cursor of the next page.
   *
   * @return the cursor, or null on the last page
   */
  public String nextCursor() {
    return nextCursor;
  }
}
