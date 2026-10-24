package com.doctorexplorer.core.identity.service;

import com.doctorexplorer.core.common.Hashing;
import com.doctorexplorer.core.config.AppSecretProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Extrai do request o que a identidade precisa guardar sem reidentificar: IP pseudonimizado e user agent. */
@Component
public class RequestContext {

    private final byte[] ipKey;

    public RequestContext(AppSecretProperties props) {
        this.ipKey = props.subkey("ip-hash");
    }

    public String ipHash(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return Hashing.hmacHex(ipKey, ip == null ? "" : ip);
    }

    public String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
