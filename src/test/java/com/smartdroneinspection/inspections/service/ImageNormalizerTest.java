package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageNormalizerTest {

  @Test
  void resizesOversizedJpegTo2048AndCreates480Thumbnail() throws Exception {
    byte[] source = createJpegBytes(3200, 1600);

    ImageNormalizer.NormalizedImage normalized = ImageNormalizer.normalize(source, "image/jpeg");

    assertThat(normalized).isNotNull();
    assertThat(normalized.width()).isEqualTo(2048);
    assertThat(normalized.height()).isEqualTo(1024);
    assertThat(normalized.primaryContentType()).isEqualTo("image/jpeg");
    assertThat(normalized.thumbnailContentType()).isEqualTo("image/jpeg");

    BufferedImage primary = ImageIO.read(new ByteArrayInputStream(normalized.primaryBytes()));
    BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(normalized.thumbnailBytes()));
    assertThat(primary.getWidth()).isEqualTo(2048);
    assertThat(primary.getHeight()).isEqualTo(1024);
    assertThat(thumb.getWidth()).isEqualTo(480);
    assertThat(thumb.getHeight()).isEqualTo(240);
  }

  @Test
  void appliesExifOrientationAndResetsDimensionOrderingForRotatedCapture() throws Exception {
    byte[] rawJpeg = createJpegBytes(400, 200);
    byte[] withOrientation6 = prependExifOrientation(rawJpeg, 6);

    assertThat(ExifMetadataExtractor.extractOrientation(withOrientation6)).isEqualTo(6);

    ImageNormalizer.NormalizedImage normalized =
        ImageNormalizer.normalize(withOrientation6, "image/jpeg");

    assertThat(normalized).isNotNull();
    assertThat(normalized.width()).isEqualTo(200);
    assertThat(normalized.height()).isEqualTo(400);
    assertThat(ExifMetadataExtractor.extractOrientation(normalized.primaryBytes())).isEqualTo(1);
    BufferedImage primary = ImageIO.read(new ByteArrayInputStream(normalized.primaryBytes()));
    assertThat(primary.getWidth()).isEqualTo(200);
    assertThat(primary.getHeight()).isEqualTo(400);
  }

  @Test
  void preservesPngAlphaWhenNormalizingOversizedPng() throws Exception {
    BufferedImage image = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(new Color(0, 0, 0, 0));
      g.fillRect(0, 0, 2400, 1200);
      g.setColor(new Color(220, 40, 40, 255));
      g.fillRect(600, 300, 1200, 600);
    } finally {
      g.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);

    ImageNormalizer.NormalizedImage normalized =
        ImageNormalizer.normalize(out.toByteArray(), "image/png");

    assertThat(normalized).isNotNull();
    assertThat(normalized.primaryContentType()).isEqualTo("image/png");
    assertThat(normalized.thumbnailContentType()).isEqualTo("image/png");
    assertThat(normalized.width()).isEqualTo(2048);
    assertThat(normalized.height()).isEqualTo(1024);

    BufferedImage primary = ImageIO.read(new ByteArrayInputStream(normalized.primaryBytes()));
    assertThat((primary.getRGB(5, 5) >>> 24) & 0xFF).isZero();
    BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(normalized.thumbnailBytes()));
    assertThat(thumb.getWidth()).isEqualTo(480);
    assertThat((thumb.getRGB(2, 2) >>> 24) & 0xFF).isZero();
  }

  @Test
  void keepsSmallImagePrimaryBytesIntactWhileCreatingThumbnail() throws Exception {
    BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    byte[] source = out.toByteArray();

    ImageNormalizer.NormalizedImage normalized = ImageNormalizer.normalize(source, "image/png");

    assertThat(normalized).isNotNull();
    assertThat(normalized.primaryBytes()).isSameAs(source);
    assertThat(normalized.width()).isEqualTo(120);
    assertThat(normalized.height()).isEqualTo(80);
    assertThat(normalized.thumbnailBytes()).isNotEmpty();
  }

  @Test
  void returnsNullForCorruptInput() throws Exception {
    assertThat(ImageNormalizer.normalize(new byte[] {1, 2, 3, 4}, "image/jpeg")).isNull();
    assertThat(ImageNormalizer.normalize(new byte[0], "image/png")).isNull();
  }

  private static byte[] createJpegBytes(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(new Color(40, 110, 190));
      g.fillRect(0, 0, width / 2, height);
      g.setColor(new Color(220, 160, 40));
      g.fillRect(width / 2, 0, width / 2, height);
    } finally {
      g.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpeg", out);
    return out.toByteArray();
  }

  private static byte[] prependExifOrientation(byte[] jpegBytes, int orientation) {
    // Minimal little-endian TIFF segment with 1 IFD0 entry: tag 0x0112 (Orientation), type SHORT
    // (3), count 1.
    ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 + 4).order(ByteOrder.LITTLE_ENDIAN);
    tiff.put((byte) 'I').put((byte) 'I');
    tiff.putShort((short) 42);
    tiff.putInt(8);
    tiff.putShort((short) 1);
    tiff.putShort((short) 0x0112);
    tiff.putShort((short) 3);
    tiff.putInt(1);
    tiff.putShort((short) orientation);
    tiff.putShort((short) 0);
    tiff.putInt(0);
    byte[] tiffBytes = tiff.array();

    int app1PayloadLength = 2 + 6 + tiffBytes.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(0xFF);
    out.write(0xD8);
    out.write(0xFF);
    out.write(0xE1);
    out.write((app1PayloadLength >>> 8) & 0xFF);
    out.write(app1PayloadLength & 0xFF);
    out.writeBytes(new byte[] {'E', 'x', 'i', 'f', 0, 0});
    out.writeBytes(tiffBytes);
    out.write(jpegBytes, 2, jpegBytes.length - 2);
    return out.toByteArray();
  }
}
