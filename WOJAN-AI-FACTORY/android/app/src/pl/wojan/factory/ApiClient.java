package pl.wojan.factory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Klient API silnika WOJAN (bez WWW — czysty JSON przez HTTP).
 * Wszystkie żądania niosą nagłówek Authorization: Bearer &lt;token&gt;.
 */
public final class ApiClient {
    public String server;
    public String token;

    public ApiClient(String server, String token) {
        this.server = server.endsWith("/") ? server.substring(0, server.length() - 1) : server;
        this.token = token;
    }

    public static class ApiException extends Exception {
        public final int code;
        public ApiException(int code, String msg) { super(msg); this.code = code; }
    }

    public JSONObject get(String path) throws Exception {
        return request("GET", path, null);
    }

    public JSONObject post(String path, JSONObject body) throws Exception {
        return request("POST", path, body);
    }

    public JSONObject request(String method, String path, JSONObject body) throws Exception {
        if (server == null || server.length() == 0) {
            throw new ApiException(0, "Nie ustawiono adresu serwera");
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(server + path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("X-Wojan-Client", "android/" + BuildInfo.VERSION);
        if (body != null) {
            conn.setDoOutput(true);
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.close();
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        conn.disconnect();
        if (code == 401) throw new ApiException(401, "Odrzucono token — sprawdź hasło połączenia");
        if (code >= 400) throw new ApiException(code, "Błąd serwera " + code + ": " + sb);
        String s = sb.toString().trim();
        if (s.length() == 0) return new JSONObject();
        return new JSONObject(s);
    }

    /** Pobiera plik binarny (artefakt APK/EXE) do bufora. */
    public byte[] download(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(server + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        int code = conn.getResponseCode();
        if (code >= 400) {
            conn.disconnect();
            throw new ApiException(code, "Nie udało się pobrać pliku (" + code + ")");
        }
        InputStream is = conn.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        conn.disconnect();
        return bos.toByteArray();
    }

    public static String optString(JSONObject o, String key, String def) {
        if (o == null || !o.has(key) || o.isNull(key)) return def;
        return o.optString(key, def);
    }

    public static JSONArray optArray(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return new JSONArray();
        return o.optJSONArray(key);
    }
}
