package com.ontologybizruntime.codegen.osi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ontologybizruntime.codegen.osi.model.ActionTypeDef;
import com.ontologybizruntime.codegen.osi.model.DatasetDef;
import com.ontologybizruntime.codegen.osi.model.FieldDef;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaObject;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaProperty;
import com.ontologybizruntime.codegen.osi.model.SemanticModelDef;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OsiModelParser {
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final ObjectMapper jsonMapper = new ObjectMapper();

  List<SemanticModelDef> parse(Path yamlPath) throws IOException {
    JsonNode root = yamlMapper.readTree(yamlPath.toFile());
    JsonNode models = root.path("semantic_model");
    if (!models.isArray()) {
      return List.of();
    }

    List<SemanticModelDef> out = new ArrayList<>();
    for (JsonNode model : models) {
      String modelName = text(model, "name");
      String modelDesc = text(model, "description");
      List<DatasetDef> datasets = parseDatasets(model.path("datasets"));
      out.add(new SemanticModelDef(modelName, modelDesc, datasets));
    }
    return out;
  }

  private List<DatasetDef> parseDatasets(JsonNode datasetsNode) {
    if (!datasetsNode.isArray()) {
      return List.of();
    }
    List<DatasetDef> out = new ArrayList<>();
    for (JsonNode ds : datasetsNode) {
      String name = text(ds, "name");
      String source = text(ds, "source");
      String desc = text(ds, "description");
      List<FieldDef> fields = parseFields(ds.path("fields"));
      List<ActionTypeDef> actions = parseActionTypesFromExtensions(ds.path("custom_extensions"));
      out.add(new DatasetDef(name, source, desc, fields, actions));
    }
    return out;
  }

  private List<FieldDef> parseFields(JsonNode fieldsNode) {
    if (!fieldsNode.isArray()) {
      return List.of();
    }
    List<FieldDef> out = new ArrayList<>();
    for (JsonNode f : fieldsNode) {
      String name = text(f, "name");
      boolean isTime = f.path("dimension").path("is_time").asBoolean(false);
      out.add(new FieldDef(name, isTime));
    }
    return out;
  }

  private List<ActionTypeDef> parseActionTypesFromExtensions(JsonNode extensionsNode) {
    if (!extensionsNode.isArray()) {
      return List.of();
    }
    List<ActionTypeDef> out = new ArrayList<>();
    for (JsonNode ext : extensionsNode) {
      String vendor = text(ext, "vendor_name");
      if (!"COMMON".equalsIgnoreCase(vendor)) {
        continue;
      }
      String data = text(ext, "data");
      if (data == null || data.isBlank()) {
        continue;
      }
      JsonNode json;
      try {
        json = jsonMapper.readTree(data);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid JSON in custom_extensions.data for vendor COMMON", e);
      }
      JsonNode actions = json.path("action_types");
      if (!actions.isArray()) {
        continue;
      }
      for (JsonNode a : actions) {
        out.add(parseAction(a));
      }
    }
    return out;
  }

  private ActionTypeDef parseAction(JsonNode a) {
    String id = text(a, "id");
    String title = text(a, "title");
    String description = text(a, "description");
    String kind = text(a, "kind");
    String operation = text(a, "operation");
    String aggregate = text(a, "aggregate");
    String entityName = text(a, "entity_name");
    String idempotency = text(a, "idempotency");
    JsonSchemaObject inputSchema = parseInputSchema(a.path("io_schema").path("input_schema"));
    List<String> tags = readStringArray(a.path("tags"));
    return new ActionTypeDef(id, title, description, kind, operation, aggregate, entityName, idempotency, inputSchema, tags);
  }

  private JsonSchemaObject parseInputSchema(JsonNode schema) {
    if (schema == null || schema.isMissingNode() || schema.isNull()) {
      return new JsonSchemaObject("object", true, List.of(), Map.of());
    }

    String type = schema.path("type").asText("object");
    boolean additionalProperties = schema.path("additionalProperties").asBoolean(true);
    List<String> required = readStringArray(schema.path("required"));
    Map<String, JsonSchemaProperty> properties = parseProperties(schema.path("properties"));
    return new JsonSchemaObject(type, additionalProperties, required, properties);
  }

  private Map<String, JsonSchemaProperty> parseProperties(JsonNode props) {
    if (props == null || props.isMissingNode() || props.isNull() || !props.isObject()) {
      return Map.of();
    }
    Map<String, JsonSchemaProperty> out = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> it = props.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> e = it.next();
      out.put(e.getKey(), parseProperty(e.getValue()));
    }
    return out;
  }

  private JsonSchemaProperty parseProperty(JsonNode p) {
    if (p == null || p.isMissingNode() || p.isNull()) {
      return new JsonSchemaProperty(null, null, null, null);
    }
    String type = p.path("type").asText(null);
    String description = p.path("description").asText(null);
    Map<String, JsonSchemaProperty> properties = null;
    if (p.has("properties")) {
      properties = parseProperties(p.path("properties"));
    }
    JsonSchemaProperty items = null;
    if (p.has("items")) {
      items = parseProperty(p.path("items"));
    }
    return new JsonSchemaProperty(type, description, properties == null || properties.isEmpty() ? null : properties, items);
  }

  private List<String> readStringArray(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (JsonNode v : node) {
      if (v.isTextual()) {
        out.add(v.asText());
      }
    }
    return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode v = node.get(field);
    if (v == null || v.isNull() || v.isMissingNode()) {
      return null;
    }
    if (v.isTextual()) {
      return v.asText();
    }
    return v.toString();
  }
}

