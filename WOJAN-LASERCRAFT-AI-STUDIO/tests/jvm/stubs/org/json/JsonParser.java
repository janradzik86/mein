package org.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimalny parser JSON (tylko na potrzeby testów na JVM). */
public class JsonParser {

    private final String s;
    private int i = 0;

    public JsonParser(String s) {
        this.s = s;
    }

    public Object parse() throws JSONException {
        skip();
        Object v = value();
        skip();
        return v;
    }

    private void skip() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() throws JSONException {
        if (i >= s.length()) throw new JSONException("eof");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        return number();
    }

    private JSONObject object() throws JSONException {
        JSONObject obj = new JSONObject();
        Map<String, Object> m = obj.map;
        i++; // {
        skip();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return obj;
        }
        while (true) {
            skip();
            String k = string();
            skip();
            if (i >= s.length() || s.charAt(i) != ':') throw new JSONException("expected :");
            i++;
            skip();
            m.put(k, value());
            skip();
            if (i >= s.length()) throw new JSONException("eof in object");
            char c = s.charAt(i);
            i++;
            if (c == '}') return obj;
            if (c != ',') throw new JSONException("expected , or } got " + c);
        }
    }

    private JSONArray array() throws JSONException {
        JSONArray arr = new JSONArray();
        List<Object> l = arr.items;
        i++; // [
        skip();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return arr;
        }
        while (true) {
            skip();
            l.add(value());
            skip();
            if (i >= s.length()) throw new JSONException("eof in array");
            char c = s.charAt(i);
            i++;
            if (c == ']') return arr;
            if (c != ',') throw new JSONException("expected , or ] got " + c);
        }
    }

    private String string() throws JSONException {
        if (s.charAt(i) != '"') throw new JSONException("expected string at " + i);
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        throw new JSONException("unterminated string");
    }

    private Object number() throws JSONException {
        int st = i;
        if (s.charAt(i) == '-') i++;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.'
                || s.charAt(i) == 'e' || s.charAt(i) == 'E' || s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
        String t = s.substring(st, i);
        if (t.isEmpty()) throw new JSONException("bad number @" + st);
        if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
            try {
                return Integer.valueOf(Integer.parseInt(t));
            } catch (Exception e) {
                return Double.valueOf(Double.parseDouble(t));
            }
        }
        return Double.valueOf(Double.parseDouble(t));
    }

    static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    static String enc(Object v, int indent) {
        if (v == null) return "null";
        if (v instanceof String) return quote((String) v);
        if (v instanceof Boolean) return v.toString();
        if (v instanceof Integer || v instanceof Long) return v.toString();
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d) + ".0";
            return Double.toString(d);
        }
        if (v instanceof JSONObject) return ((JSONObject) v).toString(indent);
        if (v instanceof JSONArray) return ((JSONArray) v).toString(indent);
        return quote(String.valueOf(v));
    }
}
