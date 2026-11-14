package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.exceptions.FlashpiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadsTest {
  private static final byte[] P12 = {0x30, (byte) 0x82, 0x01, 0x00, 0x0d, 0x0a, 0x22};

  private static FakeHttpClient.Recorded upload(CertificateUpload upload) {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(202, "{\"id\":\"cert\"}"));
    Support.client(http).issuers().certificates().upload("issuer", upload);
    return http.last();
  }

  @Test
  void sendsThePasswordThenTheFile() {
    FakeHttpClient.Recorded request = upload(CertificateUpload.of(P12, "clave ñ"));
    Matcher boundary =
        Pattern.compile("multipart/form-data; boundary=([0-9a-f]{32})")
            .matcher(request.header("content-type"));
    assertTrue(boundary.matches(), request.header("content-type"));
    String b = boundary.group(1);
    byte[] expected =
        concat(
            ("--"
                    + b
                    + "\r\nContent-Disposition: form-data; name=\"password\"\r\n\r\nclave ñ\r\n--"
                    + b
                    + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"certificate.p12\"\r\n"
                    + "Content-Type: application/x-pkcs12\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8),
            P12,
            ("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
    assertArrayEquals(expected, request.body);
    assertTrue(request.header("idempotency-key") != null);
  }

  @Test
  void everyRetrySendsTheSameBody() {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.status(503)).then(Answer.json(202, "{\"id\":\"o\"}"));
    Support.client(http).issuerOnboardings().create(CertificateUpload.of(P12, "pw"));
    assertArrayEquals(http.requests.get(0).body, http.requests.get(1).body);
    assertEquals(
        http.requests.get(0).header("content-type"), http.requests.get(1).header("content-type"));
  }

  @Test
  void escapesTheFileNameAsHtmlFormsDo() {
    FakeHttpClient.Recorded request =
        upload(
            CertificateUpload.builder()
                .file(P12)
                .password("pw")
                .filename("a\"b\\c\r\nd\u007fé.p12")
                .build());
    assertTrue(
        request.bodyText().contains("filename=\"a%22b\\\\c%0D%0Ad%7Fé.p12\""), request.bodyText());
  }

  @Test
  void readsAFileAndUsesItsName(@TempDir Path folder) throws IOException {
    Path file = folder.resolve("firma.p12");
    Files.write(file, P12);
    FakeHttpClient.Recorded request =
        upload(CertificateUpload.builder().password("pw").file(file).build());
    assertTrue(request.bodyText().contains("filename=\"firma.p12\""));
    CertificateUpload named =
        CertificateUpload.builder().filename("x.p12").file(file).password("p").build();
    assertEquals("x.p12", named.getFilename());
    assertThrows(
        UncheckedIOException.class,
        () -> CertificateUpload.builder().file(folder.resolve("missing.p12")));
  }

  @Test
  void readsAStreamWithoutClosingIt() {
    boolean[] closed = {false};
    InputStream stream =
        new ByteArrayInputStream(P12) {
          @Override
          public void close() {
            closed[0] = true;
          }
        };
    CertificateUpload upload =
        CertificateUpload.builder().file(stream).password("pw".toCharArray()).build();
    assertArrayEquals(P12, upload.getFile());
    assertFalse(closed[0]);
    InputStream failing =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("disk");
          }

          @Override
          public int read(byte[] buffer, int offset, int length) throws IOException {
            throw new IOException("disk");
          }
        };
    assertThrows(UncheckedIOException.class, () -> CertificateUpload.builder().file(failing));
  }

  @Test
  void refusesAnEmptyFileABigOneOrAMissingPasswordBeforeSending() {
    FakeHttpClient http = new FakeHttpClient();
    Flashpi flashpi = Support.client(http);
    FlashpiException empty =
        assertThrows(
            FlashpiException.class,
            () -> flashpi.issuerOnboardings().create(CertificateUpload.of(new byte[0], "pw")));
    assertEquals("The certificate file is empty", empty.getMessage());
    byte[] big = new byte[CertificateUpload.MAX_BYTES + 5];
    FlashpiException tooBig =
        assertThrows(
            FlashpiException.class,
            () ->
                flashpi
                    .issuerOnboardings()
                    .create(
                        CertificateUpload.builder()
                            .file(new ByteArrayInputStream(big))
                            .password("p")
                            .build()));
    assertEquals(
        "The certificate file has more than 1048576 bytes, the most the API accepts",
        tooBig.getMessage());
    assertEquals(
        "The certificate password is missing",
        assertThrows(
                FlashpiException.class,
                () ->
                    flashpi
                        .issuerOnboardings()
                        .create(CertificateUpload.builder().file(P12).build()))
            .getMessage());
    assertEquals(
        "The certificate file is missing",
        assertThrows(
                FlashpiException.class,
                () ->
                    flashpi
                        .issuerOnboardings()
                        .create(CertificateUpload.builder().password("p").build()))
            .getMessage());
    assertEquals(
        "The certificate password has a character that cannot be sent as UTF-8",
        assertThrows(
                FlashpiException.class,
                () -> flashpi.issuerOnboardings().create(CertificateUpload.of(P12, "a\ud800")))
            .getMessage());
    assertTrue(http.requests.isEmpty());
    // Exactly the largest size is sent.
    http.always(Answer.json(202, "{}"));
    flashpi
        .issuerOnboardings()
        .create(CertificateUpload.of(new byte[CertificateUpload.MAX_BYTES], "p"));
  }

  @Test
  void neverShowsTheFileOrThePassword() {
    CertificateUpload upload = CertificateUpload.of(P12, "s3cr3t");
    assertEquals(
        "CertificateUpload{filename=certificate.p12, file=<7 bytes>, password=***}",
        upload.toString());
    assertEquals(
        "CertificateUpload{filename=certificate.p12, file=null, password=null}",
        CertificateUpload.builder().build().toString());
    char[] password = upload.getPassword();
    Arrays.fill(password, 'x');
    assertEquals("s3cr3t", new String(upload.getPassword()));
    assertEquals(
        "Multipart{fields=[password], files=[file]}",
        ec.com.flashpi.internal.Uploads.form(upload).toString());
  }

  private static byte[] concat(byte[]... parts) {
    int size = 0;
    for (byte[] part : parts) {
      size += part.length;
    }
    byte[] all = new byte[size];
    int at = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, all, at, part.length);
      at += part.length;
    }
    return all;
  }
}
