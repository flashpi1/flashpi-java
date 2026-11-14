package ec.com.flashpi.internal;

import ec.com.flashpi.CertificateUpload;
import ec.com.flashpi.exceptions.FlashpiException;
import java.util.Arrays;

/**
 * The multipart body of a .p12 upload, for {@code issuers().certificates().upload()} and {@code
 * issuerOnboardings().create()}.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Uploads {
  /** The content type of the file part. */
  static final String CONTENT_TYPE = "application/x-pkcs12";

  private Uploads() {}

  /**
   * The body of an upload: one {@code password} field and one {@code file} part. Checked before
   * anything is sent.
   *
   * @param upload the upload
   * @return the body
   * @throws FlashpiException for a missing password, or a file that is missing, empty or over 1 MiB
   */
  public static Multipart form(CertificateUpload upload) {
    if (upload == null) {
      throw new NullPointerException("upload");
    }
    char[] password = upload.getPassword();
    byte[] file = upload.getFile();
    try {
      if (password == null) {
        throw new FlashpiException("The certificate password is missing");
      }
      if (file == null) {
        throw new FlashpiException("The certificate file is missing");
      }
      if (file.length == 0) {
        throw new FlashpiException("The certificate file is empty");
      }
      if (file.length > CertificateUpload.MAX_BYTES) {
        throw new FlashpiException(
            "The certificate file has more than "
                + CertificateUpload.MAX_BYTES
                + " bytes, the most the API accepts");
      }
      return Multipart.certificate(password, file, upload.getFilename(), CONTENT_TYPE);
    } finally {
      if (password != null) {
        Arrays.fill(password, '\0');
      }
      if (file != null) {
        Arrays.fill(file, (byte) 0);
      }
    }
  }
}
