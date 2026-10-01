package com.cloudsend.app;

import java.nio.charset.StandardCharsets;

public final class q50 {
    private static byte[] b(byte[] bArr, byte[] bArr2) {
        int length = bArr.length;
        int length2 = bArr2.length;
        int i = 0;
        int i2 = 0;
        while (i < length) {
            if (i2 >= length2) {
                i2 = 0;
            }
            bArr[i] = (byte) (bArr[i] ^ bArr2[i2]);
            i++;
            i2++;
        }
        return bArr;
    }

    public String a(byte[] bArr, byte[] bArr2) {
        return new String(b(bArr, bArr2), StandardCharsets.UTF_8);
    }
}
