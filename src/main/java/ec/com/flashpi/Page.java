package ec.com.flashpi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * One page of a list, newest first (establishments and emission points: by code).
 *
 * <p>Iterating it walks this page and every following one, fetching them as needed:
 *
 * <pre>{@code
 * for (Document document : flashpi.documents().list(params)) {
 *   System.out.println(document.getAccessKey());
 * }
 * }</pre>
 *
 * <p>{@link #stream()} does the same lazily, and {@link #getData()} and {@link #nextPage()} move
 * one page at a time. Iterating twice fetches the following pages again.
 *
 * @param <T> the items
 */
public final class Page<T> implements Iterable<T> {
  private final List<T> data;
  private final boolean hasMore;
  private final String nextCursor;
  private final Function<String, Page<T>> fetchPage;

  /**
   * A page.
   *
   * @param data the items of this page
   * @param hasMore whether more items follow
   * @param nextCursor the cursor of the next page, or null
   * @param fetchPage fetches the page after a cursor (same filters and options)
   */
  public Page(
      List<T> data, boolean hasMore, String nextCursor, Function<String, Page<T>> fetchPage) {
    this.data = Collections.unmodifiableList(new ArrayList<>(data));
    this.hasMore = hasMore;
    this.nextCursor = nextCursor;
    this.fetchPage = Objects.requireNonNull(fetchPage, "fetchPage");
  }

  /**
   * The items of this page.
   *
   * @return the items; unmodifiable
   */
  public List<T> getData() {
    return data;
  }

  /**
   * Whether more items follow this page.
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
  public String getNextCursor() {
    return nextCursor;
  }

  /**
   * Whether {@link #nextPage()} returns a page.
   *
   * @return true when there is a next page
   */
  public boolean hasNextPage() {
    return hasMore && nextCursor != null;
  }

  /**
   * Fetches the next page, with the same filters and options.
   *
   * @return the next page, or null after the last one
   */
  public Page<T> nextPage() {
    return hasNextPage() ? fetchPage.apply(nextCursor) : null;
  }

  /**
   * Every item of this page and the following ones, fetching them as needed.
   *
   * @return the iterator
   */
  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      private Page<T> page = Page.this;
      private Iterator<T> items = data.iterator();

      @Override
      public boolean hasNext() {
        while (!items.hasNext()) {
          if (page == null) {
            return false;
          }
          page = page.nextPage();
          if (page == null) {
            return false;
          }
          items = page.data.iterator();
        }
        return true;
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return items.next();
      }
    };
  }

  /**
   * Every item of this page and the following ones, as a lazy stream.
   *
   * @return the stream
   */
  public Stream<T> stream() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED), false);
  }

  @Override
  public String toString() {
    return "Page{data=<"
        + data.size()
        + " items>, hasMore="
        + hasMore
        + ", nextCursor="
        + nextCursor
        + "}";
  }
}
