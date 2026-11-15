package com.smartdroneinspection.inspections.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Decodes, normalizes oversized/rotated evidence photos (max 2048 px long edge), and generates a
 * bounded 480 px thumbnail using JDK {@link ImageIO}.
 */
public final class ImageNormalizer {

  public static final int MAX_PRIMARY_LONG_EDGE = 2048;
  public static final int THUMBNAIL_LONG_EDGE = 480;
  private static final float PRIMARY_JPEG_QUALITY = 0.85f;
  private static final float THUMBNAIL_JPEG_QUALITY = 0.80f;

  private ImageNormalizer() {}

  public static NormalizedImage normalize(byte[] sourceBytes, String contentType)
      throws IOException {
    if (sourceBytes == null || sourceBytes.length == 0) {
      return null;
    }
    BufferedImage decoded;
    try {
      decoded = ImageIO.read(new ByteArrayInputStream(sourceBytes));
    } catch (IOException | RuntimeException ex) {
      return null;
    }
    if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
      return null;
    }

    int orientation =
        "image/jpeg".equalsIgnoreCase(contentType)
            ? ExifMetadataExtractor.extractOrientation(sourceBytes)
            : 1;
    BufferedImage upright = applyExifOrientation(decoded, orientation);

    int width = upright.getWidth();
    int height = upright.getHeight();
    int longEdge = Math.max(width, height);
    boolean hasAlpha = upright.getColorModel().hasAlpha();
    boolean preservePng = hasAlpha && "image/png".equalsIgnoreCase(contentType);

    byte[] primaryBytes;
    String primaryContentType;
    int primaryWidth;
    int primaryHeight;

    if (longEdge <= MAX_PRIMARY_LONG_EDGE && orientation == 1) {
      primaryBytes = sourceBytes;
      primaryContentType = contentType;
      primaryWidth = width;
      primaryHeight = height;
    } else {
      BufferedImage primaryImage =
          longEdge > MAX_PRIMARY_LONG_EDGE
              ? resizeToMaxEdge(upright, MAX_PRIMARY_LONG_EDGE, preservePng)
              : upright;
      primaryWidth = primaryImage.getWidth();
      primaryHeight = primaryImage.getHeight();
      if (preservePng) {
        primaryBytes = encodePng(primaryImage);
        primaryContentType = "image/png";
      } else {
        primaryBytes = encodeJpeg(primaryImage, PRIMARY_JPEG_QUALITY);
        primaryContentType = "image/jpeg";
      }
    }

    BufferedImage thumbImage =
        Math.max(width, height) > THUMBNAIL_LONG_EDGE
            ? resizeToMaxEdge(upright, THUMBNAIL_LONG_EDGE, preservePng)
            : upright;
    byte[] thumbnailBytes;
    String thumbnailContentType;
    if (preservePng) {
      thumbnailBytes = encodePng(thumbImage);
      thumbnailContentType = "image/png";
    } else {
      thumbnailBytes = encodeJpeg(thumbImage, THUMBNAIL_JPEG_QUALITY);
      thumbnailContentType = "image/jpeg";
    }

    return new NormalizedImage(
        primaryBytes,
        primaryContentType,
        primaryWidth,
        primaryHeight,
        thumbnailBytes,
        thumbnailContentType);
  }

  private static BufferedImage resizeToMaxEdge(
      BufferedImage source, int maxEdge, boolean preserveAlpha) {
    int srcW = source.getWidth();
    int srcH = source.getHeight();
    double scale = (double) maxEdge / (double) Math.max(srcW, srcH);
    int targetW = Math.max(1, (int) Math.round(srcW * scale));
    int targetH = Math.max(1, (int) Math.round(srcH * scale));
    int targetType = preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    BufferedImage target = new BufferedImage(targetW, targetH, targetType);
    Graphics2D g2d = target.createGraphics();
    try {
      g2d.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (!preserveAlpha) {
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, targetW, targetH);
      }
      g2d.drawImage(source, 0, 0, targetW, targetH, null);
    } finally {
      g2d.dispose();
    }
    return target;
  }

  private static BufferedImage applyExifOrientation(BufferedImage image, int orientation) {
    if (orientation <= 1 || orientation > 8) {
      return image;
    }
    int w = image.getWidth();
    int h = image.getHeight();
    AffineTransform tx = new AffineTransform();
    int targetW = w;
    int targetH = h;
    switch (orientation) {
      case 2 -> {
        tx.scale(-1.0, 1.0);
        tx.translate(-w, 0);
      }
      case 3 -> {
        tx.translate(w, h);
        tx.rotate(Math.PI);
      }
      case 4 -> {
        tx.scale(1.0, -1.0);
        tx.translate(0, -h);
      }
      case 5 -> {
        tx.rotate(-Math.PI / 2);
        tx.scale(-1.0, 1.0);
        targetW = h;
        targetH = w;
      }
      case 6 -> {
        tx.translate(h, 0);
        tx.rotate(Math.PI / 2);
        targetW = h;
        targetH = w;
      }
      case 7 -> {
        tx.scale(-1.0, 1.0);
        tx.translate(-h, 0);
        tx.translate(0, w);
        tx.rotate(3 * Math.PI / 2);
        targetW = h;
        targetH = w;
      }
      case 8 -> {
        tx.translate(0, w);
        tx.rotate(3 * Math.PI / 2);
        targetW = h;
        targetH = w;
      }
      default -> {
        return image;
      }
    }
    int type =
        image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    BufferedImage dst = new BufferedImage(targetW, targetH, type);
    Graphics2D g2d = dst.createGraphics();
    try {
      g2d.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      if (!image.getColorModel().hasAlpha()) {
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, targetW, targetH);
      }
      g2d.transform(tx);
      g2d.drawImage(image, 0, 0, null);
    } finally {
      g2d.dispose();
    }
    return dst;
  }

  private static byte[] encodePng(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "png", out)) {
      throw new IOException("No PNG ImageIO writer is available");
    }
    return out.toByteArray();
  }

  private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
    BufferedImage rgbImage = toRgb(image);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IOException("No JPEG ImageIO writer is available");
    }
    ImageWriter writer = writers.next();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (param.canWriteCompressed()) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(rgbImage, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  private static BufferedImage toRgb(BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_RGB) {
      return image;
    }
    BufferedImage rgb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = rgb.createGraphics();
    try {
      g2d.setColor(Color.WHITE);
      g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
      g2d.drawImage(image, 0, 0, null);
    } finally {
      g2d.dispose();
    }
    return rgb;
  }

  public record NormalizedImage(
      byte[] primaryBytes,
      String primaryContentType,
      int width,
      int height,
      byte[] thumbnailBytes,
      String thumbnailContentType) {}
}
