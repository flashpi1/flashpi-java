package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.exceptions.FlashpiException;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentStatus;
import ec.com.flashpi.models.DocumentsListParams;
import ec.com.flashpi.models.Issuer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PaginationTest {
  private static String doc(String id) {
    return "{\"id\":\"" + id + "\"}";
  }

  private static FakeHttpClient threePages() {
    return new FakeHttpClient()
        .then(Answer.json(200, Support.list(doc("a") + "," + doc("b"), true, "c1")))
        .then(Answer.json(200, Support.list(doc("c"), true, "c 2/é")))
        .then(Answer.json(200, Support.list(doc("d"), false, null)));
  }

  private static final DocumentsListParams PARAMS =
      DocumentsListParams.builder()
          .status(DocumentStatus.AUTHORIZED)
          .issueDateFrom(LocalDate.of(2026, 11, 1))
          .limit(2)
          .build();

  @Test
  void iteratesEveryPageWithTheSameFilters() {
    FakeHttpClient http = threePages();
    List<String> ids = new ArrayList<>();
    for (Document document : Support.client(http).documents().list(PARAMS)) {
      ids.add(document.getId());
    }
    assertEquals(List.of("a", "b", "c", "d"), ids);
    assertEquals(
        "status=authorized&issueDateFrom=2026-11-01&limit=2", http.requests.get(0).query());
    assertEquals(
        "status=authorized&issueDateFrom=2026-11-01&limit=2&cursor=c1",
        http.requests.get(1).query());
    assertEquals(
        "status=authorized&issueDateFrom=2026-11-01&limit=2&cursor=c+2%2F%C3%A9",
        http.requests.get(2).query());
  }

  @Test
  void streamsLazily() {
    FakeHttpClient http = threePages();
    Page<Document> page = Support.client(http).documents().list(PARAMS);
    assertEquals(
        List.of("a", "b", "c"),
        page.stream().limit(3).map(Document::getId).collect(Collectors.toList()));
    assertEquals(2, http.requests.size());
  }

  @Test
  void movesOnePageAtATime() {
    FakeHttpClient http = threePages();
    Page<Document> page = Support.client(http).documents().list();
    assertEquals(2, page.getData().size());
    assertTrue(page.hasMore());
    assertEquals("c1", page.getNextCursor());
    assertTrue(page.hasNextPage());
    page = page.nextPage();
    page = page.nextPage();
    assertFalse(page.hasNextPage());
    assertNull(page.nextPage());
    assertEquals("Page{data=<1 items>, hasMore=false, nextCursor=null}", page.toString());
    assertNull(http.requests.get(0).query());
  }

  @Test
  void hasMoreWithoutACursorEnds() {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.json(200, Support.list(doc("a"), true, null)));
    Page<Issuer> page = Support.client(http).issuers().list();
    assertFalse(page.hasNextPage());
    Iterator<Issuer> items = page.iterator();
    items.next();
    assertFalse(items.hasNext());
    assertThrows(NoSuchElementException.class, items::next);
  }

  @Test
  void anEmptyFirstPageStillFollowsTheCursor() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.json(200, Support.list("", true, "c1")))
            .then(Answer.json(200, Support.list(doc("a"), false, null)));
    List<String> ids = new ArrayList<>();
    Support.client(http).events().list().forEach(event -> ids.add(event.getId()));
    assertEquals(List.of("a"), ids);
  }

  @Test
  void refusesAListWithoutDataAndPagination() {
    FakeHttpClient http = new FakeHttpClient().then(Answer.json(200, "{\"data\":[]}"));
    FlashpiException error =
        assertThrows(FlashpiException.class, () -> Support.client(http).documents().list());
    assertEquals("The API answered a list without data and pagination", error.getMessage());
  }

  @Test
  void theFirstPageCarriesItsResponse() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(
                Answer.json(200, Support.list(doc("a"), false, null))
                    .header("x-request-id", "req_list"));
    ApiResponse<Page<Document>> result = Support.client(http).documents().withResponse().list();
    assertEquals("req_list", result.getRequestId());
    assertEquals("a", result.getData().getData().get(0).getId());
  }
}
