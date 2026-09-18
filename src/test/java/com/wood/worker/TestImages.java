package com.wood.worker;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates real JPEG bytes for tests, optionally tagged with an Exif orientation
 * so the downscaling path (and its orientation handling) can be exercised.
 */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] jpegWithOrientation(int width, int height, int orientation) {
        return withExifOrientation(jpeg(width, height), orientation);
    }

    public static Path writeJpeg(Path file, int width, int height) {
        try {
            Files.write(file, jpeg(width, height));
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Inserts a minimal Exif APP1 segment (with only an Orientation tag) directly after the
     * JPEG SOI marker, mirroring the "Exif first" layout found in camera originals.
     */
    static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = new byte[] {
                0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // "II", 42, IFD0 offset 8
                0x01, 0x00,                                        // one IFD entry
                0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,    // Orientation, SHORT, count 1
                (byte) orientation, 0x00, 0x00, 0x00,              // value
                0x00, 0x00, 0x00, 0x00,                            // no next IFD
        };
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(0xFF);
            out.write(0xD8);                                            // SOI
            out.write(0xFF);
            out.write(0xE1);                                           // APP1
            int segmentLength = 2 + 6 + tiff.length;
            out.write((segmentLength >> 8) & 0xFF);
            out.write(segmentLength & 0xFF);
            out.write("Exif".getBytes(StandardCharsets.US_ASCII));
            out.write(0x00);
            out.write(0x00);
            out.write(tiff);
            out.write(sliceAfterSoi(jpeg));
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] sliceAfterSoi(byte[] jpeg) {
        byte[] rest = new byte[jpeg.length - 2];
        System.arraycopy(jpeg, 2, rest, 0, rest.length);
        return rest;
    }
}
