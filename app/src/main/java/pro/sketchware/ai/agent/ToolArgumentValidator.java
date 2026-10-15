package pro.sketchware.ai.agent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Defensive validation at the tool execution boundary. Provider schemas are helpful hints,
 * not a security boundary: every model-produced call is checked again before execution.
 */
public final class ToolArgumentValidator {
    private static final int MAX_ARGUMENT_CHARS = 1024 * 1024;
    private static final int MAX_STRING_CHARS = 256 * 1024;
    private static final int MAX_COLLECTION_ITEMS = 256;
    private static final int MAX_DEPTH = 8;

    private ToolArgumentValidator() {}

    /** Returns null for a valid call, otherwise a safe, user-displayable validation error. */
    public static String validate(Tool.ToolSpec spec, JSONObject args) {
        if (spec == null || spec.parameters == null) {
            return "Tool has no valid parameter schema.";
        }
        if (args == null) return "Tool arguments must be a JSON object.";
        try {
            if (args.toString().length() > MAX_ARGUMENT_CHARS) {
                return "Tool arguments exceed the 1 MiB safety limit.";
            }
            JSONObject schema = spec.parameters;
            if (!"object".equals(schema.optString("type"))
                    || schema.optJSONObject("properties") == null) {
                return "Tool parameter schema is invalid.";
            }
            JSONArray required = schema.optJSONArray("required");
            if (required != null) {
                for (int i = 0; i < required.length(); i++) {
                    String name = required.optString(i, "");
                    if (name.isEmpty() || !args.has(name) || args.isNull(name)) {
                        return "Missing required argument: " + safeName(name);
                    }
                }
            }

            JSONObject properties = schema.optJSONObject("properties");
            Iterator<String> keys = args.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = args.opt(key);
                JSONObject propertySchema = properties.optJSONObject(key);
                if (propertySchema == null) {
                    if (schema.has("additionalProperties")
                            && !schema.optBoolean("additionalProperties", true)) {
                        return "Unexpected argument: " + safeName(key);
                    }
                    // Bound unknown fields too; they must never bypass size/depth limits.
                    String genericError = validateGeneric(value, key, 0);
                    if (genericError != null) return genericError;
                    continue;
                }
                String error = validateValue(key, value, propertySchema, 0);
                if (error != null) return error;
            }
            return null;
        } catch (RuntimeException e) {
            return "Tool arguments could not be validated safely.";
        }
    }

    private static String validateValue(String name, Object value, JSONObject schema, int depth) {
        if (value == null || value == JSONObject.NULL) {
            return "Argument '" + safeName(name) + "' must not be null.";
        }
        if (depth > MAX_DEPTH) return "Tool arguments are nested too deeply.";
        String expected = schema.optString("type", "");
        if (!expected.isEmpty() && !matchesType(value, expected)) {
            return "Argument '" + safeName(name) + "' must be of type " + expected + ".";
        }

        JSONArray allowed = schema.optJSONArray("enum");
        if (allowed != null) {
            boolean found = false;
            for (int i = 0; i < allowed.length(); i++) {
                Object candidate = allowed.opt(i);
                if (candidate != null && candidate.equals(value)) {
                    found = true;
                    break;
                }
            }
            if (!found) return "Argument '" + safeName(name) + "' has an unsupported value.";
        }

        if (value instanceof String) {
            String text = (String) value;
            int max = schema.optInt("maxLength", MAX_STRING_CHARS);
            if (isPathLike(name)) max = Math.min(max, 4096);
            if (text.length() > max) {
                return "Argument '" + safeName(name) + "' exceeds its maximum length.";
            }
            if (schema.has("minLength") && text.length() < schema.optInt("minLength")) {
                return "Argument '" + safeName(name) + "' is too short.";
            }
            if (text.indexOf('\u0000') >= 0) {
                return "Argument '" + safeName(name) + "' contains an invalid character.";
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            int max = Math.min(schema.optInt("maxItems", MAX_COLLECTION_ITEMS), MAX_COLLECTION_ITEMS);
            if (array.length() > max) return "Argument '" + safeName(name) + "' contains too many items.";
            JSONObject itemSchema = schema.optJSONObject("items");
            if (itemSchema != null) {
                for (int i = 0; i < array.length(); i++) {
                    String error = validateValue(name, array.opt(i), itemSchema, depth + 1);
                    if (error != null) return error;
                }
            }
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.length() > MAX_COLLECTION_ITEMS) {
                return "Argument '" + safeName(name) + "' contains too many fields.";
            }
            JSONObject childProperties = schema.optJSONObject("properties");
            if (childProperties != null) {
                Iterator<String> it = object.keys();
                while (it.hasNext()) {
                    String child = it.next();
                    JSONObject childSchema = childProperties.optJSONObject(child);
                    if (childSchema == null) {
                        if (schema.has("additionalProperties")
                                && !schema.optBoolean("additionalProperties", true)) {
                            return "Unexpected field in argument '" + safeName(name) + "'.";
                        }
                        String error = validateGeneric(object.opt(child), child, depth + 1);
                        if (error != null) return error;
                    } else {
                        String error = validateValue(child, object.opt(child), childSchema, depth + 1);
                        if (error != null) return error;
                    }
                }
            }
        }
        return null;
    }

    private static String validateGeneric(Object value, String name, int depth) {
        if (depth > MAX_DEPTH) return "Tool arguments are nested too deeply.";
        if (value == null || value == JSONObject.NULL) return "Argument value must not be null.";
        if (value instanceof String) {
            String s = (String) value;
            int max = isPathLike(name) ? 4096 : MAX_STRING_CHARS;
            if (s.length() > max) return "Argument '" + safeName(name) + "' exceeds the safety limit.";
            if (s.indexOf('\u0000') >= 0) return "Argument contains an invalid character.";
        } else if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            if (a.length() > MAX_COLLECTION_ITEMS) return "Tool arguments contain too many items.";
            for (int i = 0; i < a.length(); i++) {
                String error = validateGeneric(a.opt(i), name, depth + 1);
                if (error != null) return error;
            }
        } else if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            if (o.length() > MAX_COLLECTION_ITEMS) return "Tool arguments contain too many fields.";
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String key = it.next();
                String error = validateGeneric(o.opt(key), key, depth + 1);
                if (error != null) return error;
            }
        }
        return null;
    }

    private static boolean matchesType(Object value, String type) {
        switch (type) {
            case "object": return value instanceof JSONObject;
            case "array": return value instanceof JSONArray;
            case "string": return value instanceof String;
            case "boolean": return value instanceof Boolean;
            case "integer":
                return value instanceof Number
                        && ((Number) value).doubleValue() == ((Number) value).longValue();
            case "number": return value instanceof Number;
            default: return false; // Unsupported schema types fail closed.
        }
    }

    private static boolean isPathLike(String name) {
        if (name == null) return false;
        String key = name.toLowerCase(java.util.Locale.ROOT);
        return key.equals("path") || key.equals("src") || key.equals("dst")
                || key.endsWith("path") || key.equals("folder") || key.equals("name")
                || key.equals("target") || key.equals("mode") || key.equals("op")
                || key.equals("sc_id") || key.equals("id") || key.equals("parentid")
                || key.equals("layout") || key.equals("pattern");
    }

    private static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) return "unknown";
        return name.replaceAll("[^A-Za-z0-9_.-]", "").substring(0,
                Math.min(64, name.replaceAll("[^A-Za-z0-9_.-]", "").length()));
    }
}
