package pl.bushstar.bushlaunch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimalny parser JSON (bez zewnętrznych bibliotek). */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json j = new Json(text);
        j.skipWs();
        Object v = j.readValue();
        j.skipWs();
        if (j.i != j.s.length()) {
            throw j.error("Nieoczekiwane znaki po JSON");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String str(Object o) {
        return o instanceof String ? (String) o : null;
    }

    public static long num(Object o, long def) {
        return o instanceof Number ? ((Number) o).longValue() : def;
    }

    public static boolean bool(Object o, boolean def) {
        return o instanceof Boolean ? (Boolean) o : def;
    }

    /** get(root, "a", "b") zwraca root.a.b albo null. */
    public static Object get(Object o, String... path) {
        for (String k : path) {
            Map<String, Object> m = map(o);
            if (m == null) {
                return null;
            }
            o = m.get(k);
        }
        return o;
    }

    private Object readValue() {
        if (i >= s.length()) {
            throw error("Nieoczekiwany koniec danych");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        skipWs();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            skipWs();
            if (peek() != '"') {
                throw error("Oczekiwano klucza tekstowego");
            }
            String key = readString();
            skipWs();
            if (peek() != ':') {
                throw error("Oczekiwano ':'");
            }
            i++;
            skipWs();
            m.put(key, readValue());
            skipWs();
            char c = peek();
            i++;
            if (c == ',') {
                continue;
            }
            if (c == '}') {
                return m;
            }
            throw error("Oczekiwano ',' lub '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> l = new ArrayList<>();
        i++; // [
        skipWs();
        if (peek() == ']') {
            i++;
            return l;
        }
        while (true) {
            skipWs();
            l.add(readValue());
            skipWs();
            char c = peek();
            i++;
            if (c == ',') {
                continue;
            }
            if (c == ']') {
                return l;
            }
            throw error("Oczekiwano ',' lub ']'");
        }
    }

    private String readString() {
        i++; // "
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw error("Niezamknięty tekst");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default:
                    sb.append(e); // \" \\ \/
            }
        }
    }

    private Object readNumber() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw error("Nieoczekiwany znak '" + s.charAt(i) + "'");
        }
        String t = s.substring(start, i);
        if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) {
            return Double.parseDouble(t);
        }
        return Long.parseLong(t);
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) {
            throw error("Oczekiwano '" + word + "'");
        }
        i += word.length();
    }

    private char peek() {
        if (i >= s.length()) {
            throw error("Nieoczekiwany koniec danych");
        }
        return s.charAt(i);
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("Błąd JSON na pozycji " + i + ": " + msg);
    }
}
