package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ExifMetadataExtractorTest {

  @Test
  void returnsNullOnNullOrEmpty() {
    assertThat(ExifMetadataExtractor.extract(null)).isNull();
    assertThat(ExifMetadataExtractor.extract(new byte[0])).isNull();
    assertThat(ExifMetadataExtractor.extract(new byte[] {1, 2, 3})).isNull();
  }

  @Test
  void extractsGpsCoordinatesFromJpegWithExif() {
    ExifMetadataExtractor.ExtractedMetadata meta = ExifMetadataExtractor.extract(jpegWithGpsExif());

    assertThat(meta).isNotNull();
    assertThat(meta.latitude()).isEqualByComparingTo(new BigDecimal("10.776889"));
    assertThat(meta.longitude()).isEqualByComparingTo(new BigDecimal("106.700806"));
  }

  private static byte[] jpegWithGpsExif() {
    try {
      ByteArrayOutputStream tiffBytes = new ByteArrayOutputStream();
      // TIFF little-endian header with first IFD at offset 8.
      tiffBytes.write('I');
      tiffBytes.write('I');
      tiffBytes.write(42);
      tiffBytes.write(0);
      writeLe32(tiffBytes, 8);
      // IFD0: one GPSInfo pointer; GPS IFD begins immediately after IFD0.
      writeLe16(tiffBytes, 1);
      writeLe16(tiffBytes, 0x8825);
      writeLe16(tiffBytes, 4);
      writeLe32(tiffBytes, 1);
      int gpsIfdOffset = 8 + 2 + 12 + 4;
      writeLe32(tiffBytes, gpsIfdOffset);
      writeLe32(tiffBytes, 0); // next IFD

      // GPS IFD has four entries: lat ref/value, lon ref/value.
      writeLe16(tiffBytes, 4);
      int gpsDataOffset = gpsIfdOffset + 2 + (4 * 12) + 4;
      writeGpsAsciiEntry(tiffBytes, 1, 'N');
      writeLe16(tiffBytes, 2);
      writeLe16(tiffBytes, 5); // RATIONAL
      writeLe32(tiffBytes, 3);
      writeLe32(tiffBytes, gpsDataOffset);
      writeGpsAsciiEntry(tiffBytes, 3, 'E');
      writeLe16(tiffBytes, 4);
      writeLe16(tiffBytes, 5); // RATIONAL
      writeLe32(tiffBytes, 3);
      writeLe32(tiffBytes, gpsDataOffset + 24);
      writeLe32(tiffBytes, 0); // next IFD

      writeRational(tiffBytes, 10, 1);
      writeRational(tiffBytes, 46, 1);
      writeRational(tiffBytes, 368, 10);
      writeRational(tiffBytes, 106, 1);
      writeRational(tiffBytes, 42, 1);
      writeRational(tiffBytes, 29, 10);

      ByteArrayOutputStream app1 = new ByteArrayOutputStream();
      app1.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
      app1.write(tiffBytes.toByteArray());
      byte[] app1Payload = app1.toByteArray();
      int segmentLength = app1Payload.length + 2;

      ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
      jpeg.write(0xFF);
      jpeg.write(0xD8); // SOI
      jpeg.write(0xFF);
      jpeg.write(0xE1); // APP1
      jpeg.write((segmentLength >>> 8) & 0xFF);
      jpeg.write(segmentLength & 0xFF);
      jpeg.write(app1Payload);
      jpeg.write(0xFF);
      jpeg.write(0xD9); // EOI
      return jpeg.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void writeGpsAsciiEntry(ByteArrayOutputStream out, int tag, char value) {
    writeLe16(out, tag);
    writeLe16(out, 2); // ASCII
    writeLe32(out, 2);
    out.write(value);
    out.write(0);
    out.write(0);
    out.write(0);
  }

  private static void writeRational(ByteArrayOutputStream out, int numerator, int denominator) {
    writeLe32(out, numerator);
    writeLe32(out, denominator);
  }

  private static void writeLe16(ByteArrayOutputStream out, int value) {
    out.write(value & 0xFF);
    out.write((value >>> 8) & 0xFF);
  }

  private static void writeLe32(ByteArrayOutputStream out, int value) {
    out.write(value & 0xFF);
    out.write((value >>> 8) & 0xFF);
    out.write((value >>> 16) & 0xFF);
    out.write((value >>> 24) & 0xFF);
  }
}
