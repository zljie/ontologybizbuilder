package com.ontologybizruntime.codegen.osi;

import com.ontologybizruntime.codegen.osi.model.ActionTypeDef;
import com.ontologybizruntime.codegen.osi.model.DatasetDef;
import com.ontologybizruntime.codegen.osi.model.FieldDef;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaObject;
import com.ontologybizruntime.codegen.osi.model.JsonSchemaProperty;
import com.ontologybizruntime.codegen.osi.model.SemanticModelDef;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;

public final class OsiSpringBootGenerator {
  private final OsiModelParser parser = new OsiModelParser();
  private final JavaTypeResolver typeResolver = new JavaTypeResolver();

  public void generate(GeneratorConfig config) throws IOException {
    List<SemanticModelDef> models = parser.parse(config.modelPath());
    if (models.isEmpty()) {
      throw new IllegalArgumentException("No semantic_model found in YAML: " + config.modelPath());
    }

    int generatedCount = 0;
    for (SemanticModelDef model : models) {
      if (config.onlyModelName() != null && !config.onlyModelName().equals(model.name())) {
        continue;
      }
      generateOneModel(config, model);
      generatedCount++;
    }

    if (config.onlyModelName() != null && generatedCount == 0) {
      throw new IllegalArgumentException("semantic_model.name not found: " + config.onlyModelName());
    }
  }

  private void generateOneModel(GeneratorConfig config, SemanticModelDef model) throws IOException {
    String modelName = model.name();
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("semantic_model.name is required");
    }

    String basePackage = config.basePackage();
    if (basePackage == null || basePackage.isBlank()) {
      basePackage = Names.defaultBasePackageFromModelName(modelName);
    }

    String artifactId = toArtifactId(modelName) + "-service";
    Path projectDir = config.outputDir().resolve(modelName + "-service");

    if (Files.exists(projectDir)) {
      if (!config.overwrite()) {
        throw new IllegalStateException("Output dir exists (use --overwrite): " + projectDir);
      }
      FileOps.deleteRecursively(projectDir);
    }

    Path javaRoot = projectDir.resolve("src/main/java");
    Path resourcesRoot = projectDir.resolve("src/main/resources");

    FileOps.ensureDir(javaRoot);
    FileOps.ensureDir(resourcesRoot);

    FileOps.writeString(projectDir.resolve("pom.xml"), GeneratedProjectPom.render(basePackage, artifactId));
    FileOps.writeString(resourcesRoot.resolve("application.yml"), "server:\n  port: 8080\n");

    List<ActionTypeDef> allActions = collectActions(model.datasets());

    writeApplication(javaRoot, basePackage);
    writeActionResult(javaRoot, basePackage);
    writeDatasetDtos(javaRoot, basePackage, model.datasets());
    writeActionRequestDtos(javaRoot, basePackage, allActions);
    writeActionsService(javaRoot, basePackage, allActions);
    writeControllers(javaRoot, basePackage, allActions);
    FileOps.writeString(projectDir.resolve("openapi.yaml"), OpenApiWriter.render(modelName, basePackage, allActions, typeResolver));
  }

  private static String toArtifactId(String raw) {
    if (raw == null) {
      return "model";
    }
    String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    s = s.replaceAll("^-+", "").replaceAll("-+$", "");
    return s.isBlank() ? "model" : s;
  }

  private static List<ActionTypeDef> collectActions(List<DatasetDef> datasets) {
    List<ActionTypeDef> out = new ArrayList<>();
    for (DatasetDef ds : datasets) {
      if (ds.actionTypes() == null) {
        continue;
      }
      out.addAll(ds.actionTypes());
    }
    out.sort(Comparator.comparing(ActionTypeDef::id, Comparator.nullsLast(String::compareTo)));
    return out;
  }

  private void writeApplication(Path javaRoot, String basePackage) throws IOException {
    ClassName appAnn = ClassName.get("org.springframework.boot.autoconfigure", "SpringBootApplication");
    ClassName springApp = ClassName.get("org.springframework.boot", "SpringApplication");

    TypeSpec type = TypeSpec.classBuilder("Application")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(appAnn)
        .addMethod(MethodSpec.methodBuilder("main")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(void.class)
            .addParameter(String[].class, "args")
            .addStatement("$T.run(Application.class, args)", springApp)
            .build())
        .build();

    JavaFile file = JavaFile.builder(basePackage, type).build();
    FileOps.writeJava(javaRoot, file);
  }

  private void writeActionResult(Path javaRoot, String basePackage) throws IOException {
    ClassName self = ClassName.get(basePackage, "ActionResult");

    FieldSpec success = FieldSpec.builder(TypeName.BOOLEAN, "success", Modifier.PRIVATE).build();
    FieldSpec message = FieldSpec.builder(ClassName.get(String.class), "message", Modifier.PRIVATE).build();
    FieldSpec data = FieldSpec.builder(ClassName.get(Object.class), "data", Modifier.PRIVATE).build();

    MethodSpec ctor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .addParameter(TypeName.BOOLEAN, "success")
        .addParameter(ClassName.get(String.class), "message")
        .addParameter(ClassName.get(Object.class), "data")
        .addStatement("this.success = success")
        .addStatement("this.message = message")
        .addStatement("this.data = data")
        .build();

    MethodSpec isSuccess = MethodSpec.methodBuilder("isSuccess")
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BOOLEAN)
        .addStatement("return success")
        .build();

    MethodSpec getMessage = MethodSpec.methodBuilder("getMessage")
        .addModifiers(Modifier.PUBLIC)
        .returns(ClassName.get(String.class))
        .addStatement("return message")
        .build();

    MethodSpec getData = MethodSpec.methodBuilder("getData")
        .addModifiers(Modifier.PUBLIC)
        .returns(ClassName.get(Object.class))
        .addStatement("return data")
        .build();

    MethodSpec okMsg = MethodSpec.methodBuilder("ok")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(self)
        .addParameter(ClassName.get(String.class), "message")
        .addStatement("return new ActionResult(true, message, null)")
        .build();

    MethodSpec okData = MethodSpec.methodBuilder("ok")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(self)
        .addParameter(ClassName.get(Object.class), "data")
        .addStatement("return new ActionResult(true, null, data)")
        .build();

    MethodSpec fail = MethodSpec.methodBuilder("fail")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(self)
        .addParameter(ClassName.get(String.class), "message")
        .addStatement("return new ActionResult(false, message, null)")
        .build();

    TypeSpec type = TypeSpec.classBuilder("ActionResult")
        .addModifiers(Modifier.PUBLIC)
        .addField(success)
        .addField(message)
        .addField(data)
        .addMethod(ctor)
        .addMethod(isSuccess)
        .addMethod(getMessage)
        .addMethod(getData)
        .addMethod(okMsg)
        .addMethod(okData)
        .addMethod(fail)
        .build();

    JavaFile file = JavaFile.builder(basePackage, type).build();
    FileOps.writeJava(javaRoot, file);
  }

  private void writeDatasetDtos(Path javaRoot, String basePackage, List<DatasetDef> datasets) throws IOException {
    String pkg = basePackage + ".dto";
    for (DatasetDef ds : datasets) {
      String name = ds.name();
      if (name == null || name.isBlank()) {
        continue;
      }
      String typeName = Names.toPascal(name) + "Dto";
      TypeSpec.Builder b = TypeSpec.classBuilder(typeName).addModifiers(Modifier.PUBLIC);

      b.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());

      if (ds.fields() != null) {
        Set<String> used = new LinkedHashSet<>();
        for (FieldDef f : ds.fields()) {
          String fieldName = Names.safeJavaIdentifier(Names.toCamel(f.name()), "field");
          fieldName = ensureUnique(fieldName, used);
          used.add(fieldName);
          TypeName t = typeResolver.typeForDatasetField(f);
          FieldSpec field = FieldSpec.builder(t, fieldName, Modifier.PRIVATE).build();
          b.addField(field);
          b.addMethod(getter(fieldName, t));
          b.addMethod(setter(fieldName, t));
        }
      }

      JavaFile file = JavaFile.builder(pkg, b.build()).build();
      FileOps.writeJava(javaRoot, file);
    }
  }

  private void writeActionRequestDtos(Path javaRoot, String basePackage, List<ActionTypeDef> actions) throws IOException {
    String pkg = basePackage + ".dto";
    ClassName notNull = ClassName.get("jakarta.validation.constraints", "NotNull");
    ClassName notBlank = ClassName.get("jakarta.validation.constraints", "NotBlank");

    for (ActionTypeDef a : actions) {
      if (a.id() == null || a.id().isBlank()) {
        continue;
      }
      JsonSchemaObject schema = a.inputSchema();
      String typeName = Names.toPascal(a.id()) + "Request";
      TypeSpec.Builder b = TypeSpec.classBuilder(typeName).addModifiers(Modifier.PUBLIC);

      b.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());

      Set<String> used = new LinkedHashSet<>();
      Map<String, JsonSchemaProperty> props = schema == null ? Map.of() : schema.properties();
      for (Map.Entry<String, JsonSchemaProperty> e : props.entrySet()) {
        String rawName = e.getKey();
        JsonSchemaProperty p = e.getValue();
        String fieldName = Names.safeJavaIdentifier(Names.toCamel(rawName), "param");
        fieldName = ensureUnique(fieldName, used);
        used.add(fieldName);

        TypeName t = typeResolver.typeForSchemaProperty(p);
        FieldSpec.Builder fb = FieldSpec.builder(t, fieldName, Modifier.PRIVATE);
        if (schema != null && schema.required() != null && schema.required().contains(rawName)) {
          if (t.equals(ClassName.get(String.class))) {
            fb.addAnnotation(AnnotationSpec.builder(notBlank).build());
          } else {
            fb.addAnnotation(AnnotationSpec.builder(notNull).build());
          }
        }
        b.addField(fb.build());
        b.addMethod(getter(fieldName, t));
        b.addMethod(setter(fieldName, t));
      }

      JavaFile file = JavaFile.builder(pkg, b.build()).build();
      FileOps.writeJava(javaRoot, file);
    }
  }

  private void writeActionsService(Path javaRoot, String basePackage, List<ActionTypeDef> actions) throws IOException {
    String pkg = basePackage + ".service";
    ClassName serviceAnn = ClassName.get("org.springframework.stereotype", "Service");
    ClassName actionResult = ClassName.get(basePackage, "ActionResult");

    TypeSpec.Builder iface = TypeSpec.interfaceBuilder("ActionsService")
        .addModifiers(Modifier.PUBLIC);

    TypeSpec.Builder impl = TypeSpec.classBuilder("DefaultActionsService")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(serviceAnn)
        .addSuperinterface(ClassName.get(pkg, "ActionsService"));

    for (ActionTypeDef a : actions) {
      if (a.id() == null || a.id().isBlank()) {
        continue;
      }
      String method = Names.safeJavaIdentifier(Names.toCamel(a.id()), "action");
      ClassName reqType = ClassName.get(basePackage + ".dto", Names.toPascal(a.id()) + "Request");

      MethodSpec m = MethodSpec.methodBuilder(method)
          .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
          .returns(actionResult)
          .addParameter(reqType, "request")
          .build();
      iface.addMethod(m);

      MethodSpec implM = MethodSpec.methodBuilder(method)
          .addAnnotation(Override.class)
          .addModifiers(Modifier.PUBLIC)
          .returns(actionResult)
          .addParameter(reqType, "request")
          .addStatement("return $T.ok($S)", actionResult, "not_implemented")
          .build();
      impl.addMethod(implM);
    }

    FileOps.writeJava(javaRoot, JavaFile.builder(pkg, iface.build()).build());
    FileOps.writeJava(javaRoot, JavaFile.builder(pkg, impl.build()).build());
  }

  private void writeControllers(Path javaRoot, String basePackage, List<ActionTypeDef> actions) throws IOException {
    String pkg = basePackage + ".controller";
    ClassName rest = ClassName.get("org.springframework.web.bind.annotation", "RestController");
    ClassName requestMapping = ClassName.get("org.springframework.web.bind.annotation", "RequestMapping");
    ClassName postMapping = ClassName.get("org.springframework.web.bind.annotation", "PostMapping");
    ClassName getMapping = ClassName.get("org.springframework.web.bind.annotation", "GetMapping");
    ClassName requestBody = ClassName.get("org.springframework.web.bind.annotation", "RequestBody");
    ClassName modelAttribute = ClassName.get("org.springframework.web.bind.annotation", "ModelAttribute");
    ClassName valid = ClassName.get("jakarta.validation", "Valid");
    ClassName actionResult = ClassName.get(basePackage, "ActionResult");
    ClassName actionsService = ClassName.get(basePackage + ".service", "ActionsService");

    Map<String, List<ActionTypeDef>> byController = groupActions(actions);

    for (Map.Entry<String, List<ActionTypeDef>> e : byController.entrySet()) {
      String controllerName = e.getKey();
      List<ActionTypeDef> list = e.getValue();

      FieldSpec serviceField = FieldSpec.builder(actionsService, "actionsService", Modifier.PRIVATE, Modifier.FINAL).build();
      MethodSpec ctor = MethodSpec.constructorBuilder()
          .addModifiers(Modifier.PUBLIC)
          .addParameter(actionsService, "actionsService")
          .addStatement("this.actionsService = actionsService")
          .build();

      TypeSpec.Builder c = TypeSpec.classBuilder(controllerName)
          .addModifiers(Modifier.PUBLIC)
          .addAnnotation(rest)
          .addAnnotation(AnnotationSpec.builder(requestMapping).addMember("value", "$S", "/api").build())
          .addField(serviceField)
          .addMethod(ctor);

      for (ActionTypeDef a : list) {
        if (a.id() == null || a.id().isBlank()) {
          continue;
        }
        String methodName = Names.safeJavaIdentifier(Names.toCamel(a.id()), "action");
        ClassName reqType = ClassName.get(basePackage + ".dto", Names.toPascal(a.id()) + "Request");

        boolean isGet = isQueryKind(a) && typeResolver.isGetFriendly(a.inputSchema());
        String subPath = "/" + a.id();

        MethodSpec.Builder m = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(actionResult);

        if (isGet) {
          m.addAnnotation(AnnotationSpec.builder(getMapping).addMember("value", "$S", subPath).build());
          ParameterSpec p = ParameterSpec.builder(reqType, "request")
              .addAnnotation(modelAttribute)
              .addAnnotation(valid)
              .build();
          m.addParameter(p);
        } else {
          m.addAnnotation(AnnotationSpec.builder(postMapping).addMember("value", "$S", subPath).build());
          ParameterSpec p = ParameterSpec.builder(reqType, "request")
              .addAnnotation(requestBody)
              .addAnnotation(valid)
              .build();
          m.addParameter(p);
        }

        m.addStatement("return actionsService.$L(request)", methodName);
        c.addMethod(m.build());
      }

      FileOps.writeJava(javaRoot, JavaFile.builder(pkg, c.build()).build());
    }
  }

  private static boolean isQueryKind(ActionTypeDef a) {
    String kind = a.kind();
    if (kind == null || kind.isBlank()) {
      return false;
    }
    return "query".equalsIgnoreCase(kind);
  }

  private static Map<String, List<ActionTypeDef>> groupActions(List<ActionTypeDef> actions) {
    Map<String, List<ActionTypeDef>> by = new LinkedHashMap<>();
    for (ActionTypeDef a : actions) {
      String aggregate = a.aggregate();
      String key = (aggregate == null || aggregate.isBlank()) ? null : aggregate;
      if (key == null) {
        String entity = a.entityName();
        key = (entity == null || entity.isBlank()) ? "Actions" : Names.toPascal(entity);
      }
      String controller = Names.toPascal(key) + "Controller";
      by.computeIfAbsent(controller, k -> new ArrayList<>()).add(a);
    }
    return by;
  }

  private static String ensureUnique(String base, Set<String> used) {
    if (!used.contains(base)) {
      return base;
    }
    int i = 2;
    while (used.contains(base + i)) {
      i++;
    }
    return base + i;
  }

  private static MethodSpec getter(String fieldName, TypeName type) {
    String m = (type.equals(TypeName.BOOLEAN) ? "is" : "get") + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    return MethodSpec.methodBuilder(m)
        .addModifiers(Modifier.PUBLIC)
        .returns(type)
        .addStatement("return $L", fieldName)
        .build();
  }

  private static MethodSpec setter(String fieldName, TypeName type) {
    String m = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    return MethodSpec.methodBuilder(m)
        .addModifiers(Modifier.PUBLIC)
        .returns(void.class)
        .addParameter(type, fieldName)
        .addStatement("this.$L = $L", fieldName, fieldName)
        .build();
  }
}
