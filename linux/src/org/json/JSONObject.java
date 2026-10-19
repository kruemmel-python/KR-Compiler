package org.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public class JSONObject {
    public static final Object NULL = new Null();

    private static final class Null {
        @Override
        protected final Object clone() { return this; }
        @Override
        public boolean equals(Object object) { return object == null || object == this; }
        @Override
        public int hashCode() { return 0; }
        @Override
        public String toString() { return "null"; }
    }

    private final Map<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {}

    public JSONObject(JSONTokener x) throws JSONException {
        this();
        char c;
        String key;

        if (x.nextClean() != '{') {
            throw x.syntaxError("A JSONObject text must begin with '{'");
        }
        for (;;) {
            c = x.nextClean();
            switch (c) {
                case 0:
                    throw x.syntaxError("A JSONObject text must end with '}'");
                case '}':
                    return;
                default:
                    x.back();
                    key = x.nextValue().toString();
            }

            c = x.nextClean();
            if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key");
            }
            if (key != null) {
                if (this.opt(key) != null) {
                    // Duplicate key, overwrite
                }
                Object value = x.nextValue();
                if (value != null) {
                    this.put(key, value);
                }
            }

            switch (x.nextClean()) {
                case ';':
                case ',':
                    if (x.nextClean() == '}') {
                        return;
                    }
                    x.back();
                    break;
                case '}':
                    return;
                default:
                    throw x.syntaxError("Expected a ',' or '}'");
            }
        }
    }

    public JSONObject(String source) throws JSONException {
        this(new JSONTokener(source));
    }

    public JSONObject(Map<?, ?> map) {
        if (map != null) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    this.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
    }

    public JSONObject put(String key, Object value) throws JSONException {
        if (key == null) throw new NullPointerException("Null key.");
        if (value != null) {
            this.map.put(key, value);
        } else {
            this.remove(key);
        }
        return this;
    }

    public JSONObject put(String key, boolean value) throws JSONException {
        return this.put(key, Boolean.valueOf(value));
    }

    public JSONObject put(String key, double value) throws JSONException {
        return this.put(key, Double.valueOf(value));
    }

    public JSONObject put(String key, int value) throws JSONException {
        return this.put(key, Integer.valueOf(value));
    }

    public JSONObject put(String key, long value) throws JSONException {
        return this.put(key, Long.valueOf(value));
    }

    public Object opt(String key) {
        return key == null ? null : this.map.get(key);
    }

    public Object get(String key) throws JSONException {
        if (key == null) throw new JSONException("Null key.");
        Object result = this.opt(key);
        if (result == null) throw new JSONException("JSONObject[" + quote(key) + "] not found.");
        return result;
    }

    public boolean optBoolean(String key, boolean defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) return defaultValue;
        if (val instanceof Boolean) return ((Boolean) val).booleanValue();
        try { return this.getBoolean(key); } catch (Exception e) { return defaultValue; }
    }

    public boolean getBoolean(String key) throws JSONException {
        Object object = this.get(key);
        if (object.equals(Boolean.FALSE) || (object instanceof String && ((String) object).equalsIgnoreCase("false"))) {
            return false;
        } else if (object.equals(Boolean.TRUE) || (object instanceof String && ((String) object).equalsIgnoreCase("true"))) {
            return true;
        }
        throw new JSONException("JSONObject[" + quote(key) + "] is not a Boolean.");
    }

    public int optInt(String key, int defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) return defaultValue;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return defaultValue; }
    }

    public int getInt(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof Number) return ((Number) object).intValue();
        try { return Integer.parseInt(object.toString()); } catch (Exception e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not an int.");
        }
    }

    public long optLong(String key, long defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) return defaultValue;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return defaultValue; }
    }

    public long getLong(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof Number) return ((Number) object).longValue();
        try { return Long.parseLong(object.toString()); } catch (Exception e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not a long.");
        }
    }

    public double optDouble(String key, double defaultValue) {
        Object val = this.opt(key);
        if (NULL.equals(val)) return defaultValue;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return defaultValue; }
    }

    public double getDouble(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof Number) return ((Number) object).doubleValue();
        try { return Double.parseDouble(object.toString()); } catch (Exception e) {
            throw new JSONException("JSONObject[" + quote(key) + "] is not a double.");
        }
    }

    public String optString(String key, String defaultValue) {
        Object object = this.opt(key);
        return NULL.equals(object) || object == null ? defaultValue : object.toString();
    }

    public String optString(String key) {
        return this.optString(key, "");
    }

    public String getString(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof String) return (String) object;
        return object.toString();
    }

    public JSONObject optJSONObject(String key) {
        Object object = this.opt(key);
        return object instanceof JSONObject ? (JSONObject) object : null;
    }

    public JSONObject getJSONObject(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof JSONObject) return (JSONObject) object;
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONObject.");
    }

    public JSONArray optJSONArray(String key) {
        Object object = this.opt(key);
        return object instanceof JSONArray ? (JSONArray) object : null;
    }

    public JSONArray getJSONArray(String key) throws JSONException {
        Object object = this.get(key);
        if (object instanceof JSONArray) return (JSONArray) object;
        throw new JSONException("JSONObject[" + quote(key) + "] is not a JSONArray.");
    }

    public boolean has(String key) {
        return this.map.containsKey(key);
    }

    public boolean isNull(String key) {
        return JSONObject.NULL.equals(this.opt(key));
    }

    public int length() {
        return this.map.size();
    }

    public Iterator<String> keys() {
        return this.keySet().iterator();
    }

    public Set<String> keySet() {
        return this.map.keySet();
    }

    public Object remove(String key) {
        return this.map.remove(key);
    }

    public static String quote(String string) {
        StringWriter sw = new StringWriter();
        synchronized (sw.getBuffer()) {
            try {
                return quote(string, sw).toString();
            } catch (IOException ignored) {
                return "";
            }
        }
    }

    public static Writer quote(String string, Writer w) throws IOException {
        if (string == null || string.isEmpty()) {
            w.write("\"\"");
            return w;
        }

        char b;
        char c = 0;
        String hhhh;
        int i;
        int len = string.length();

        w.write('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    w.write('\\');
                    w.write(c);
                    break;
                case '/':
                    if (b == '<') w.write('\\');
                    w.write(c);
                    break;
                case '\b': w.write("\\b"); break;
                case '\t': w.write("\\t"); break;
                case '\n': w.write("\\n"); break;
                case '\f': w.write("\\f"); break;
                case '\r': w.write("\\r"); break;
                default:
                    if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {
                        w.write("\\u");
                        hhhh = Integer.toHexString(c);
                        w.write("0000", 0, 4 - hhhh.length());
                        w.write(hhhh);
                    } else {
                        w.write(c);
                    }
            }
        }
        w.write('"');
        return w;
    }

    public static Object stringToValue(String string) {
        if ("".equals(string)) return string;
        if ("true".equalsIgnoreCase(string)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(string)) return Boolean.FALSE;
        if ("null".equalsIgnoreCase(string)) return JSONObject.NULL;

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                if (string.indexOf('.') > -1 || string.indexOf('e') > -1 || string.indexOf('E') > -1 || "-0".equals(string)) {
                    Double d = Double.valueOf(string);
                    if (!d.isInfinite() && !d.isNaN()) return d;
                } else {
                    Long val = Long.valueOf(string);
                    if (string.equals(val.toString())) {
                        if (val.longValue() == val.intValue()) return Integer.valueOf(val.intValue());
                        return val;
                    }
                }
            } catch (Exception ignored) {}
        }
        return string;
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
        StringWriter w = new StringWriter();
        synchronized (w.getBuffer()) {
            return this.write(w, indentFactor, 0).toString();
        }
    }

    public Writer write(Writer writer, int indentFactor, int indent) throws JSONException {
        try {
            boolean commanate = false;
            final int length = this.length();
            writer.write('{');

            if (length == 1) {
                Map.Entry<String, ?> entry = this.map.entrySet().iterator().next();
                String key = entry.getKey();
                writer.write(quote(key));
                writer.write(':');
                if (indentFactor > 0) writer.write(' ');
                writeValue(writer, entry.getValue(), indentFactor, indent);
            } else if (length != 0) {
                final int newIndent = indent + indentFactor;
                for (Map.Entry<String, ?> entry : this.map.entrySet()) {
                    if (commanate) writer.write(',');
                    if (indentFactor > 0) writer.write('\n');
                    indent(writer, newIndent);
                    writer.write(quote(entry.getKey()));
                    writer.write(':');
                    if (indentFactor > 0) writer.write(' ');
                    writeValue(writer, entry.getValue(), indentFactor, newIndent);
                    commanate = true;
                }
                if (indentFactor > 0) writer.write('\n');
                indent(writer, indent);
            }
            writer.write('}');
            return writer;
        } catch (IOException exception) {
            throw new JSONException(exception);
        }
    }

    static final Writer writeValue(Writer writer, Object value, int indentFactor, int indent) throws JSONException, IOException {
        if (value == null || value.equals(null)) {
            writer.write("null");
        } else if (value instanceof JSONObject) {
            ((JSONObject) value).write(writer, indentFactor, indent);
        } else if (value instanceof JSONArray) {
            ((JSONArray) value).write(writer, indentFactor, indent);
        } else if (value instanceof Number) {
            writer.write(value.toString());
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else {
            quote(value.toString(), writer);
        }
        return writer;
    }

    static final void indent(Writer writer, int indent) throws IOException {
        for (int i = 0; i < indent; i += 1) writer.write(' ');
    }
}
