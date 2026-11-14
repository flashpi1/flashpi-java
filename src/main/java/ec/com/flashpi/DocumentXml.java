package ec.com.flashpi;

import java.util.Objects;

/** The XML of a document and its stage, from {@code documents().xml(id)}. */
public final class DocumentXml {
  /** The stage of the XML, from the {@code Flashpi-Xml-Kind} header. */
  public enum Kind {
    /** The SRI authorized it: the XML to keep and share. */
    AUTHORIZED("authorized"),
    /** Signed, not authorized yet. */
    SIGNED("signed"),
    /** Not signed yet. */
    UNSIGNED("unsigned"),
    /** A stage this version of the SDK does not know. */
    UNKNOWN(null);

    private final String value;

    Kind(String value) {
      this.value = value;
    }

    /**
     * The stage as the header sends it.
     *
     * @return the value, or null for UNKNOWN
     */
    public String value() {
      return value;
    }

    /**
     * The constant of a header value.
     *
     * @param value the header's value
     * @return its constant, UNKNOWN for one this version does not know, or null for null
     */
    public static Kind fromValue(String value) {
      if (value == null) {
        return null;
      }
      for (Kind kind : values()) {
        if (value.equals(kind.value)) {
          return kind;
        }
      }
      return UNKNOWN;
    }
  }

  private final String xml;
  private final Kind kind;

  /**
   * An XML.
   *
   * @param xml the XML
   * @param kind its stage, or null
   */
  public DocumentXml(String xml, Kind kind) {
    this.xml = Objects.requireNonNull(xml, "xml");
    this.kind = kind;
  }

  /**
   * The XML: the authorized one once the SRI authorizes the document, otherwise the signed or
   * unsigned one.
   *
   * @return the XML
   */
  public String getXml() {
    return xml;
  }

  /**
   * The stage of the XML.
   *
   * @return the stage, or null when the API did not say
   */
  public Kind getKind() {
    return kind;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof DocumentXml)) {
      return false;
    }
    DocumentXml document = (DocumentXml) other;
    return xml.equals(document.xml) && kind == document.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(xml, kind);
  }

  @Override
  public String toString() {
    return "DocumentXml{kind=" + kind + ", xml=<" + xml.length() + " characters>}";
  }
}
