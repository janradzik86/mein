package org.json;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stub testowy JSONObject z serializacją i parsowaniem (podzbiór API Androida). */
public class JSONObject {

    public final Map<String, Object> map = new LinkedHashMap<String, Object>();

    public JSONObject() {
    }

    public JSONObject(String json) throws JSONException {
        Object o = new JsonParser(json).parse();
        if (!(o instanceof JSONObject)) throw new JSONException("expected object");
        map.putAll(((JSONObject) o).map);
    }

    public JSONObject put(String k, Object v) throws JSONException {
        map.put(k, v);
        return this;
    }

    public JSONObject put(String k, int v) throws JSONException {
        map.put(k, Integer.valueOf(v));
        return this;
    }

    public JSONObject put(String k, long v) throws JSONException {
        map.put(k, Long.valueOf(v));
        return this;
    }

    public JSONObject put(String k, double v) throws JSONException {
        map.put(k, Double.valueOf(v));
        return this;
    }

    public JSONObject put(String k, boolean v) throws JSONException {
        map.put(k, Boolean.valueOf(v));
        return this;
    }

    public boolean has(String k) {
        return map.containsKey(k);
    }

    public Iterator<String> keys() {
        return map.keySet().iterator();
    }

    public Object opt(String k) {
        return map.get(k);
    }

    public String optString(String k) {
        return optString(k, "");
    }

    public String optString(String k, String def) {
        Object o = map.get(k);
        if (o == null) return def;
        return String.valueOf(o);
    }

    public int optInt(String k, int def) {
        Object o = map.get(k);
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    public double optDouble(String k, double def) {
        Object o = map.get(k);
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim());
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }

    public boolean optBoolean(String k, boolean def) {
        Object o = map.get(k);
        if (o instanceof Boolean) return ((Boolean) o).booleanValue();
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return def;
    }

    public JSONObject optJSONObject(String k) {
        Object o = map.get(k);
        return o instanceof JSONObject ? (JSONObject) o : null;
    }

    public JSONArray optJSONArray(String k) {
        Object o = map.get(k);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }

    public String toString() {
        return toString(0);
    }

    public String toString(int indent) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            if (indent > 0) sb.append("\n").append(JsonParser.spaces(indent + 2));
            sb.append(JsonParser.quote(e.getKey())).append(indent > 0 ? ": " : ":");
            sb.append(JsonParser.enc(e.getValue(), indent + 2));
        }
        if (indent > 0 && !map.isEmpty()) sb.append("\n").append(JsonParser.spaces(indent));
        return sb.append("}").toString();
    }
}
