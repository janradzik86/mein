package org.json;

import java.util.ArrayList;
import java.util.List;

/** Stub testowy JSONArray z serializacją i parsowaniem (podzbiór API Androida). */
public class JSONArray {

    public final List<Object> items = new ArrayList<Object>();

    public JSONArray() {
    }

    public JSONArray(String json) throws JSONException {
        Object o = new JsonParser(json).parse();
        if (!(o instanceof JSONArray)) throw new JSONException("expected array");
        items.addAll(((JSONArray) o).items);
    }

    public JSONArray put(Object v) throws JSONException {
        items.add(v);
        return this;
    }

    public int length() {
        return items.size();
    }

    public Object get(int i) throws JSONException {
        if (i < 0 || i >= items.size()) throw new JSONException("index " + i);
        return items.get(i);
    }

    public JSONObject optJSONObject(int i) {
        Object o = i >= 0 && i < items.size() ? items.get(i) : null;
        return o instanceof JSONObject ? (JSONObject) o : null;
    }

    public JSONArray optJSONArray(int i) {
        Object o = i >= 0 && i < items.size() ? items.get(i) : null;
        return o instanceof JSONArray ? (JSONArray) o : null;
    }

    public String optString(int i, String def) {
        Object o = i >= 0 && i < items.size() ? items.get(i) : null;
        return o == null ? def : String.valueOf(o);
    }

    public String toString() {
        return toString(0);
    }

    public String toString(int indent) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            if (indent > 0) sb.append("\n").append(JsonParser.spaces(indent + 2));
            sb.append(JsonParser.enc(items.get(i), indent + 2));
        }
        if (indent > 0 && !items.isEmpty()) sb.append("\n").append(JsonParser.spaces(indent));
        return sb.append("]").toString();
    }
}
