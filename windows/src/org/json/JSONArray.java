package org.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.*;

public class JSONArray implements Iterable<Object> {
    private final ArrayList<Object> myArrayList = new ArrayList<>();

    public JSONArray() {}

    public JSONArray(JSONTokener x) throws JSONException {
        this();
        if (x.nextClean() != '[') {
            throw x.syntaxError("A JSONArray text must start with '['");
        }
        char nextChar = x.nextClean();
        if (nextChar == 0) {
            throw x.syntaxError("Expected a ',' or ']'");
        }
        if (nextChar != ']') {
            x.back();
            for (;;) {
                if (x.nextClean() == ',') {
                    x.back();
                    this.myArrayList.add(JSONObject.NULL);
                } else {
                    x.back();
                    this.myArrayList.add(x.nextValue());
                }
                switch (x.nextClean()) {
                    case 0:
                        throw x.syntaxError("Expected a ',' or ']'");
                    case ',':
                        nextChar = x.nextClean();
                        if (nextChar == 0) {
                            throw x.syntaxError("Expected a ',' or ']'");
                        }
                        if (nextChar == ']') {
                            return;
                        }
                        x.back();
                        break;
                    case ']':
                        return;
                    default:
                        throw x.syntaxError("Expected a ',' or ']'");
                }
            }
        }
    }

    public JSONArray(String source) throws JSONException {
        this(new JSONTokener(source));
    }

    public JSONArray(Collection<?> collection) {
        if (collection != null) {
            this.myArrayList.addAll(collection);
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return this.myArrayList.iterator();
    }

    public Object get(int index) throws JSONException {
        Object object = this.opt(index);
        if (object == null) {
            throw new JSONException("JSONArray[" + index + "] not found.");
        }
        return object;
    }

    public Object opt(int index) {
        return (index < 0 || index >= this.length()) ? null : this.myArrayList.get(index);
    }

    public boolean optBoolean(int index, boolean defaultValue) {
        try { return this.getBoolean(index); } catch (Exception e) { return defaultValue; }
    }

    public boolean getBoolean(int index) throws JSONException {
        Object object = this.get(index);
        if (object.equals(Boolean.FALSE) || (object instanceof String && ((String) object).equalsIgnoreCase("false"))) {
            return false;
        } else if (object.equals(Boolean.TRUE) || (object instanceof String && ((String) object).equalsIgnoreCase("true"))) {
            return true;
        }
        throw new JSONException("JSONArray[" + index + "] is not a boolean.");
    }

    public double optDouble(int index, double defaultValue) {
        try { return this.getDouble(index); } catch (Exception e) { return defaultValue; }
    }

    public double getDouble(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof Number) return ((Number) object).doubleValue();
        try { return Double.parseDouble(object.toString()); } catch (Exception e) {
            throw new JSONException("JSONArray[" + index + "] is not a number.");
        }
    }

    public int optInt(int index, int defaultValue) {
        try { return this.getInt(index); } catch (Exception e) { return defaultValue; }
    }

    public int getInt(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof Number) return ((Number) object).intValue();
        try { return Integer.parseInt(object.toString()); } catch (Exception e) {
            throw new JSONException("JSONArray[" + index + "] is not a number.");
        }
    }

    public long optLong(int index, long defaultValue) {
        try { return this.getLong(index); } catch (Exception e) { return defaultValue; }
    }

    public long getLong(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof Number) return ((Number) object).longValue();
        try { return Long.parseLong(object.toString()); } catch (Exception e) {
            throw new JSONException("JSONArray[" + index + "] is not a number.");
        }
    }

    public String optString(int index, String defaultValue) {
        Object object = this.opt(index);
        return JSONObject.NULL.equals(object) || object == null ? defaultValue : object.toString();
    }

    public String optString(int index) {
        return this.optString(index, "");
    }

    public String getString(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof String) return (String) object;
        return object.toString();
    }

    public JSONObject optJSONObject(int index) {
        Object object = this.opt(index);
        return object instanceof JSONObject ? (JSONObject) object : null;
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof JSONObject) return (JSONObject) object;
        throw new JSONException("JSONArray[" + index + "] is not a JSONObject.");
    }

    public JSONArray optJSONArray(int index) {
        Object object = this.opt(index);
        return object instanceof JSONArray ? (JSONArray) object : null;
    }

    public JSONArray getJSONArray(int index) throws JSONException {
        Object object = this.get(index);
        if (object instanceof JSONArray) return (JSONArray) object;
        throw new JSONException("JSONArray[" + index + "] is not a JSONArray.");
    }

    public boolean isNull(int index) {
        return JSONObject.NULL.equals(this.opt(index));
    }

    public int length() {
        return this.myArrayList.size();
    }

    public JSONArray put(Object value) {
        this.myArrayList.add(value);
        return this;
    }

    public JSONArray put(boolean value) {
        return this.put(Boolean.valueOf(value));
    }

    public JSONArray put(double value) throws JSONException {
        return this.put(Double.valueOf(value));
    }

    public JSONArray put(int value) {
        return this.put(Integer.valueOf(value));
    }

    public JSONArray put(long value) {
        return this.put(Long.valueOf(value));
    }

    public Object remove(int index) {
        return index >= 0 && index < this.length() ? this.myArrayList.remove(index) : null;
    }

    @Override
    public String toString() {
        try {
            return this.toString(0);
        } catch (Exception e) {
            return null;
        }
    }

    public String toString(int indentFactor) throws JSONException {
        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {
            return this.write(sw, indentFactor, 0).toString();
        }
    }

    public Writer write(Writer writer, int indentFactor, int indent) throws JSONException {
        try {
            boolean commanate = false;
            int length = this.length();
            writer.write('[');

            if (length == 1) {
                JSONObject.writeValue(writer, this.myArrayList.get(0), indentFactor, indent);
            } else if (length != 0) {
                final int newIndent = indent + indentFactor;
                for (int i = 0; i < length; i += 1) {
                    if (commanate) writer.write(',');
                    if (indentFactor > 0) writer.write('\n');
                    JSONObject.indent(writer, newIndent);
                    JSONObject.writeValue(writer, this.myArrayList.get(i), indentFactor, newIndent);
                    commanate = true;
                }
                if (indentFactor > 0) writer.write('\n');
                JSONObject.indent(writer, indent);
            }
            writer.write(']');
            return writer;
        } catch (IOException e) {
            throw new JSONException(e);
        }
    }
}
