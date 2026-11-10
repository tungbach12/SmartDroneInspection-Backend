package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.spi.SmartDroneHubPort.FlightTracePoint;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FlightLogParser {

  private static final int MAX_INPUT_BYTES = 15 * 1024 * 1024;
  private static final int MAX_RAW_POINTS = 100_000;
  private static final int MAX_POINTS = 500;
  private static final int MAX_RECORD_CHARS = 65_536;
  private static final int MAX_CSV_COLUMNS = 256;
  private static final int MAX_CSV_RECORDS = 250_000;
  private static final int MAX_XML_DEPTH = 64;
  private static final int MAX_XML_ELEMENTS = 500_000;
  private static final int MAX_XML_TEXT = 1_048_576;
  private static final String KML_NAMESPACE = "http://www.opengis.net/kml/2.2";
  private static final String GX_NAMESPACE = "http://www.google.com/kml/ext/2.2";
  private static final String GPX_10_NAMESPACE = "http://www.topografix.com/GPX/1/0";
  private static final String GPX_11_NAMESPACE = "http://www.topografix.com/GPX/1/1";
  private static final Pattern SRT_FIELD =
      Pattern.compile(
          "\\b(latitude|longitude|altitude|rel_alt|abs_alt)\\s*:\\s*([^\\s\\[\\]<>]+)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SRT_GPS =
      Pattern.compile(
          "\\bGPS\\s*\\(\\s*([^,()]+)\\s*,\\s*([^,()]+)\\s*,\\s*([^,()]+)\\s*\\)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SRT_BAROMETER =
      Pattern.compile("\\bBAROMETER\\s*:\\s*([^\\s,<>]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SRT_HEIGHT =
      Pattern.compile("(?:^|[,\\s])H\\s+([-+0-9.eE]+)\\s*m\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern SRT_SPEED =
      Pattern.compile("\\bH\\.S\\s+([-+0-9.eE]+)\\s*(m/s|km/h)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern SRT_OFFSET_TIME =
      Pattern.compile(
          "\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})\\b");

  public List<FlightTracePoint> parseSrt(InputStream input) {
    byte[] bytes = readInput(input);
    List<FlightTracePoint> points = new ArrayList<>();
    try (BufferedReader reader = utf8Reader(bytes)) {
      StringBuilder block = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || line.contains("-->")) {
          addSrtBlock(points, block.toString());
          block.setLength(0);
        }
        if (!line.isBlank()) {
          if (block.length() + line.length() + 1 > MAX_RECORD_CHARS) throw invalidLog();
          block.append(line).append('\n');
        }
      }
      addSrtBlock(points, block.toString());
    } catch (IOException ex) {
      throw invalidLog();
    }
    return downsample(points);
  }

  private static void addSrtBlock(List<FlightTracePoint> points, String block) {
    Map<String, String> fields = new HashMap<>();
    Matcher field = SRT_FIELD.matcher(block);
    while (field.find())
      fields.putIfAbsent(field.group(1).toLowerCase(Locale.ROOT), field.group(2));
    Double lat = number(fields.get("latitude"));
    Double lon = number(fields.get("longitude"));
    Double altitude = null;
    Double speed = null;
    if (fields.containsKey("latitude") || fields.containsKey("longitude")) {
      // Keep the absolute altitude reference when both absolute and takeoff-relative values exist.
      altitude = firstPresentNumber(fields, "abs_alt", "altitude", "rel_alt");
    } else {
      Matcher gps = SRT_GPS.matcher(block);
      if (!gps.find()) return;
      String third = gps.group(3).trim();
      boolean matrice =
          third.toUpperCase(Locale.ROOT).endsWith("M")
              && number(third.substring(0, third.length() - 1)) != null;
      lat = number(gps.group(matrice ? 1 : 2));
      lon = number(gps.group(matrice ? 2 : 1));
      Matcher barometer = SRT_BAROMETER.matcher(block);
      Matcher height = SRT_HEIGHT.matcher(block);
      if (barometer.find()) {
        String value = barometer.group(1);
        altitude = number(value.replaceFirst("[mM]$", ""));
      } else if (height.find()) {
        altitude = number(height.group(1));
      } else if (!matrice && third.endsWith("m")) {
        altitude = number(third.substring(0, third.length() - 1));
      }
      Matcher horizontalSpeed = SRT_SPEED.matcher(block);
      if (horizontalSpeed.find()) {
        Double value = number(horizontalSpeed.group(1));
        if (value != null && value >= 0) {
          speed = horizontalSpeed.group(2).equalsIgnoreCase("km/h") ? value / 3.6 : value;
        }
      }
    }
    Matcher timestamp = SRT_OFFSET_TIME.matcher(block);
    Instant at = timestamp.find() ? offsetTime(timestamp.group()) : null;
    addPoint(points, lat, lon, altitude, speed, null, null, at);
  }

  private static Double firstPresentNumber(Map<String, String> fields, String... names) {
    for (String name : names) {
      if (fields.containsKey(name)) return number(fields.get(name));
    }
    return null;
  }

  public List<FlightTracePoint> parseCsv(InputStream input) {
    byte[] bytes = readInput(input);
    List<FlightTracePoint> points = new ArrayList<>();
    try (CsvReader reader = new CsvReader(utf8Reader(bytes))) {
      List<String> header = reader.readRecord();
      if (header == null) throw invalidLog();
      Map<String, Integer> columns = new HashMap<>();
      for (int i = 0; i < header.size(); i++) {
        String name = normalizeHeader(header.get(i));
        if (!name.isEmpty() && columns.putIfAbsent(name, i) != null) throw invalidLog();
      }
      Integer lat = column(columns, "latitude", "latitude(degrees)", "latitude_deg");
      Integer lon = column(columns, "longitude", "longitude(degrees)", "longitude_deg");
      if (lat == null || lon == null) throw invalidLog();
      NumberColumn altitude =
          numberColumn(
              columns,
              new String[] {"height_above_takeoff(feet)", "height_above_takeoff(ft)"},
              0.3048,
              new String[] {"height_above_takeoff(meters)", "height_above_takeoff(m)"},
              1,
              new String[] {"altitude(m)", "altitude(meters)", "altitude_m"},
              1,
              new String[] {"altitude(feet)", "altitude(ft)", "altitude_ft"},
              0.3048);
      NumberColumn speed =
          numberColumn(
              columns,
              new String[] {"speed(mph)", "ground_speed(mph)", "speed_mph", "ground_speed_mph"},
              0.44704,
              new String[] {"speed(m/s)", "ground_speed(m/s)", "speed_mps", "ground_speed_mps"},
              1,
              new String[] {"speed(km/h)", "ground_speed(km/h)", "speed_kmh", "ground_speed_kmh"},
              1 / 3.6);
      Integer heading = column(columns, "compass_heading(degrees)", "heading_deg");
      Integer battery = column(columns, "battery_percent");
      Integer utc = column(columns, "datetime(utc)");
      Integer time = utc != null ? utc : column(columns, "timestamp", "recorded_at");
      List<String> values;
      int records = 0;
      while ((values = reader.readRecord()) != null) {
        if (++records > MAX_CSV_RECORDS) throw invalidLog();
        Double velocity = readNumber(values, speed);
        if (velocity != null && velocity < 0) velocity = null;
        Double direction = number(text(values, heading));
        if (direction != null && (direction < 0 || direction > 360)) direction = null;
        Double charge = number(text(values, battery));
        Integer batteryPercent =
            charge != null && charge >= 0 && charge <= 100 && charge == Math.rint(charge)
                ? charge.intValue()
                : null;
        String timestamp = text(values, time);
        Instant at = offsetTime(timestamp);
        if (at == null && utc != null) at = explicitUtcTime(timestamp);
        addPoint(
            points,
            number(text(values, lat)),
            number(text(values, lon)),
            readNumber(values, altitude),
            velocity,
            direction,
            batteryPercent,
            at);
      }
    } catch (IOException ex) {
      throw invalidLog();
    }
    return downsample(points);
  }

  public List<FlightTracePoint> parseKml(InputStream input) {
    return parseXml(input, true);
  }

  public List<FlightTracePoint> parseGpx(InputStream input) {
    return parseXml(input, false);
  }

  private static List<FlightTracePoint> parseXml(InputStream input, boolean kml) {
    byte[] bytes = readInput(input);
    List<FlightTracePoint> points = new ArrayList<>();
    try {
      XMLInputFactory factory = XMLInputFactory.newFactory();
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setXMLResolver(
          (publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External references are disabled");
          });
      XMLStreamReader xml = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
      try {
        parseXmlDocument(xml, kml, points);
      } finally {
        xml.close();
      }
    } catch (XMLStreamException | IllegalArgumentException ex) {
      throw invalidLog();
    }
    return downsample(points);
  }

  private static void parseXmlDocument(
      XMLStreamReader xml, boolean kml, List<FlightTracePoint> points) throws XMLStreamException {
    List<String> path = new ArrayList<>();
    int elements = 0;
    int trackDepth = -1;
    List<Coordinate> trackCoordinates = null;
    List<Instant> trackTimes = null;
    int pointDepth = -1;
    Double pointLat = null;
    Double pointLon = null;
    Double pointAltitude = null;
    Instant pointTime = null;
    StringBuilder text = null;
    int textDepth = -1;
    String textKind = null;
    boolean rootSeen = false;
    while (xml.hasNext()) {
      int event = xml.next();
      if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
        throw invalidLog();
      }
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (++elements > MAX_XML_ELEMENTS || path.size() >= MAX_XML_DEPTH) throw invalidLog();
        String local = xml.getLocalName();
        String ns = xml.getNamespaceURI();
        String parent = path.isEmpty() ? "" : path.getLast();
        path.add(local);
        int depth = path.size();
        if (!rootSeen) {
          rootSeen = true;
          if (kml ? !isKml(ns, local, "kml") : !isGpx(ns, local, "gpx")) throw invalidLog();
        }
        if (kml && GX_NAMESPACE.equals(ns) && local.equals("Track")) {
          if (trackDepth != -1) throw invalidLog();
          trackDepth = depth;
          trackCoordinates = new ArrayList<>();
          trackTimes = new ArrayList<>();
        } else if (!kml
            && isGpx(ns, local, "trkpt")
            && parent.equals("trkseg")
            && path.size() >= 4
            && path.get(path.size() - 3).equals("trk")) {
          pointDepth = depth;
          pointLat = number(xml.getAttributeValue(null, "lat"));
          pointLon = number(xml.getAttributeValue(null, "lon"));
          pointAltitude = null;
          pointTime = null;
        } else if (text == null) {
          if (kml && depth == trackDepth + 1 && isKml(ns, local, "when")) {
            textKind = "when";
          } else if (kml
              && depth == trackDepth + 1
              && GX_NAMESPACE.equals(ns)
              && local.equals("coord")) {
            textKind = "coord";
          } else if (kml
              && trackDepth == -1
              && parent.equals("LineString")
              && isKml(ns, local, "coordinates")) {
            textKind = "coordinates";
          } else if (!kml && depth == pointDepth + 1 && isGpx(ns, local, "ele")) {
            textKind = "ele";
          } else if (!kml && depth == pointDepth + 1 && isGpx(ns, local, "time")) {
            textKind = "time";
          }
          if (textKind != null) {
            text = new StringBuilder();
            textDepth = depth;
          }
        }
      } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
          && text != null) {
        if (text.length() + xml.getTextLength() > MAX_XML_TEXT) throw invalidLog();
        text.append(xml.getText());
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        int depth = path.size();
        if (text != null && depth == textDepth) {
          String value = text.toString().trim();
          switch (textKind) {
            case "when" -> {
              if (trackTimes.size() >= MAX_RAW_POINTS) throw invalidLog();
              trackTimes.add(offsetTime(value));
            }
            case "coord" -> {
              if (trackCoordinates.size() >= MAX_RAW_POINTS) throw invalidLog();
              trackCoordinates.add(parseCoordinate(value, false));
            }
            case "coordinates" -> addLineCoordinates(points, value);
            case "ele" -> pointAltitude = number(value);
            case "time" -> pointTime = offsetTime(value);
            default -> throw invalidLog();
          }
          text = null;
          textKind = null;
          textDepth = -1;
        }
        if (kml && depth == trackDepth) {
          boolean aligned = trackTimes.size() == trackCoordinates.size();
          for (int i = 0; i < trackCoordinates.size(); i++) {
            Coordinate c = trackCoordinates.get(i);
            if (c != null)
              addPoint(
                  points,
                  c.lat(),
                  c.lon(),
                  c.altitude(),
                  null,
                  null,
                  null,
                  aligned ? trackTimes.get(i) : null);
          }
          trackDepth = -1;
          trackCoordinates = null;
          trackTimes = null;
        }
        if (!kml && depth == pointDepth) {
          addPoint(points, pointLat, pointLon, pointAltitude, null, null, null, pointTime);
          pointDepth = -1;
        }
        path.removeLast();
      }
    }
    if (!rootSeen) throw invalidLog();
  }

  private static boolean isKml(String ns, String local, String expected) {
    return local.equals(expected) && (ns == null || ns.isEmpty() || KML_NAMESPACE.equals(ns));
  }

  private static boolean isGpx(String ns, String local, String expected) {
    return local.equals(expected)
        && (ns == null
            || ns.isEmpty()
            || GPX_10_NAMESPACE.equals(ns)
            || GPX_11_NAMESPACE.equals(ns));
  }

  private static void addLineCoordinates(List<FlightTracePoint> points, String value) {
    if (value.isEmpty()) return;
    for (String tuple : value.split("\\s+")) {
      Coordinate c = parseCoordinate(tuple, true);
      if (c != null) addPoint(points, c.lat(), c.lon(), c.altitude(), null, null, null, null);
    }
  }

  private static Coordinate parseCoordinate(String value, boolean commaSeparated) {
    if (value.isBlank()) return null;
    String[] parts = value.split(commaSeparated ? "," : "\\s+", -1);
    if (parts.length < 2 || parts.length > 3) return null;
    Double lon = number(parts[0]);
    Double lat = number(parts[1]);
    if (!validCoordinates(lat, lon)) return null;
    return new Coordinate(lat, lon, parts.length == 3 ? number(parts[2]) : null);
  }

  private record Coordinate(Double lat, Double lon, Double altitude) {}

  private record NumberColumn(int index, double factor) {}

  private static NumberColumn numberColumn(Map<String, Integer> columns, Object... alternatives) {
    for (int i = 0; i < alternatives.length; i += 2) {
      Integer index = column(columns, (String[]) alternatives[i]);
      if (index != null)
        return new NumberColumn(index, ((Number) alternatives[i + 1]).doubleValue());
    }
    return null;
  }

  private static Double readNumber(List<String> values, NumberColumn column) {
    if (column == null) return null;
    Double value = number(text(values, column.index()));
    if (value == null) return null;
    double converted = value * column.factor();
    return Double.isFinite(converted) ? converted : null;
  }

  private static Integer column(Map<String, Integer> columns, String... names) {
    for (String name : names) {
      Integer index = columns.get(name);
      if (index != null) return index;
    }
    return null;
  }

  private static String normalizeHeader(String value) {
    return value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
  }

  private static String text(List<String> values, Integer index) {
    if (index == null || index >= values.size()) return null;
    String value = values.get(index).trim();
    return value.isEmpty() ? null : value;
  }

  private static Double number(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      double parsed = Double.parseDouble(value.trim());
      return Double.isFinite(parsed) ? parsed : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Instant offsetTime(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return OffsetDateTime.parse(
              value.trim().replace(' ', 'T'), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
          .toInstant();
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  private static Instant explicitUtcTime(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDateTime.parse(
              value.trim().replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
          .toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  private static byte[] readInput(InputStream input) {
    if (input == null) throw invalidLog();
    try (input) {
      byte[] bytes = input.readNBytes(MAX_INPUT_BYTES + 1);
      if (bytes.length > MAX_INPUT_BYTES) throw invalidLog();
      return bytes;
    } catch (IOException ex) {
      throw invalidLog();
    }
  }

  private static BufferedReader utf8Reader(byte[] bytes) {
    return new BufferedReader(
        new InputStreamReader(
            new ByteArrayInputStream(bytes),
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)));
  }

  private static boolean validCoordinates(Double lat, Double lon) {
    return lat != null && lon != null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
  }

  private static void addPoint(
      List<FlightTracePoint> points,
      Double lat,
      Double lon,
      Double altitude,
      Double speed,
      Double heading,
      Integer battery,
      Instant at) {
    if (!validCoordinates(lat, lon)) return;
    if (points.size() >= MAX_RAW_POINTS) throw invalidLog();
    points.add(new FlightTracePoint(lat, lon, altitude, speed, heading, battery, null, null, at));
  }

  private static List<FlightTracePoint> downsample(List<FlightTracePoint> points) {
    if (points.isEmpty()) throw invalidLog();
    if (points.size() <= MAX_POINTS) return List.copyOf(points);
    List<FlightTracePoint> sampled = new ArrayList<>(MAX_POINTS);
    for (int i = 0; i < MAX_POINTS; i++)
      sampled.add(points.get((int) ((long) i * (points.size() - 1) / (MAX_POINTS - 1))));
    return List.copyOf(sampled);
  }

  private static BusinessException invalidLog() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST,
        "FLIGHT_LOG_EMPTY_OR_INVALID",
        "The uploaded log did not contain a supported GPS flight trace or exceeded parsing limits.");
  }

  private static final class CsvReader implements AutoCloseable {
    private final PushbackReader input;
    private boolean first = true;

    private CsvReader(BufferedReader input) {
      this.input = new PushbackReader(input, 1);
    }

    private List<String> readRecord() throws IOException {
      List<String> values = new ArrayList<>();
      StringBuilder field = new StringBuilder();
      boolean quoted = false;
      boolean afterQuote = false;
      boolean started = false;
      int length = 0;
      int c;
      while ((c = input.read()) != -1) {
        if (first) {
          first = false;
          if (c == '\uFEFF') continue;
        }
        started = true;
        if (++length > MAX_RECORD_CHARS) throw invalidLog();
        if (quoted) {
          if (c == '"') {
            quoted = false;
            afterQuote = true;
          } else {
            field.append((char) c);
          }
        } else if (afterQuote && c == '"') {
          field.append('"');
          quoted = true;
          afterQuote = false;
        } else if (c == ',' || c == '\n' || c == '\r') {
          addField(values, field);
          afterQuote = false;
          if (c != ',') {
            if (c == '\r') {
              int next = input.read();
              if (next != '\n' && next != -1) input.unread(next);
            }
            return values;
          }
        } else if (c == '"' && field.toString().isBlank() && !afterQuote) {
          field.setLength(0);
          quoted = true;
        } else if (afterQuote) {
          if (c != ' ' && c != '\t') throw invalidLog();
        } else if (c == '"') {
          throw invalidLog();
        } else {
          field.append((char) c);
        }
      }
      if (quoted) throw invalidLog();
      if (!started) return null;
      addField(values, field);
      return values;
    }

    private static void addField(List<String> values, StringBuilder field) {
      if (values.size() >= MAX_CSV_COLUMNS) throw invalidLog();
      values.add(field.toString());
      field.setLength(0);
    }

    @Override
    public void close() throws IOException {
      input.close();
    }
  }
}
