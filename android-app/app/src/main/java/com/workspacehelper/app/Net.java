package com.workspacehelper.app;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

// Tiny shared HTTP helper used by the background worker and the notification
// action buttons.
public class Net {
    public static String get(String u) { return req("GET", u, null); }
    public static String post(String u, String body) { return req("POST", u, body); }

    public static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    public static String fbUrl(String base, String path, String secret) {
        return base.replaceAll("/+$", "") + "/" + path + ".json?auth=" + enc(secret);
    }

    static String req(String method, String u, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
