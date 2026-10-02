package com.hongmap.hongmapbackend.report.image;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 제보 사진의 메타데이터(촬영 위치 GPS, 기기 모델, 촬영 시각, 텍스트 주석 등)를 서버에서 지운다.
 * 앱도 올리기 전에 GPS 를 지우지만 클라이언트는 믿지 않는다 — 서버가 받은 바이트를 다시 정리해 새 키로 저장한다.
 *
 * <p>픽셀 데이터는 그대로 두고(재인코딩 없음 → 화질 손실·디코더 취약점 노출 없음) 컨테이너 구조만 다시 쓴다.
 * 허용 목록(allowlist) 방식이라 모르는 메타데이터 블록은 모두 버린다.</p>
 *
 * <ul>
 *   <li><b>JPEG</b>: SOI(FFD8FF) 로 시작해야 한다. APP0(JFIF), APP2 ICC_PROFILE(색 프로필), APP14 Adobe(색 변환)만 남기고
 *       APP1(EXIF·XMP), 그 밖의 APPn(MPF·IPTC 등), COM 은 버린다. EOI 뒤에 붙은 데이터(보조 이미지·제조사 트레일러)도 버린다.
 *       EXIF 의 방향(Orientation) 값만 새 최소 EXIF 로 다시 넣는다(빼면 세로 사진이 눕는다).</li>
 *   <li><b>PNG</b>: 8바이트 시그니처(89504E47 0D0A1A0A)로 시작해야 한다. 화면 표시에 필요한 청크
 *       (IHDR, PLTE, IDAT, IEND, tRNS, cHRM, gAMA, iCCP, sBIT, sRGB, bKGD, pHYs)만 남기고
 *       eXIf·tEXt·iTXt·zTXt·tIME 등은 버린다. IEND 뒤 데이터도 버린다.</li>
 * </ul>
 * 형식이 맞지 않거나 구조가 깨져 있으면 {@link InvalidImageException}.
 */
public final class ImageMetadataSanitizer {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final List<String> PNG_KEEP_CHUNKS = List.of(
            "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "cHRM", "gAMA", "iCCP", "sBIT", "sRGB", "bKGD", "pHYs");
    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final byte[] ICC_HEADER = "ICC_PROFILE\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ADOBE_HEADER = "Adobe".getBytes(StandardCharsets.US_ASCII);

    private ImageMetadataSanitizer() {
    }

    public static class InvalidImageException extends RuntimeException {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    /** contentType 은 image/jpeg 또는 image/png. 바이트가 그 형식의 시그니처로 시작하지 않으면 거부한다. */
    public static byte[] sanitize(byte[] bytes, String contentType) {
        if (bytes == null) {
            throw new InvalidImageException("empty");
        }
        return switch (contentType) {
            case "image/jpeg" -> sanitizeJpeg(bytes);
            case "image/png" -> sanitizePng(bytes);
            default -> throw new InvalidImageException("unsupported content type");
        };
    }

    // ---------------------------------------------------------------- JPEG

    static byte[] sanitizeJpeg(byte[] b) {
        int len = b.length;
        if (len < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8 || u8(b, 2) != 0xFF) {
            throw new InvalidImageException("not a JPEG (magic)");
        }
        List<byte[]> leadingApp0 = new ArrayList<>();
        List<byte[]> rest = new ArrayList<>();
        Integer orientation = null;
        int pos = 2;
        while (true) {
            if (pos + 1 >= len || u8(b, pos) != 0xFF) {
                throw new InvalidImageException("JPEG: marker expected at " + pos);
            }
            while (pos + 1 < len && u8(b, pos + 1) == 0xFF) {
                pos++; // fill bytes
            }
            if (pos + 1 >= len) {
                throw new InvalidImageException("JPEG: truncated");
            }
            int marker = u8(b, pos + 1);
            pos += 2;
            if (marker == 0xD9) { // EOI — 뒤에 붙은 데이터는 버린다
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // 길이 없는 마커
                rest.add(new byte[]{(byte) 0xFF, (byte) marker});
                continue;
            }
            if (pos + 2 > len) {
                throw new InvalidImageException("JPEG: truncated segment");
            }
            int segLen = u16be(b, pos);
            if (segLen < 2 || pos + segLen > len) {
                throw new InvalidImageException("JPEG: bad segment length");
            }
            int payloadStart = pos + 2;
            int payloadLen = segLen - 2;
            int segEnd = pos + segLen;

            if (marker == 0xDA) { // SOS: 헤더 뒤 엔트로피 데이터를 다음 마커까지 함께 복사
                int i = segEnd;
                while (true) {
                    if (i + 1 >= len) {
                        throw new InvalidImageException("JPEG: unterminated scan");
                    }
                    if (u8(b, i) == 0xFF) {
                        int next = u8(b, i + 1);
                        if (next == 0x00 || (next >= 0xD0 && next <= 0xD7)) {
                            i += 2;
                            continue;
                        }
                        if (next == 0xFF) {
                            i += 1;
                            continue;
                        }
                        break; // 다음 마커
                    }
                    i++;
                }
                rest.add(slice(b, pos - 2, i));
                pos = i;
                continue;
            }

            boolean keep;
            if (marker == 0xE0) {
                keep = true; // APP0 JFIF/JFXX
            } else if (marker == 0xE1) {
                keep = false; // EXIF·XMP
                if (startsWith(b, payloadStart, payloadLen, EXIF_HEADER)) {
                    Integer o = readExifOrientation(b, payloadStart + EXIF_HEADER.length, payloadLen - EXIF_HEADER.length);
                    if (o != null) {
                        orientation = o;
                    }
                }
            } else if (marker == 0xE2) {
                keep = startsWith(b, payloadStart, payloadLen, ICC_HEADER);
            } else if (marker == 0xEE) {
                keep = startsWith(b, payloadStart, payloadLen, ADOBE_HEADER);
            } else if (marker >= 0xE3 && marker <= 0xEF) {
                keep = false; // 그 밖의 APPn(IPTC/Photoshop, 제조사 블록 등)
            } else if (marker == 0xFE) {
                keep = false; // COM
            } else {
                keep = true; // DQT, DHT, SOFn, DRI, DNL ...
            }
            if (keep) {
                byte[] segment = slice(b, pos - 2, segEnd);
                if (marker == 0xE0 && rest.isEmpty()) {
                    leadingApp0.add(segment);
                } else {
                    rest.add(segment);
                }
            }
            pos = segEnd;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(len);
        out.write(0xFF);
        out.write(0xD8);
        leadingApp0.forEach(out::writeBytes);
        if (orientation != null && orientation != 1) {
            out.writeBytes(minimalExifWithOrientation(orientation));
        }
        rest.forEach(out::writeBytes);
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
    }

    /** TIFF 헤더 + IFD0 에서 Orientation(0x0112) 값만 읽는다. 구조가 이상하면 null(방향 없이 진행). */
    static Integer readExifOrientation(byte[] b, int tiffStart, int tiffLen) {
        if (tiffLen < 8) {
            return null;
        }
        boolean little;
        if (u8(b, tiffStart) == 'I' && u8(b, tiffStart + 1) == 'I') {
            little = true;
        } else if (u8(b, tiffStart) == 'M' && u8(b, tiffStart + 1) == 'M') {
            little = false;
        } else {
            return null;
        }
        if (u16(b, tiffStart + 2, little) != 42) {
            return null;
        }
        long ifd = u32(b, tiffStart + 4, little);
        if (ifd < 8 || ifd + 2 > tiffLen) {
            return null;
        }
        int ifdPos = tiffStart + (int) ifd;
        int count = u16(b, ifdPos, little);
        for (int n = 0; n < count; n++) {
            int entry = ifdPos + 2 + n * 12;
            if (entry + 12 > tiffStart + tiffLen) {
                return null;
            }
            if (u16(b, entry, little) == 0x0112 && u16(b, entry + 2, little) == 3) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : null;
            }
        }
        return null;
    }

    /** "Exif\0\0" + 빅엔디언 TIFF, IFD0 에 Orientation 항목 하나만 있는 APP1 세그먼트. */
    static byte[] minimalExifWithOrientation(int orientation) {
        byte[] payload = {
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,          // TIFF 헤더, IFD0 오프셋 8
                0, 1,                                   // 항목 1개
                0x01, 0x12, 0, 3, 0, 0, 0, 1,           // Orientation, SHORT, count 1
                0, (byte) orientation, 0, 0,            // 값
                0, 0, 0, 0                              // 다음 IFD 없음
        };
        int segLen = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 4);
        out.write(0xFF);
        out.write(0xE1);
        out.write(segLen >> 8);
        out.write(segLen & 0xFF);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- PNG

    static byte[] sanitizePng(byte[] b) {
        int len = b.length;
        if (!startsWith(b, 0, len, PNG_SIGNATURE)) {
            throw new InvalidImageException("not a PNG (magic)");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(len);
        out.writeBytes(PNG_SIGNATURE);
        int pos = PNG_SIGNATURE.length;
        boolean first = true;
        while (true) {
            if (pos + 8 > len) {
                throw new InvalidImageException("PNG: truncated (no IEND)");
            }
            long dataLen = u32(b, pos, false);
            String type = new String(b, pos + 4, 4, StandardCharsets.US_ASCII);
            long chunkEnd = pos + 12L + dataLen;
            if (dataLen > Integer.MAX_VALUE || chunkEnd > len) {
                throw new InvalidImageException("PNG: bad chunk length");
            }
            if (first && !"IHDR".equals(type)) {
                throw new InvalidImageException("PNG: IHDR must be first");
            }
            first = false;
            if (PNG_KEEP_CHUNKS.contains(type)) {
                out.write(b, pos, (int) (chunkEnd - pos));
            }
            pos = (int) chunkEnd;
            if ("IEND".equals(type)) {
                break;
            }
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- helpers

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16be(byte[] b, int i) {
        return (u8(b, i) << 8) | u8(b, i + 1);
    }

    private static int u16(byte[] b, int i, boolean little) {
        return little ? (u8(b, i + 1) << 8) | u8(b, i) : u16be(b, i);
    }

    private static long u32(byte[] b, int i, boolean little) {
        if (little) {
            return ((long) u8(b, i + 3) << 24) | ((long) u8(b, i + 2) << 16) | ((long) u8(b, i + 1) << 8) | u8(b, i);
        }
        return ((long) u8(b, i) << 24) | ((long) u8(b, i + 1) << 16) | ((long) u8(b, i + 2) << 8) | u8(b, i + 3);
    }

    private static boolean startsWith(byte[] b, int start, int available, byte[] prefix) {
        if (available < prefix.length || start + prefix.length > b.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (b[start + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] slice(byte[] b, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(b, from, out, 0, out.length);
        return out;
    }
}
