package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.ci.CiTriggerRequest;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class CiTriggerService {

    // Basic implementation – callers must supply a fully‑formed URL and any auth token.
    public String trigger(String url, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.getOutputStream().write((new byte[0])); // empty body
        int code = conn.getResponseCode();
        return "CI trigger response: HTTP " + code;
    }
}
