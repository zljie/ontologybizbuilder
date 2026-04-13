# 模型驱动代码生成方案（Java / Spring Boot）

## Summary

基于 OSI 统一语义模型 YAML（以 [food_semantic_model.yaml](file:///Users/johnson_mac/code/ontologybizruntime/ontologyraw/example/food_semantic_model.yaml) 为输入样例），实现一个“模板生成器/脚手架生成器”，自动生成：

- Java Spring Boot（Maven）后端工程骨架
- 由 `datasets` / `fields` 生成的业务对象（DTO）
- 由 `custom_extensions.vendor_name=COMMON` 中的 `action_types` 生成的 REST Controller 接口
- 与接口同步落地的 OpenAPI 规范文件（openapi.yaml）

范围聚焦在“代码初始化”和“接口/DTO 结构生成”，不包含真实数据库访问与业务逻辑落地（仅生成可编译的 stub）。

## Current State Analysis

仓库当前仅包含规范与示例模型文件：

- 语义模型规范与行为层嵌入说明：[osi_unified_spec_zh.yaml](file:///Users/johnson_mac/code/ontologybizruntime/ontologyraw/spec/osi_unified_spec_zh.yaml)、[osi_unified_spec.md](file:///Users/johnson_mac/code/ontologybizruntime/ontologyraw/spec/osi_unified_spec.md)
- 示例语义模型输入：[food_semantic_model.yaml](file:///Users/johnson_mac/code/ontologybizruntime/ontologyraw/example/food_semantic_model.yaml) 等
- 未发现既有代码生成器、模板、Java 工程结构（无 `pom.xml` / `build.gradle` / `src/main/java`）

模型关键形态（将直接影响生成策略）：

- `semantic_model` 为数组（可能多个模型需要处理）
- `datasets[].fields[]` 不包含显式类型（仅有 `dimension.is_time` 等少量元数据）
- 行为层通过 `datasets[].custom_extensions[]` 中 `vendor_name: COMMON` 的 `data: |` 以 JSON 字符串嵌入，包含 `action_types[]` 与 `rules[]`
- `action_types[].io_schema.input_schema` 本身是 JSON Schema 风格，可用于请求 DTO 字段类型与校验生成

## Decisions (Locked)

来自需求确认：

- 目标后端框架：Spring Boot
- 构建工具：Maven
- DTO 字段类型策略：基于命名/维度推断
- 接口形态：按 action_types 生成 REST Controller，同时输出 OpenAPI 规范文件

本方案默认补充的工程决策（执行时按此落地）：

- Java 版本：17
- Spring Boot 版本：3.x（使用 Spring Web + Validation）
- 生成器实现语言：Java（单独一个 Maven 模块，产出可执行 jar）

## Proposed Changes

### 1) 建立轻量迭代看板（plan.md）

由于仓库根目录不存在 `plan.md`，执行阶段将新增一个 `plan.md` 作为轻量迭代看板：

- Backlog：新增“模型驱动代码生成器（MVP）”等卡片（含用户故事与验收标准）
- Done：验收通过后移动并勾选验收标准

文件：

- `plan.md`（仓库根目录）

### 2) 新增“生成器”模块（codegen 工具）

新增一个独立的代码生成器模块，用于读取语义模型 YAML 并输出 Spring Boot 工程：

建议目录结构（执行阶段创建）：

- `codegen/osi-codegen-java/`
  - `pom.xml`（生成器自身）
  - `src/main/java/...`
  - `src/main/resources/templates/`（非 Java 文件模板：pom、application.yml、README、openapi 等）
  - `README.md`（生成器使用说明）

依赖（在生成器模块中引入）：

- YAML 解析：SnakeYAML
- JSON 解析（custom_extensions.data）：Jackson
- Java 代码生成：JavaPoet（用于 DTO / Controller / Application 等）

核心 CLI（建议）：

- `--model <path>`：输入语义模型 YAML 路径
- `--output <dir>`：输出目录（会创建 `<output>/<modelName>-service/`）
- `--base-package <pkg>`：生成代码的根包名（默认从 modelName 推导，例如 `com.osi.generated.restaurant`）
- `--overwrite`：允许覆盖既有输出

### 3) 生成目标 Spring Boot 工程结构

输出工程（以 `food_semantic_model.yaml` 的 `restaurant_management_model` 为例）：

- `generated/restaurant_management_model-service/`
  - `pom.xml`
  - `src/main/java/<basePackage>/Application.java`
  - `src/main/java/<basePackage>/dto/...`（Dataset DTO、Action Request DTO）
  - `src/main/java/<basePackage>/controller/...`（Action Controller）
  - `src/main/java/<basePackage>/service/...`（stub：仅编译通过）
  - `src/main/resources/application.yml`
  - `openapi.yaml`

说明：

- 生成结果可以作为“初始化模板项目”直接复制/独立运行
- `generated/` 建议加入 `.gitignore`，避免生成产物污染仓库（或仅保留一个示例产物用于演示）

### 4) Dataset DTO 生成规则（datasets / fields）

为每个 `dataset` 生成一个 DTO（建议命名：`<DatasetName>Dto`，例如 `OrderItemsDto`）：

- 字段来源：`datasets[].fields[].name`
- 字段类型推断（命名/维度）：
  - `dimension.is_time=true` 或字段名以 `_time`/`_at`/`_date` 结尾：`java.time.OffsetDateTime`（或 `LocalDateTime`，执行时固定一种）
  - 以 `_id` 结尾：`String`
  - 含 `amount`/`price`/`revenue`：`java.math.BigDecimal`
  - 含 `count`/`quantity`/`qty`/`top_n`：`Integer`
  - 以 `is_` 开头：`Boolean`
  - 其他：`String`
- 可选生成 Lombok：本方案默认不依赖 Lombok（避免强依赖），使用 Java 17 record 或 POJO（执行阶段二选一并固定）

注：`fields.expression` 用于注入元数据（可选），MVP 阶段不强制生成到 Java 注解中，以保持输出工程简洁。

### 5) Action Types -> REST Controller 生成规则

读取每个 `dataset.custom_extensions` 中 `vendor_name=COMMON` 的 `data` JSON，提取 `action_types[]`：

- Controller 组织方式（建议）：
  - 按 `aggregate` 分组生成 Controller；缺失 aggregate 时按 dataset/entity_name 分组
  - 示例：`OrderController`、`DishController`
- 路径规则（确定且无需额外推断）：
  - `@RequestMapping("/api")`
  - 每个 action 生成一个端点：`/<action.id>`（action.id 自带 `/`，如 `orders/create` -> `/api/orders/create`）
- HTTP Method 规则：
  - `kind == "command"`：`POST`
  - `kind == "query"`：优先 `GET`（把 input_schema.properties 映射为 query params）；若存在复杂 object/array，则退化为 `POST`
  - kind 缺失：默认 `POST`
- Request DTO：
  - 从 `io_schema.input_schema` 生成 `<ActionIdPascal>Request`（如 `OrdersCreateRequest`）
  - `required[]` -> `@NotNull`（字符串可用 `@NotBlank`，执行时固定策略）
  - `additionalProperties:false` -> DTO 不接收未声明字段（Spring 默认忽略未知字段；如需严格，可在执行时统一配置 ObjectMapper）
- Response：
  - MVP 默认返回统一 `ActionResult`（包含 `success`、`message`、`data`），或对 query 返回 `List<Map<String,Object>>` 占位
  - 业务真实返回结构不在本次范围

### 6) 同步生成 OpenAPI 规范（openapi.yaml）

为所有 action 生成 OpenAPI 3.0/3.1 文档：

- `paths` 与 Controller 完全一致（同 path、同 method）
- requestBody / parameters 与 `io_schema.input_schema` 同步（GET -> parameters，POST -> requestBody）
- components.schemas：
  - 为每个 Request DTO 输出对应 schema
  - 基础类型映射：string/number/integer/boolean/object
- tags：
  - 使用 action.tags 或 aggregate 生成 tag 分组

## Assumptions & Edge Cases

- `semantic_model` 为数组：生成器默认对每个 model 输出一个 `<modelName>-service/` 子工程；也支持 CLI 指定只生成某个 model
- `custom_extensions.data` 可能不是合法 JSON（缩进/注释等）：生成器在解析失败时给出清晰错误并跳过该 extension（MVP 失败即退出也可，执行时固定策略）
- `action_types` 字段可能缺失 `kind/operation/aggregate/entity_name`：生成器使用稳健默认值并确保生成代码可编译
- DTO 类型推断可能与真实数据类型不一致：MVP 以“可用/可编译”为优先，后续可通过在 fields 扩展 type 元数据来增强

## Verification Steps (Execution Phase)

1. 用 `food_semantic_model.yaml` 运行生成器，生成 `generated/restaurant_management_model-service/`
2. 检查生成内容：
   - 每个 dataset 有对应 DTO
   - 每个 action_type 有对应 Controller 方法与 Request DTO
   - `openapi.yaml` 中 paths 与代码端点一致
3. 在生成项目目录执行 `mvn -q -DskipTests package`，确保编译通过
4. （可选）启动生成项目，访问 swagger-ui 或导出 openapi 文件校验

