package com.ontologybizruntime.codegen.osi;

import com.ontologybizruntime.codegen.osi.model.FieldDef;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaObject;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaProperty;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

final class JavaTypeResolver {
  private static final ClassName OFFSET_DATE_TIME = ClassName.get(OffsetDateTime.class);
  private static final ClassName BIG_DECIMAL = ClassName.get(BigDecimal.class);
  private static final ClassName STRING = ClassName.get(String.class);
  private static final ClassName INTEGER = ClassName.get(Integer.class);
  private static final ClassName BOOLEAN = ClassName.get(Boolean.class);
  private static final ClassName JSON_NODE = ClassName.get("com.fasterxml.jackson.databind", "JsonNode");

  TypeName typeForDatasetField(FieldDef field) {
    String name = field.name();
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (field.isTime() || n.endsWith("_time") || n.endsWith("_at") || n.endsWith("_date")) {
      return OFFSET_DATE_TIME;
    }
    if (n.endsWith("_id")) {
      return STRING;
    }
    if (n.contains("amount") || n.contains("price") || n.contains("revenue")) {
      return BIG_DECIMAL;
    }
    if (n.contains("count") || n.contains("quantity") || n.contains("qty") || n.contains("top_n")) {
      return INTEGER;
    }
    if (n.startsWith("is_")) {
      return BOOLEAN;
    }
    return STRING;
  }

  TypeName typeForSchemaProperty(JsonSchemaProperty p) {
    String t = p == null ? null : p.type();
    if (t == null) {
      return STRING;
    }
    return switch (t) {
      case "string" -> STRING;
      case "integer" -> INTEGER;
      case "number" -> BIG_DECIMAL;
      case "boolean" -> BOOLEAN;
      case "object" -> JSON_NODE;
      case "array" -> ParameterizedTypeName.get(ClassName.get(java.util.List.class), JSON_NODE);
      default -> STRING;
    };
  }

  boolean isGetFriendly(JsonSchemaObject schema) {
    if (schema == null) {
      return true;
    }
    if (schema.properties() == null) {
      return true;
    }
    for (Map.Entry<String, JsonSchemaProperty> e : schema.properties().entrySet()) {
      JsonSchemaProperty p = e.getValue();
      if (p == null) {
        continue;
      }
      String t = p.type();
      if ("object".equals(t) || "array".equals(t)) {
        return false;
      }
    }
    return true;
  }
}

