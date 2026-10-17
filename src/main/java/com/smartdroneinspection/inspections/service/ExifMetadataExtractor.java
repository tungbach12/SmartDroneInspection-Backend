package com.smartdroneinspection.inspections.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Pure Java extractor for EXIF GPS and capture timestamp from JPEG image bytes. Requires no
 * external image processing dependencies.
 */
public final class ExifMetadataExtractor {

  private static final DateTimeFormatter EXIF_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

  public record ExtractedMetadata(BigDecimal latitude, BigDecimal longitude, Instant captureTime) {}

  private ExifMetadataExtractor() {}

  public static ExtractedMetadata extract(byte[] data) {
    if (data == null || data.length < 12) {
      return null;
    }
    // Check JPEG SOI marker (0xFFD8)
    if ((data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
      return null;
    }

    try {
      int offset = 2;
      while (offset < data.length - 4) {
        if ((data[offset] & 0xFF) != 0xFF) {
          break;
        }
        int marker = data[offset + 1] & 0xFF;
        offset += 2;

        // SOS (0xDA) or EOI (0xD9) markers indicate end of headers
        if (marker == 0xDA || marker == 0xD9) {
          break;
        }

        int length = readUint16BE(data, offset);
        if (length < 2 || offset + length > data.length) {
          break;
        }

        if (marker == 0xE1) { // APP1 marker (EXIF)
          if (offset + 8 <= data.length) {
            String header = new String(data, offset + 2, 4, StandardCharsets.US_ASCII);
            if ("Exif".equals(header) && data[offset + 6] == 0 && data[offset + 7] == 0) {
              return parseTiff(data, offset + 8, length - 8);
            }
          }
        }
        offset += length;
      }
    } catch (Exception ignored) {
      // Malformed or truncated EXIF headers should fail silently and return null
    }

    return null;
  }

  private static ExtractedMetadata parseTiff(byte[] data, int tiffBase, int tiffLength) {
    if (tiffLength < 8 || tiffBase + tiffLength > data.length) {
      return null;
    }

    boolean littleEndian;
    if (data[tiffBase] == 'I' && data[tiffBase + 1] == 'I') {
      littleEndian = true;
    } else if (data[tiffBase] == 'M' && data[tiffBase + 1] == 'M') {
      littleEndian = false;
    } else {
      return null;
    }

    int magic = readUint16(data, tiffBase + 2, littleEndian);
    if (magic != 42) {
      return null;
    }

    long ifd0Offset = readUint32(data, tiffBase + 4, littleEndian);
    if (ifd0Offset < 8 || ifd0Offset >= tiffLength) {
      return null;
    }

    int ifd0Pos = (int) (tiffBase + ifd0Offset);
    if (ifd0Pos + 2 > tiffBase + tiffLength) {
      return null;
    }

    int numEntries = readUint16(data, ifd0Pos, littleEndian);
    int gpsIfdOffset = -1;
    Instant captureTime = null;

    for (int i = 0; i < numEntries; i++) {
      int entryPos = ifd0Pos + 2 + (i * 12);
      if (entryPos + 12 > tiffBase + tiffLength) {
        break;
      }
      int tag = readUint16(data, entryPos, littleEndian);
      if (tag == 0x8825) { // GPSInfo IFD pointer
        gpsIfdOffset = (int) readUint32(data, entryPos + 8, littleEndian);
      } else if (tag == 0x0132) { // DateTime
        long valOffset = readUint32(data, entryPos + 8, littleEndian);
        captureTime = parseExifDate(data, tiffBase, valOffset, tiffLength);
      }
    }

    if (gpsIfdOffset < 0 || gpsIfdOffset >= tiffLength) {
      return captureTime != null ? new ExtractedMetadata(null, null, captureTime) : null;
    }

    int gpsIfdPos = tiffBase + gpsIfdOffset;
    if (gpsIfdPos + 2 > tiffBase + tiffLength) {
      return captureTime != null ? new ExtractedMetadata(null, null, captureTime) : null;
    }

    int numGpsEntries = readUint16(data, gpsIfdPos, littleEndian);
    String latRef = null;
    double[] latValues = null;
    String lonRef = null;
    double[] lonValues = null;

    for (int i = 0; i < numGpsEntries; i++) {
      int entryPos = gpsIfdPos + 2 + (i * 12);
      if (entryPos + 12 > tiffBase + tiffLength) {
        break;
      }
      int tag = readUint16(data, entryPos, littleEndian);
      long valueOrOffset = readUint32(data, entryPos + 8, littleEndian);

      if (tag == 1) { // GPSLatitudeRef
        latRef = new String(data, entryPos + 8, 1, StandardCharsets.US_ASCII);
      } else if (tag == 2) { // GPSLatitude (3 RATIONALs)
        latValues = readRationals(data, tiffBase, (int) valueOrOffset, 3, littleEndian, tiffLength);
      } else if (tag == 3) { // GPSLongitudeRef
        lonRef = new String(data, entryPos + 8, 1, StandardCharsets.US_ASCII);
      } else if (tag == 4) { // GPSLongitude (3 RATIONALs)
        lonValues = readRationals(data, tiffBase, (int) valueOrOffset, 3, littleEndian, tiffLength);
      }
    }

    BigDecimal latitude = null;
    BigDecimal longitude = null;

    if (latValues != null && latValues.length == 3) {
      double lat = latValues[0] + (latValues[1] / 60.0) + (latValues[2] / 3600.0);
      if ("S".equalsIgnoreCase(latRef)) {
        lat = -lat;
      }
      if (lat >= -90.0 && lat <= 90.0) {
        latitude = BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP);
      }
    }

    if (lonValues != null && lonValues.length == 3) {
      double lon = lonValues[0] + (lonValues[1] / 60.0) + (lonValues[2] / 3600.0);
      if ("W".equalsIgnoreCase(lonRef)) {
        lon = -lon;
      }
      if (lon >= -180.0 && lon <= 180.0) {
        longitude = BigDecimal.valueOf(lon).setScale(6, RoundingMode.HALF_UP);
      }
    }

    if (latitude != null && longitude != null) {
      return new ExtractedMetadata(latitude, longitude, captureTime);
    }

    return captureTime != null ? new ExtractedMetadata(null, null, captureTime) : null;
  }

  private static double[] readRationals(
      byte[] data, int tiffBase, int offset, int count, boolean littleEndian, int tiffLength) {
    if (offset < 0 || offset + (count * 8) > tiffLength) {
      return null;
    }
    double[] result = new double[count];
    for (int i = 0; i < count; i++) {
      int pos = tiffBase + offset + (i * 8);
      long numerator = readUint32(data, pos, littleEndian);
      long denominator = readUint32(data, pos + 4, littleEndian);
      result[i] = denominator == 0 ? 0 : (double) numerator / (double) denominator;
    }
    return result;
  }

  private static Instant parseExifDate(byte[] data, int tiffBase, long offset, int tiffLength) {
    if (offset < 0 || offset + 19 > tiffLength) {
      return null;
    }
    try {
      String dateStr = new String(data, (int) (tiffBase + offset), 19, StandardCharsets.US_ASCII);
      LocalDateTime ldt = LocalDateTime.parse(dateStr, EXIF_DATE_TIME);
      return ldt.toInstant(ZoneOffset.UTC);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static int readUint16BE(byte[] data, int pos) {
    return ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
  }

  private static int readUint16(byte[] data, int pos, boolean littleEndian) {
    if (littleEndian) {
      return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
    } else {
      return ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
    }
  }

  private static long readUint32(byte[] data, int pos, boolean littleEndian) {
    if (littleEndian) {
      return ((long) (data[pos] & 0xFF))
          | (((long) (data[pos + 1] & 0xFF)) << 8)
          | (((long) (data[pos + 2] & 0xFF)) << 16)
          | (((long) (data[pos + 3] & 0xFF)) << 24);
    } else {
      return (((long) (data[pos] & 0xFF)) << 24)
          | (((long) (data[pos + 1] & 0xFF)) << 16)
          | (((long) (data[pos + 2] & 0xFF)) << 8)
          | ((long) (data[pos + 3] & 0xFF));
    }
  }
}
