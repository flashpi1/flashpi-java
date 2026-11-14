package ec.com.flashpi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * A .p12 certificate (firma electrónica) and its password, for {@code issuerOnboardings().create()}
 * and {@code issuers().certificates().upload()}.
 *
 * <pre>{@code
 * CertificateUpload upload = CertificateUpload.builder()
 *     .file(Path.of("firma.p12"))
 *     .password(System.getenv("P12_PASSWORD"))
 *     .build();
 * }</pre>
 *
 * <p>The file is read once, when it is set, so every retry sends the same bytes; the API accepts up
 * to 1 MiB. {@link #toString()} never shows the file or the password.
 */
public final class CertificateUpload {
  /** The largest .p12 the API accepts (it answers 413 {@code payload_too_large} above it). */
  public static final int MAX_BYTES = 1_048_576;

  /** The file name sent when none is given. */
  public static final String DEFAULT_FILENAME = "certificate.p12";

  private final byte[] file;
  private final char[] password;
  private final String filename;

  private CertificateUpload(Builder builder) {
    this.file = builder.file == null ? null : builder.file.clone();
    this.password = builder.password == null ? null : builder.password.clone();
    this.filename = builder.filename;
  }

  /**
   * An upload of a .p12's bytes.
   *
   * @param file the .p12
   * @param password its password
   * @return the upload
   */
  public static CertificateUpload of(byte[] file, String password) {
    return builder().file(file).password(password).build();
  }

  /**
   * A builder with nothing set.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * The .p12, as read (up to one byte past {@link #MAX_BYTES}).
   *
   * @return a copy of its bytes, or null when it was not set
   */
  public byte[] getFile() {
    return file == null ? null : file.clone();
  }

  /**
   * The password of the .p12.
   *
   * @return a copy of it, or null when it was not set
   */
  public char[] getPassword() {
    return password == null ? null : password.clone();
  }

  /**
   * The file name sent with the .p12.
   *
   * @return the name
   */
  public String getFilename() {
    return filename;
  }

  /** The file name and the size of the file; never the file or the password. */
  @Override
  public String toString() {
    return "CertificateUpload{filename="
        + filename
        + ", file="
        + (file == null ? "null" : "<" + file.length + " bytes>")
        + ", password="
        + (password == null ? "null" : "***")
        + "}";
  }

  /** Builds a {@link CertificateUpload}. */
  public static final class Builder {
    private byte[] file;
    private char[] password;
    private String filename = DEFAULT_FILENAME;

    private Builder() {}

    /**
     * The .p12's bytes.
     *
     * @param file the bytes (copied)
     * @return this builder
     */
    public Builder file(byte[] file) {
      this.file = Objects.requireNonNull(file, "file").clone();
      return this;
    }

    /**
     * A .p12 file, read now (up to one byte past the largest the API accepts). Its name becomes the
     * file name sent, unless {@link #filename(String)} sets another.
     *
     * @param path the file
     * @return this builder
     * @throws UncheckedIOException when it cannot be read
     */
    public Builder file(Path path) {
      try (InputStream stream = Files.newInputStream(path)) {
        file(stream);
      } catch (IOException error) {
        throw new UncheckedIOException("Could not read the certificate file " + path, error);
      }
      Path name = path.getFileName();
      if (name != null && DEFAULT_FILENAME.equals(filename)) {
        filename = name.toString();
      }
      return this;
    }

    /**
     * A .p12 from a stream, read now to its end (up to one byte past the largest the API accepts).
     * The stream is not closed.
     *
     * @param stream the stream
     * @return this builder
     * @throws UncheckedIOException when it cannot be read
     */
    public Builder file(InputStream stream) {
      Objects.requireNonNull(stream, "stream");
      byte[] buffer = new byte[MAX_BYTES + 1];
      int size = 0;
      try {
        while (size < buffer.length) {
          int read = stream.read(buffer, size, buffer.length - size);
          if (read < 0) {
            break;
          }
          size += read;
        }
      } catch (IOException error) {
        throw new UncheckedIOException("Could not read the certificate file", error);
      }
      this.file = Arrays.copyOf(buffer, size);
      Arrays.fill(buffer, (byte) 0);
      return this;
    }

    /**
     * The password of the .p12.
     *
     * @param password the password
     * @return this builder
     */
    public Builder password(String password) {
      this.password = Objects.requireNonNull(password, "password").toCharArray();
      return this;
    }

    /**
     * The password of the .p12, for example from {@code Console.readPassword()}.
     *
     * @param password the password (copied; clear yours when done)
     * @return this builder
     */
    public Builder password(char[] password) {
      this.password = Objects.requireNonNull(password, "password").clone();
      return this;
    }

    /**
     * The file name sent with the .p12 (default {@code certificate.p12}).
     *
     * @param filename the name
     * @return this builder
     */
    public Builder filename(String filename) {
      this.filename = Objects.requireNonNull(filename, "filename");
      return this;
    }

    /**
     * Builds the upload. The SDK checks the file and the password when the upload is sent.
     *
     * @return the upload
     */
    public CertificateUpload build() {
      return new CertificateUpload(this);
    }
  }
}
