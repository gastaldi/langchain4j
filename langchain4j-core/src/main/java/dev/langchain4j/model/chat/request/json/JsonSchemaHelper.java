package dev.langchain4j.model.chat.request.json;

import dev.langchain4j.model.output.structured.Description;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.internal.TypeUtils.isJsonBoolean;
import static dev.langchain4j.internal.TypeUtils.isJsonInteger;
import static dev.langchain4j.internal.TypeUtils.isJsonNumber;
import static dev.langchain4j.internal.TypeUtils.isJsonString;
import static java.lang.reflect.Modifier.isStatic;

public class JsonSchemaHelper { // TODO name, place

    public static JsonSchemaElement jsonSchemaElementFrom(Class<?> clazz, Type type, String fieldDescription) {

        if (isJsonString(clazz)) {
            return JsonStringSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonInteger(clazz)) {
            return JsonIntegerSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonNumber(clazz)) {
            return JsonNumberSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (isJsonBoolean(clazz)) {
            return JsonBooleanSchema.builder()
                    .description(fieldDescription)
                    .build();
        }

        if (clazz.isEnum()) {
            return JsonEnumSchema.builder()
                    .enumValues(clazz)
                    .description(Optional.ofNullable(fieldDescription).orElse(descriptionFrom(clazz)))
                    .build();
        }

        if (clazz.isArray()) {
            return JsonArraySchema.builder()
                    .items(jsonSchemaElementFrom(clazz.getComponentType(), null, null))
                    .description(fieldDescription)
                    .build();
        }

        if (clazz.equals(List.class) || clazz.equals(Set.class)) {
            return JsonArraySchema.builder()
                    .items(jsonSchemaElementFrom(getActualType(type), null, null))
                    .description(fieldDescription)
                    .build();
        }

        return jsonObjectSchemaFrom(clazz, fieldDescription);
    }

    public static JsonObjectSchema jsonObjectSchemaFrom(Class<?> type, String description) {

        Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        for (Field field : type.getDeclaredFields()) {
            String fieldName = field.getName();
            if (isStatic(field.getModifiers()) || fieldName.equals("__$hits$__") || fieldName.startsWith("this$")) {
                continue;
            }
            String fieldDescription = descriptionFrom(field);
            JsonSchemaElement jsonSchemaElement = jsonSchemaElementFrom(field.getType(), field.getGenericType(), fieldDescription);
            properties.put(fieldName, jsonSchemaElement);
        }

        return JsonObjectSchema.builder()
                .description(Optional.ofNullable(description).orElse(descriptionFrom(type)))
                .properties(properties)
                .required(new ArrayList<>(properties.keySet()))
                .additionalProperties(false)
                .build();
    }

    private static String descriptionFrom(Field field) {
        // TODO check if works for tool params
        return descriptionFrom(field.getAnnotation(Description.class));
    }

    private static String descriptionFrom(Class<?> type) {
        // TODO check if works for tool params
        return descriptionFrom(type.getAnnotation(Description.class));
    }

    private static String descriptionFrom(Description description) {
        if (description == null) {
            return null;
        }
        return String.join(" ", description.value());
    }

    private static Class<?> getActualType(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                return (Class<?>) actualTypeArguments[0];
            }
        }
        return null;
    }

    public static Map<String, Map<String, Object>> toMap(Map<String, JsonSchemaElement> properties) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        properties.forEach((property, value) -> map.put(property, toMap(value)));
        return map;
    }

    public static Map<String, Object> toMap(JsonSchemaElement jsonSchemaElement) {
        if (jsonSchemaElement instanceof JsonObjectSchema) {
            JsonObjectSchema jsonObjectSchema = (JsonObjectSchema) jsonSchemaElement;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "object");
            if (jsonObjectSchema.description() != null) {
                properties.put("description", jsonObjectSchema.description());
            }
            properties.put("properties", toMap(jsonObjectSchema.properties()));
            if (jsonObjectSchema.required() != null) {
                properties.put("required", jsonObjectSchema.required());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonArraySchema) {
            JsonArraySchema jsonArraySchema = (JsonArraySchema) jsonSchemaElement;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "array");
            if (jsonArraySchema.description() != null) {
                properties.put("description", jsonArraySchema.description());
            }
            properties.put("items", toMap(jsonArraySchema.items()));
            return properties;
        } else if (jsonSchemaElement instanceof JsonEnumSchema) {
            JsonEnumSchema jsonEnumSchema = (JsonEnumSchema) jsonSchemaElement;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "string");
            if (jsonEnumSchema.description() != null) {
                properties.put("description", jsonEnumSchema.description());
            }
            properties.put("enum", jsonEnumSchema.enumValues());
            return properties;
        } else if (jsonSchemaElement instanceof JsonStringSchema) {
            JsonStringSchema jsonStringSchema = (JsonStringSchema) jsonSchemaElement;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "string");
            if (jsonStringSchema.description() != null) {
                properties.put("description", jsonStringSchema.description());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonIntegerSchema) {
            JsonIntegerSchema jsonIntegerSchema = (JsonIntegerSchema) jsonSchemaElement;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "integer");
            if (jsonIntegerSchema.description() != null) {
                properties.put("description", jsonIntegerSchema.description());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonNumberSchema) {
            JsonNumberSchema jsonNumberSchema = (JsonNumberSchema) jsonSchemaElement;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "number");
            if (jsonNumberSchema.description() != null) {
                properties.put("description", jsonNumberSchema.description());
            }
            return properties;
        } else if (jsonSchemaElement instanceof JsonBooleanSchema) {
            JsonBooleanSchema jsonBooleanSchema = (JsonBooleanSchema) jsonSchemaElement;
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("type", "boolean");
            if (jsonBooleanSchema.description() != null) {
                properties.put("description", jsonBooleanSchema.description());
            }
            return properties;
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonSchemaElement.getClass());
        }
    }
}
