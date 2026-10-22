package org.json;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
public class JSONObject {
    private final Map<String, Object> map = new LinkedHashMap<String, Object>();
    public JSONObject() { }
    public JSONObject(String raw) { }
    public JSONObject put(String k, Object v) { map.put(k, v); return this; }
    public JSONObject put(String k, int v) { map.put(k, v); return this; }
    public JSONObject put(String k, long v) { map.put(k, v); return this; }
    public JSONObject put(String k, boolean v) { map.put(k, v); return this; }
    public JSONObject put(String k, double v) { map.put(k, v); return this; }
    public Object opt(String k) { return map.get(k); }
    public String optString(String k) { return optString(k, ""); }
    public String optString(String k, String d) { Object o = map.get(k); return o == null ? d : String.valueOf(o); }
    public String getString(String k) { Object o = map.get(k); if (o == null) throw new RuntimeException("no " + k); return String.valueOf(o); }
    public int optInt(String k) { return optInt(k, 0); }
    public int optInt(String k, int d) { Object o = map.get(k); return o instanceof Number ? ((Number) o).intValue() : d; }
    public int getInt(String k) { Object o = map.get(k); if (o == null) throw new RuntimeException("no " + k); return ((Number) o).intValue(); }
    public long optLong(String k) { return optLong(k, 0); }
    public long optLong(String k, long d) { Object o = map.get(k); return o instanceof Number ? ((Number) o).longValue() : d; }
    public double optDouble(String k, double d) { Object o = map.get(k); return o instanceof Number ? ((Number) o).doubleValue() : d; }
    public boolean optBoolean(String k) { return optBoolean(k, false); }
    public boolean optBoolean(String k, boolean d) { Object o = map.get(k); return o instanceof Boolean ? (Boolean) o : d; }
    public boolean getBoolean(String k) { Object o = map.get(k); if (o == null) throw new RuntimeException("no " + k); return (Boolean) o; }
    public JSONObject optJSONObject(String k) { Object o = map.get(k); return o instanceof JSONObject ? (JSONObject) o : null; }
    public JSONObject getJSONObject(String k) { JSONObject o = optJSONObject(k); if (o == null) throw new RuntimeException("no " + k); return o; }
    public JSONArray optJSONArray(String k) { Object o = map.get(k); return o instanceof JSONArray ? (JSONArray) o : null; }
    public JSONArray getJSONArray(String k) { JSONArray a = optJSONArray(k); if (a == null) throw new RuntimeException("no " + k); return a; }
    public boolean has(String k) { return map.containsKey(k); }
    public boolean isNull(String k) { Object o = map.get(k); return o == null; }
    public static final Object NULL = new Object();
    public Iterator<String> keys() { return map.keySet().iterator(); }
    public int length() { return map.size(); }
}
