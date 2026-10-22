package org.json;
import java.util.ArrayList;
import java.util.List;
public class JSONArray {
    private final List<Object> list = new ArrayList<Object>();
    public JSONArray() { }
    public JSONArray(String raw) { }
    public JSONArray put(Object v) { list.add(v); return this; }
    public int length() { return list.size(); }
    public Object opt(int i) { return i >= 0 && i < list.size() ? list.get(i) : null; }
    public JSONObject optJSONObject(int i) { Object o = opt(i); return o instanceof JSONObject ? (JSONObject) o : null; }
    public String optString(int i) { Object o = opt(i); return o == null ? "" : String.valueOf(o); }
    public int optInt(int i) { Object o = opt(i); return o instanceof Number ? ((Number) o).intValue() : 0; }
    public long optLong(int i) { Object o = opt(i); return o instanceof Number ? ((Number) o).longValue() : 0; }
}
