# osi-codegen-java

用 OSI 语义模型 YAML 生成 Spring Boot（Maven）工程骨架、DTO、按 action_types 生成的 REST Controller，以及 openapi.yaml。

## Build

```bash
cd codegen/osi-codegen-java
mvn -q -DskipTests package
```

产物：

- `target/osi-codegen-java-0.1.0.jar`

## Generate

```bash
java -jar target/osi-codegen-java-0.1.0.jar \
  --model ../../ontologyraw/example/food_semantic_model.yaml \
  --output ../../generated \
  --overwrite
```

输出目录示例：

- `generated/restaurant_management_model-service/`（按 semantic_model.name 命名）
