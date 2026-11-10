package com.smartdroneinspection.inspections.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartdroneinspection.shared.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class FlightLogParserTest {

  private final FlightLogParser parser = new FlightLogParser();

  @Test
  void parsesDjiSrtVariantsWithoutAssumingTimezone() throws Exception {
    var air2s = parseResource("/flight-logs/dji-air2s.srt", parser::parseSrt);
    var enterprise = parseResource("/flight-logs/dji-matrice-300.srt", parser::parseSrt);
    var legacy = parseResource("/flight-logs/dji-mavic-mini.srt", parser::parseSrt);
    var modern = parseResource("/flight-logs/dji-mavic-3-altitudes.srt", parser::parseSrt);

    assertTrue(air2s.size() > 1);
    assertTrue(enterprise.size() > 1);
    assertTrue(legacy.size() > 1);
    assertTrue(modern.size() > 1);
    assertEquals(48.0771, legacy.getFirst().latitude(), 0.00001);
    assertEquals(-121.7458, legacy.getFirst().longitude(), 0.00001);
    assertEquals(200.7, legacy.getFirst().altitudeMeters(), 0.00001);
    assertEquals(0.3, enterprise.getFirst().altitudeMeters(), 0.00001);
    assertEquals(0.401, modern.getFirst().altitudeMeters(), 0.00001);
    assertNull(air2s.getFirst().recordedAt());
    assertNull(enterprise.getFirst().recordedAt());
    assertNull(modern.getFirst().recordedAt());
  }

  @Test
  void preservesExplicitSrtTimezoneOffsets() {
    String srt =
        "1\n00:00:00,000 --> 00:00:01,000\n"
            + "2026-11-10T12:00:00+02:00\n"
            + "[latitude: 21.0] [longitude: 105.0] [altitude: 10]\n";
    var point = parser.parseSrt(input(srt)).getFirst();
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), point.recordedAt());
  }

  @Test
  void parsesLegacySrtWithMissingDatesWithoutInventingTime() throws Exception {
    var points = parseResource("/flight-logs/dji-broken-incomplete.srt", parser::parseSrt);
    assertTrue(points.size() > 1);
    assertEquals(-34.7383, points.getFirst().latitude(), 0.00001);
    assertEquals(-58.4563, points.getFirst().longitude(), 0.00001);
    assertEquals(20.1, points.getFirst().altitudeMeters(), 0.00001);
    assertNull(points.getFirst().recordedAt());
  }

  @Test
  void parsesQuotedCsvHeadersAndFieldsAndConvertsRecognizedUnits() {
    String csv =
        "\uFEFF\"timestamp\",\"Latitude\",\"Longitude\",\"altitude(ft)\",\"ground_speed(km/h)\",\"heading_deg\",\"battery_percent\",\"note\"\r\n"
            + "\"2026-11-10T12:00:00+02:00\",21.0285,105.8542,10,36,90,98,\"contains, a comma\"\r\n"
            + "\"2026-11-10 10:00:05\",21.0286,105.8543,20,18,95,96,\"escaped \"\"quote\"\"\"\r\n";
    var points = parser.parseCsv(input(csv));

    assertEquals(2, points.size());
    assertEquals(3.048, points.getFirst().altitudeMeters(), 0.0001);
    assertEquals(10, points.getFirst().groundSpeedMps(), 0.0001);
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), points.getFirst().recordedAt());
    assertNull(points.getLast().recordedAt(), "timezone-less generic timestamps are ambiguous");
  }

  @Test
  void parsesAirdataCsvFixture() throws Exception {
    var points = parseResource("/flight-logs/airdata-sample.csv", parser::parseCsv);
    assertEquals(3, points.size());
    assertEquals(10 * 0.3048, points.getFirst().altitudeMeters(), 0.0001);
    assertEquals(4.47 * 0.44704, points.getFirst().groundSpeedMps(), 0.0001);
    assertEquals(90d, points.getFirst().headingDeg());
    assertEquals(98, points.getFirst().batteryPercent());
    assertNotNull(points.getFirst().recordedAt());
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), points.getFirst().recordedAt());
  }

  @Test
  void treatsDjiUtcCsvHeaderAsUtcAndDoesNotInferUnknownUnits() {
    String csv =
        "datetime(utc),latitude,longitude,altitude,Speed,heading_deg,battery_percent\n"
            + "2026-11-10 10:00:00,21.0285,105.8542,100,50,360,100\n";
    var point = parser.parseCsv(input(csv)).getFirst();

    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), point.recordedAt());
    assertNull(point.altitudeMeters(), "unitless altitude is not assumed to be meters");
    assertNull(point.groundSpeedMps(), "unitless speed is not assumed to be m/s");
    assertEquals(360, point.headingDeg());
  }

  @Test
  void rejectsMalformedCsvAndSkipsRowsWithInvalidCoordinates() {
    assertThrows(BusinessException.class, () -> parser.parseCsv(input("x,y\n1,2\n")));
    assertThrows(
        BusinessException.class,
        () -> parser.parseCsv(input("latitude,longitude\nNaN,105\nInfinity,105\n91,105\n")));
    assertThrows(
        BusinessException.class,
        () -> parser.parseCsv(input("latitude,longitude\n\"21.0,105.0\n")));

    String csv =
        "latitude,longitude,speed(m/s),compass_heading(degrees),battery_percent\n"
            + "21.0,105.0,NaN,400,101\n"
            + "91,105,1,90,50\n";
    var point = parser.parseCsv(input(csv)).getFirst();
    assertNull(point.groundSpeedMps());
    assertNull(point.headingDeg());
    assertNull(point.batteryPercent());
  }

  @Test
  void parsesKmlLineStringsAndGxTracksButNotMissionPoints() throws Exception {
    var lines = parseResource("/flight-logs/mission-sample.kml", parser::parseKml);
    var track = parseResource("/flight-logs/kml-gx-track.kml", parser::parseKml);

    assertEquals(3, lines.size());
    assertEquals(5, track.size());
    assertEquals(105.8542, track.getFirst().longitude(), 0.00001);
    assertEquals(21.0285, track.getFirst().latitude(), 0.00001);
    assertEquals(30, track.get(2).altitudeMeters());
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), track.get(2).recordedAt());
    assertEquals(Instant.parse("2026-11-10T08:00:05Z"), track.get(3).recordedAt());
    assertNull(track.getLast().recordedAt());
  }

  @Test
  void parsesGpxOnlyTrackPointsAndHonorsExplicitTimeOffsets() throws Exception {
    var points = parseResource("/flight-logs/mission-sample.gpx", parser::parseGpx);
    assertEquals(3, points.size());
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), points.getFirst().recordedAt());

    String gpx =
        "<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">"
            + "<rte><rtept lat=\"40\" lon=\"50\"/></rte>"
            + "<trk><trkseg><trkpt lat=\"21\" lon=\"105\"><time>2026-11-10T12:00:00+02:00</time></trkpt>"
            + "<trkpt lat=\"21.1\" lon=\"105.1\"/></trkseg></trk></gpx>";
    var custom = parser.parseGpx(input(gpx));
    assertEquals(2, custom.size());
    assertEquals(Instant.parse("2026-11-10T10:00:00Z"), custom.getFirst().recordedAt());
    assertNull(custom.getLast().recordedAt());
  }

  @Test
  void rejectsXmlExternalEntitiesAndOversizedInputs() {
    String xxe =
        "<!DOCTYPE kml [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
            + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Placemark><LineString><coordinates>&xxe;</coordinates>"
            + "</LineString></Placemark></kml>";
    assertThrows(BusinessException.class, () -> parser.parseKml(input(xxe)));
    assertThrows(
        BusinessException.class,
        () -> parser.parseCsv(new ByteArrayInputStream(new byte[15 * 1024 * 1024 + 1])));
  }

  @Test
  void downsamplesLargeTraceWithoutDroppingEndpoints() {
    StringBuilder csv = new StringBuilder("timestamp,latitude,longitude,altitude_m\n");
    for (int i = 0; i < 1200; i++)
      csv.append("2026-11-10T10:00:00Z,21.")
          .append(String.format(Locale.ROOT, "%04d", i))
          .append(",105.8542,30\n");
    var points = parser.parseCsv(input(csv.toString()));
    assertEquals(500, points.size());
    assertTrue(points.getFirst().latitude() < points.getLast().latitude());
    assertFalse(points.isEmpty());
  }

  @Test
  void rejectsLogsWithoutAnyUsableTrace() {
    assertThrows(BusinessException.class, () -> parser.parseSrt(input("No telemetry here\n")));
    assertThrows(BusinessException.class, () -> parser.parseKml(input("<kml/>")));
    assertThrows(BusinessException.class, () -> parser.parseGpx(input("<gpx/>")));
  }

  private static ByteArrayInputStream input(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }

  private static <T> T parseResource(String name, Parser<T> parser) throws Exception {
    try (InputStream input = FlightLogParserTest.class.getResourceAsStream(name)) {
      if (input == null) throw new IllegalStateException("Missing fixture: " + name);
      return parser.parse(input);
    }
  }

  @FunctionalInterface
  private interface Parser<T> {
    T parse(InputStream input);
  }
}
