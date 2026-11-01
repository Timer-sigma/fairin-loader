package com.fairin.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser (enough for version manifests: maps, lists, strings, numbers, booleans, null). */
public final class TinyJson {
    private final String s;
    private int i;

    private TinyJson(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        return new TinyJson(json).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object o = parse(json);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private Object parseValue() {
        skipWs();
        char c = s.charAt(i);
        switch (c) {
            case '{': return parseMap();
            case '[': return parseList();
            case '"': return parseString();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return parseNumber();
        }
    }

    private Map<String, Object> parseMap() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++; // {
        skipWs();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            i++; // :
            m.put(key, parseValue());
            skipWs();
            char c = s.charAt(i);
            i++;
            if (c == '}') return m;
        }
    }

    private List<Object> parseList() {
        List<Object> l = new ArrayList<Object>();
        i++; // [
        skipWs();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            l.add(parseValue());
            skipWs();
            char c = s.charAt(i);
            i++;
            if (c == ']') return l;
        }
    }

    private String parseString() {
        StringBuilder sb = new StringBuilder();
        i++; // "
        while (s.charAt(i) != '"') {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                char e = s.charAt(i);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        i++; // closing "
        return sb.toString();
    }

    private Object parseNumber() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String num = s.substring(start, i);
        if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
            try { return Long.valueOf(num); } catch (NumberFormatException ignore) { /* fall through */ }
        }
        return Double.valueOf(num);
    }

    private void skipWs() {
        while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
    }
}
