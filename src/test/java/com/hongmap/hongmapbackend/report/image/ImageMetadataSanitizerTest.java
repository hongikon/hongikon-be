package com.hongmap.hongmapbackend.report.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 테스트용 이미지 생성 도우미는 ReportImageIntegrationTest 도 쓴다. */
public class ImageMetadataSanitizerTest {

    // ------------------------------------------------------------- 테스트용 이미지 생성

    public static byte[] realJpeg() throws IOException {
        BufferedImage image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 16; y++) {
                image.setRGB(x, y, (x * 8) << 16 | (y * 16) << 8 | 0x40);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    public static byte[] realPng() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0xFF00FF00);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** IFD0: Orientation + Make("SECRETCAM") + GPSInfo 포인터 → GPS IFD: 위도 "N" 과 좌표 바이트. 빅엔디언. */
    public static byte[] exifApp1WithGps(int orientation) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        writeBytes(tiff, 'M', 'M', 0, 42, 0, 0, 0, 8);
        // IFD0 @8: 3 entries → 8 + 2 + 36 + 4 = 50
        writeBytes(tiff, 0, 3);
        writeBytes(tiff, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation, 0, 0);       // Orientation
        writeBytes(tiff, 0x01, 0x0F, 0, 2, 0, 0, 0, 10, 0, 0, 0, 80);               // Make → offset 80
        writeBytes(tiff, 0x88, 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 50);                // GPSInfo → offset 50
        writeBytes(tiff, 0, 0, 0, 0);
        // GPS IFD @50: 2 entries → 50 + 2 + 24 + 4 = 80
        writeBytes(tiff, 0, 2);
        writeBytes(tiff, 0, 1, 0, 2, 0, 0, 0, 2, 'N', 0, 0, 0);                     // GPSLatitudeRef
        writeBytes(tiff, 0, 2, 0, 1, 0, 0, 0, 4, 37, 33, 0x2A, 0x77);               // GPSLatitude(가짜)
        writeBytes(tiff, 0, 0, 0, 0);
        tiff.writeBytes("SECRETCAM\0".getBytes(StandardCharsets.US_ASCII));        // @80
        byte[] payload = concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff.toByteArray());
        return segment(0xE1, payload);
    }

    static byte[] segment(int marker, byte[] payload) {
        int len = payload.length + 2;
        return concat(new byte[]{(byte) 0xFF, (byte) marker, (byte) (len >> 8), (byte) len}, payload);
    }

    /** SOI 바로 뒤(APP0 이 있으면 그 뒤)에 세그먼트를 끼워 넣는다. */
    public static byte[] insertAfterApp0(byte[] jpeg, byte[]... segments) {
        int pos = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            pos = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, pos);
        for (byte[] s : segments) {
            out.writeBytes(s);
        }
        out.write(jpeg, pos, jpeg.length - pos);
        return out.toByteArray();
    }

    static byte[] pngChunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long c = crc.getValue();
        int n = data.length;
        return concat(new byte[]{(byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n}, typeBytes, data,
                new byte[]{(byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c});
    }

    /** IHDR 바로 뒤에 청크를 끼워 넣는다. */
    static byte[] insertAfterIhdr(byte[] png, byte[]... chunks) {
        int pos = 8 + 12 + 13;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, pos);
        for (byte[] c : chunks) {
            out.writeBytes(c);
        }
        out.write(png, pos, png.length - pos);
        return out.toByteArray();
    }

    static void writeBytes(ByteArrayOutputStream out, int... values) {
        for (int v : values) {
            out.write(v);
        }
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // ------------------------------------------------------------- JPEG

    @Test
    void JPEG_EXIF_GPS_XMP_COM_보조이미지를_지우고_방향만_남기며_픽셀은_그대로다() throws IOException {
        byte[] clean = realJpeg();
        byte[] xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>SECRETXMP</x:xmpmeta>"
                .getBytes(StandardCharsets.US_ASCII));
        byte[] com = segment(0xFE, "SECRETCOMMENT".getBytes(StandardCharsets.US_ASCII));
        byte[] mpf = segment(0xE2, "MPF\0SECRETMPF".getBytes(StandardCharsets.US_ASCII));
        byte[] icc = segment(0xE2, "ICC_PROFILE\0\1\1KEEPICC".getBytes(StandardCharsets.US_ASCII));
        byte[] dirty = concat(
                insertAfterApp0(clean, exifApp1WithGps(6), xmp, com, mpf, icc),
                "TRAILER-SECRETCAM".getBytes(StandardCharsets.US_ASCII)); // EOI 뒤 데이터

        byte[] out = ImageMetadataSanitizer.sanitize(dirty, "image/jpeg");

        assertThat(contains(out, "SECRETCAM")).isFalse();
        assertThat(contains(out, "SECRETXMP")).isFalse();
        assertThat(contains(out, "SECRETCOMMENT")).isFalse();
        assertThat(contains(out, "SECRETMPF")).isFalse();
        assertThat(contains(out, "TRAILER")).isFalse();
        assertThat(contains(out, "KEEPICC")).isTrue();
        assertThat(out[out.length - 2] & 0xFF).isEqualTo(0xFF);
        assertThat(out[out.length - 1] & 0xFF).isEqualTo(0xD9);

        // 방향(6)만 담은 최소 EXIF 가 남는다 — GPS IFD(0x8825) 는 없다.
        int exifAt = new String(out, StandardCharsets.ISO_8859_1).indexOf("Exif\0\0");
        assertThat(exifAt).isPositive();
        int tiff = exifAt + 6;
        assertThat(ImageMetadataSanitizer.readExifOrientation(out, tiff, out.length - tiff)).isEqualTo(6);
        int segLen = ((out[exifAt - 2] & 0xFF) << 8) | (out[exifAt - 1] & 0xFF);
        assertThat(segLen).isEqualTo(2 + 6 + 26); // IFD0 항목 1개(Orientation)뿐 — GPS IFD 없음

        // 정리 후에도 같은 그림으로 디코딩되고, 원래 깨끗한 JPEG 와 픽셀이 같다.
        BufferedImage before = decode(clean);
        BufferedImage after = decode(out);
        assertThat(after.getWidth()).isEqualTo(32);
        assertThat(after.getHeight()).isEqualTo(16);
        for (int x = 0; x < 32; x += 5) {
            for (int y = 0; y < 16; y += 5) {
                assertThat(after.getRGB(x, y)).isEqualTo(before.getRGB(x, y));
            }
        }
    }

    @Test
    void 방향이_1이거나_EXIF가_없으면_EXIF를_새로_넣지_않는다() throws IOException {
        byte[] clean = realJpeg();
        assertThat(contains(ImageMetadataSanitizer.sanitize(clean, "image/jpeg"), "Exif")).isFalse();
        byte[] upright = insertAfterApp0(clean, exifApp1WithGps(1));
        assertThat(contains(ImageMetadataSanitizer.sanitize(upright, "image/jpeg"), "Exif")).isFalse();
    }

    @Test
    void 이미_깨끗한_JPEG는_그대로다() throws IOException {
        byte[] clean = realJpeg();
        assertThat(ImageMetadataSanitizer.sanitize(clean, "image/jpeg")).isEqualTo(clean);
    }

    @Test
    void 매직바이트가_틀리거나_구조가_깨지면_거부한다() throws IOException {
        byte[] png = realPng();
        byte[] jpeg = realJpeg();
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(png, "image/jpeg"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(jpeg, "image/png"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(
                "<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII), "image/jpeg"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
        byte[] truncated = java.util.Arrays.copyOf(jpeg, jpeg.length / 2);
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(truncated, "image/jpeg"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
        byte[] badLength = insertAfterApp0(jpeg, new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) 0xFF, (byte) 0xFF, 1});
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(
                java.util.Arrays.copyOf(badLength, 40), "image/jpeg"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(jpeg, "image/gif"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
    }

    // ------------------------------------------------------------- PNG

    @Test
    void PNG_eXIf_텍스트_시간_청크와_IEND_뒤_데이터를_지우고_픽셀은_그대로다() throws IOException {
        byte[] clean = realPng();
        byte[] dirty = concat(insertAfterIhdr(clean,
                        pngChunk("eXIf", "MM\0*SECRETCAM".getBytes(StandardCharsets.US_ASCII)),
                        pngChunk("tEXt", "Location\0SECRETTEXT".getBytes(StandardCharsets.US_ASCII)),
                        pngChunk("iTXt", "XML:com.adobe.xmp\0\0\0\0\0SECRETITXT".getBytes(StandardCharsets.US_ASCII)),
                        pngChunk("zTXt", "Comment\0\0SECRETZTXT".getBytes(StandardCharsets.US_ASCII)),
                        pngChunk("tIME", new byte[]{0x07, (byte) 0xEA, 10, 2, 1, 2, 3}),
                        pngChunk("pHYs", new byte[]{0, 0, 0x0B, 0x13, 0, 0, 0x0B, 0x13, 1})),
                "TRAILER-SECRET".getBytes(StandardCharsets.US_ASCII));

        byte[] out = ImageMetadataSanitizer.sanitize(dirty, "image/png");

        for (String secret : new String[]{"eXIf", "tEXt", "iTXt", "zTXt", "tIME", "SECRET"}) {
            assertThat(contains(out, secret)).as(secret).isFalse();
        }
        assertThat(contains(out, "pHYs")).isTrue();
        BufferedImage after = decode(out);
        assertThat(after.getWidth()).isEqualTo(8);
        assertThat(after.getRGB(1, 1)).isEqualTo(decode(clean).getRGB(1, 1));
    }

    @Test
    void PNG_매직바이트가_틀리거나_IEND가_없으면_거부한다() throws IOException {
        byte[] png = realPng();
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(
                java.util.Arrays.copyOf(png, png.length - 12), "image/png"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
        byte[] fakeSig = png.clone();
        fakeSig[1] = 'X';
        assertThatThrownBy(() -> ImageMetadataSanitizer.sanitize(fakeSig, "image/png"))
                .isInstanceOf(ImageMetadataSanitizer.InvalidImageException.class);
        assertThat(ImageMetadataSanitizer.sanitize(png, "image/png")).isEqualTo(png);
    }
}
