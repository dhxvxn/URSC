package org.ursc.trajectory.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON support with no dependencies: parse to {@code Map/List/String/Double/Boolean/null},
 * serialise those (and plain public-field objects) back, and bind JSON onto plain
 * public-field config objects (primitives, String, enums, nested objects, lists).
 */
public final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- parse

    public static Object parse(final String text) {
        final Parser p = new Parser(text);
        p.ws();
        final Object v = p.value();
        p.ws();
        if (p.i != text.length()) {
            throw p.error("trailing characters");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(final String text) {
        final Object o = parse(text);
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("JSON value is not an object");
        }
        return (Map<String, Object>) o;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(final String s) {
            this.s = s;
        }

        IllegalArgumentException error(final String msg) {
            return new IllegalArgumentException("JSON " + msg + " at offset " + i);
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object value() {
            if (i >= s.length()) {
                throw error("unexpected end");
            }
            final char c = s.charAt(i);
            if (c == '{') {
                return object();
            }
            if (c == '[') {
                return array();
            }
            if (c == '"') {
                return string();
            }
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

        Map<String, Object> object() {
            final Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                final String k = string();
                ws();
                expect(':');
                ws();
                m.put(k, value());
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            final List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                ws();
                l.add(value());
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                expect(']');
                return l;
            }
        }

        void expect(final char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw error("expected '" + c + "'");
            }
            i++;
        }

        String string() {
            expect('"');
            final StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw error("unterminated string");
                }
                final char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    final char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u':
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
        }

        Double number() {
            final int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (start == i) {
                throw error("unexpected character '" + s.charAt(i) + "'");
            }
            return Double.valueOf(s.substring(start, i));
        }
    }

    // ---------------------------------------------------------------- write

    public static String write(final Object value) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value, 0, true);
        return sb.toString();
    }

    public static String writeCompact(final Object value) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value, 0, false);
        return sb.toString();
    }

    private static void indent(final StringBuilder sb, final int n, final boolean pretty) {
        if (pretty) {
            sb.append('\n');
            for (int k = 0; k < n; k++) {
                sb.append("  ");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void write(final StringBuilder sb, final Object v, final int depth, final boolean pretty) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            quote(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Number) {
            final double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (v instanceof Integer || v instanceof Long || (d == Math.rint(d) && Math.abs(d) < 1e15)) {
                sb.append(((Number) v).longValue());
            } else {
                sb.append(Double.toString(d)); // shortest exact round-trip form, valid JSON
            }
        } else if (v instanceof Enum) {
            quote(sb, ((Enum<?>) v).name());
        } else if (v instanceof Map) {
            final Map<Object, Object> m = (Map<Object, Object>) v;
            sb.append('{');
            boolean first = true;
            for (final Map.Entry<Object, Object> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                indent(sb, depth + 1, pretty);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                write(sb, e.getValue(), depth + 1, pretty);
            }
            if (!m.isEmpty()) {
                indent(sb, depth, pretty);
            }
            sb.append('}');
        } else if (v instanceof Collection || v.getClass().isArray()) {
            final List<Object> l = new ArrayList<>();
            if (v instanceof Collection) {
                l.addAll((Collection<Object>) v);
            } else {
                final int n = java.lang.reflect.Array.getLength(v);
                for (int k = 0; k < n; k++) {
                    l.add(java.lang.reflect.Array.get(v, k));
                }
            }
            sb.append('[');
            final boolean flat = l.stream().allMatch(x -> x == null || x instanceof Number || x instanceof Boolean);
            for (int k = 0; k < l.size(); k++) {
                if (k > 0) {
                    sb.append(flat && pretty ? ", " : ",");
                }
                if (!flat) {
                    indent(sb, depth + 1, pretty);
                }
                write(sb, l.get(k), depth + 1, pretty);
            }
            if (!flat && !l.isEmpty()) {
                indent(sb, depth, pretty);
            }
            sb.append(']');
        } else {
            write(sb, toMap(v), depth, pretty);
        }
    }

    private static void quote(final StringBuilder sb, final String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            final char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- object binding

    /** Public, non-static fields of a plain config object as an ordered map (recursively). */
    public static Map<String, Object> toMap(final Object o) {
        final Map<String, Object> m = new LinkedHashMap<>();
        for (final Field f : o.getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            try {
                m.put(f.getName(), f.get(o));
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return m;
    }

    /**
     * Copy values from a parsed JSON object onto the public fields of {@code target}.
     * Unknown keys are reported in the returned list (not fatal) so an older config still loads.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<String> bind(final Map<String, Object> json, final Object target) {
        final List<String> unknown = new ArrayList<>();
        for (final Map.Entry<String, Object> e : json.entrySet()) {
            final Field f;
            try {
                f = target.getClass().getField(e.getKey());
            } catch (final NoSuchFieldException ex) {
                unknown.add(e.getKey());
                continue;
            }
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                continue;
            }
            final Object v = e.getValue();
            try {
                final Class<?> t = f.getType();
                if (v == null) {
                    if (!t.isPrimitive()) {
                        f.set(target, null);
                    }
                } else if (t == double.class || t == Double.class) {
                    f.set(target, ((Number) v).doubleValue());
                } else if (t == int.class || t == Integer.class) {
                    f.set(target, ((Number) v).intValue());
                } else if (t == long.class || t == Long.class) {
                    f.set(target, ((Number) v).longValue());
                } else if (t == boolean.class || t == Boolean.class) {
                    f.set(target, v);
                } else if (t == String.class) {
                    f.set(target, String.valueOf(v));
                } else if (t.isEnum()) {
                    f.set(target, Enum.valueOf((Class<? extends Enum>) t, String.valueOf(v)));
                } else if (List.class.isAssignableFrom(t)) {
                    final Type g = f.getGenericType();
                    final Class<?> el = g instanceof ParameterizedType
                            ? (Class<?>) ((ParameterizedType) g).getActualTypeArguments()[0] : Object.class;
                    final List<Object> out = new ArrayList<>();
                    for (final Object x : (List<Object>) v) {
                        if (x instanceof Map && el != Object.class && el != Map.class) {
                            final Object child = el.getDeclaredConstructor().newInstance();
                            for (final String u : bind((Map<String, Object>) x, child)) {
                                unknown.add(e.getKey() + "[]." + u);
                            }
                            out.add(child);
                        } else if (el == String.class) {
                            out.add(String.valueOf(x));
                        } else if (el == Double.class && x instanceof Number) {
                            out.add(((Number) x).doubleValue());
                        } else {
                            out.add(x);
                        }
                    }
                    f.set(target, out);
                } else if (v instanceof Map) {
                    Object child = f.get(target);
                    if (child == null) {
                        child = t.getDeclaredConstructor().newInstance();
                        f.set(target, child);
                    }
                    for (final String u : bind((Map<String, Object>) v, child)) {
                        unknown.add(e.getKey() + "." + u);
                    }
                } else {
                    unknown.add(e.getKey() + " (unsupported type)");
                }
            } catch (final ReflectiveOperationException | ClassCastException | IllegalArgumentException ex) {
                throw new IllegalArgumentException("bad value for '" + e.getKey() + "': " + v, ex);
            }
        }
        return unknown;
    }
}
