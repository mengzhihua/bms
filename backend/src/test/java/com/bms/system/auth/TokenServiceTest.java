package com.bms.system.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    private final TokenService svc = new TokenService("secret-a", Duration.ofHours(1));

    @Test
    void roundTrip() {
        String token = svc.issue(7L, "admin");
        TokenService.Principal p = svc.parse(token);
        assertNotNull(p);
        assertEquals(7L, p.getUserId());
        assertEquals("admin", p.getUsername());
        assertTrue(p.getExpiresAt() > System.currentTimeMillis());
    }

    @Test
    void expiredTokenRejected() {
        String token = svc.issue(7L, "admin", System.currentTimeMillis() - 1);
        assertNull(svc.parse(token));
    }

    @Test
    void tamperedOrForeignTokenRejected() {
        String token = svc.issue(7L, "admin");
        assertNull(svc.parse(token.substring(0, token.length() - 2) + "xx"));
        assertNull(svc.parse("garbage"));
        assertNull(svc.parse(null));
        TokenService other = new TokenService("secret-b", Duration.ofHours(1));
        assertNull(other.parse(token));
    }

    @Test
    void malformedBodyRejectedWithoutException() {
        // 签名有效但正文非预期格式（非数字 id / 非 base64）应返回 null 而非抛异常
        assertNull(svc.parse(signedRaw("abc:admin:999999999999999")));
        assertNull(svc.parse(signedRaw("7:admin:notanumber")));
        assertNull(svc.parse(signedRaw("7:admin")));
    }

    /** 用同一密钥为任意正文生成合法签名的令牌 */
    private static String signedRaw(String plain) {
        try {
            Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
            String body = enc.encodeToString(plain.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("secret-a".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return body + "." + enc.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
