package ec.com.flashpi;

/** The language of the API's messages. Error codes do not change with it. */
public enum Language {
  /** English, the default. */
  EN("en"),
  /** Spanish. */
  ES("es");

  private final String value;

  Language(String value) {
    this.value = value;
  }

  /**
   * The language as the Accept-Language header sends it.
   *
   * @return {@code en} or {@code es}
   */
  public String value() {
    return value;
  }
}
