package com.ontologybizruntime.codegen.osi;

import com.ontologybizruntime.codegen.osi.model.ActionTypeDef;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaObject;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class OpenApiWriter {
  private OpenApiWriter() {}

  static String render(String modelName, String basePackage, List<ActionTypeDef> actions, JavaTypeResolver resolver) {
    String title = (modelName == null || modelName.isBlank()) ? "Generated Service" : (modelName + " Service");

    Map<String, List<ActionTypeDef>> tags = groupByTag(actions);

    StringBuilder sb = new StringBuilder();
    sb.append("openapi: 3.0.3\n");
    sb.append("info:\n");
    sb.append("  title: ").append(q(title)).append("\n");
    sb.append("  version: '0.1.0'\n");
    sb.append("servers:\n");
    sb.append("  - url: http://localhost:8080\n");
    sb.append("paths:\n");

    for (ActionTypeDef a : actions) {
      if (a.id() == null || a.id().isBlank()) {
        continue;
      }
      String path = "/api/" + a.id();
      String method = httpMethod(a, resolver);
      String operationId = Names.toCamel(a.id());
      String tag = primaryTag(a);
      String reqSchemaName = Names.toPascal(a.id()) + "Request";

      sb.append("  ").append(path).append(":\n");
      sb.append("    ").append(method).append(":\n");
      sb.append("      operationId: ").append(q(operationId)).append("\n");
      if (a.title() != null && !a.title().isBlank()) {
        sb.append("      summary: ").append(q(a.title())).append("\n");
      }
      sb.append("      tags:\n");
      sb.append("        - ").append(q(tag)).append("\n");

      JsonSchemaObject input = a.inputSchema();
      if (input != null && input.properties() != null && !input.properties().isEmpty()) {
        if ("get".equals(method)) {
          sb.append("      parameters:\n");
          for (Map.Entry<String, JsonSchemaProperty> e : input.properties().entrySet()) {
            String name = e.getKey();
            JsonSchemaProperty p = e.getValue();
            sb.append("        - name: ").append(q(name)).append("\n");
            sb.append("          in: query\n");
            sb.append("          required: ").append(input.required() != null && input.required().contains(name) ? "true" : "false").append("\n");
            sb.append("          schema:\n");
            emitSchemaInline(sb, 12, p);
            if (p != null && p.description() != null && !p.description().isBlank()) {
              sb.append("          description: ").append(q(p.description())).append("\n");
            }
          }
        } else {
          sb.append("      requestBody:\n");
          sb.append("        required: true\n");
          sb.append("        content:\n");
          sb.append("          application/json:\n");
          sb.append("            schema:\n");
          sb.append("              $ref: '#/components/schemas/").append(reqSchemaName).append("'\n");
        }
      }

      sb.append("      responses:\n");
      sb.append("        '200':\n");
      sb.append("          description: OK\n");
      sb.append("          content:\n");
      sb.append("            application/json:\n");
      sb.append("              schema:\n");
      sb.append("                $ref: '#/components/schemas/ActionResult'\n");
    }

    sb.append("components:\n");
    sb.append("  schemas:\n");
    emitActionResultSchema(sb);
    emitRequestSchemas(sb, actions);
    return sb.toString();
  }

  private static String httpMethod(ActionTypeDef a, JavaTypeResolver resolver) {
    String kind = a.kind();
    if (kind != null && "query".equalsIgnoreCase(kind)) {
      if (resolver.isGetFriendly(a.inputSchema())) {
        return "get";
      }
      return "post";
    }
    if (kind != null && "command".equalsIgnoreCase(kind)) {
      return "post";
    }
    return "post";
  }

  private static Map<String, List<ActionTypeDef>> groupByTag(List<ActionTypeDef> actions) {
    Map<String, List<ActionTypeDef>> out = new LinkedHashMap<>();
    for (ActionTypeDef a : actions) {
      String tag = primaryTag(a);
      out.computeIfAbsent(tag, k -> new ArrayList<>()).add(a);
    }
    return out;
  }

  private static String primaryTag(ActionTypeDef a) {
    if (a.tags() != null && !a.tags().isEmpty()) {
      return a.tags().get(0);
    }
    if (a.aggregate() != null && !a.aggregate().isBlank()) {
      return a.aggregate();
    }
    if (a.entityName() != null && !a.entityName().isBlank()) {
      return a.entityName();
    }
    return "Actions";
  }

  private static void emitActionResultSchema(StringBuilder sb) {
    sb.append("    ActionResult:\n");
    sb.append("      type: object\n");
    sb.append("      properties:\n");
    sb.append("        success:\n");
    sb.append("          type: boolean\n");
    sb.append("        message:\n");
    sb.append("          type: string\n");
    sb.append("          nullable: true\n");
    sb.append("        data:\n");
    sb.append("          nullable: true\n");
    sb.append("          type: object\n");
  }

  private static void emitRequestSchemas(StringBuilder sb, List<ActionTypeDef> actions) {
    Set<String> emitted = new LinkedHashSet<>();
    for (ActionTypeDef a : actions) {
      if (a.id() == null || a.id().isBlank()) {
        continue;
      }
      String name = Names.toPascal(a.id()) + "Request";
      if (emitted.contains(name)) {
        continue;
      }
      emitted.add(name);

      JsonSchemaObject input = a.inputSchema();
      sb.append("    ").append(name).append(":\n");
      sb.append("      type: object\n");
      sb.append("      additionalProperties: false\n");
      if (input != null && input.required() != null && !input.required().isEmpty()) {
        sb.append("      required:\n");
        for (String r : input.required()) {
          sb.append("        - ").append(q(r)).append("\n");
        }
      }
      sb.append("      properties:\n");
      if (input != null && input.properties() != null && !input.properties().isEmpty()) {
        for (Map.Entry<String, JsonSchemaProperty> e : input.properties().entrySet()) {
          sb.append("        ").append(e.getKey()).append(":\n");
          emitSchemaInline(sb, 10, e.getValue());
        }
      }
    }
  }

  private static void emitSchemaInline(StringBuilder sb, int indent, JsonSchemaProperty p) {
    String i = " ".repeat(indent);
    String type = p == null ? null : p.type();
    if (type == null) {
      sb.append(i).append("type: string\n");
      return;
    }
    switch (type) {
      case "string" -> sb.append(i).append("type: string\n");
      case "integer" -> sb.append(i).append("type: integer\n");
      case "number" -> sb.append(i).append("type: number\n");
      case "boolean" -> sb.append(i).append("type: boolean\n");
      case "object" -> sb.append(i).append("type: object\n");
      case "array" -> {
        sb.append(i).append("type: array\n");
        sb.append(i).append("items:\n");
        emitSchemaInline(sb, indent + 2, p.items());
      }
      default -> sb.append(i).append("type: string\n");
    }
  }

  private static String q(String s) {
    if (s == null) {
      return "''";
    }
    String v = s.replace("'", "''");
    return "'" + v + "'";
  }
}

