# 项目迭代面板（plan.md）

我们用这个文件做轻量迭代看板：每次开始一个新任务就新增一条卡片；实现完成并通过验收后，将卡片移动到 Done，并勾选验收标准。

## Backlog（待办）

[OSI-CG-001] 模型驱动 Java 后端脚手架生成器（MVP）

用户故事：作为后端开发者，我希望基于 OSI 语义模型 YAML 自动生成 Spring Boot（Maven）工程骨架、DTO、按 action_types 生成的 REST Controller，以及同步的 OpenAPI 规范文件，以便快速初始化项目并保持接口与模型一致。

验收标准：

[ ] 能读取 `ontologyraw/example/food_semantic_model.yaml` 并生成一个可编译的 Spring Boot 工程到 `generated/restaurant_management_model-service/`

[ ] 每个 dataset 生成对应 DTO（字段来自 datasets.fields；类型按命名/维度推断）

[ ] 每个 action_type 生成对应 REST 端点与 Request DTO（参数来自 io_schema.input_schema；required 生成校验）

[ ] 输出 openapi.yaml，paths/method/参数/Schema 与生成的 Controller 完全一致

关联（可选）：`ontologyraw/spec/osi_unified_spec_zh.yaml`；`ontologyraw/example/food_semantic_model.yaml`

记录（可选）：创建日期 2026-04-13；备注：MVP 仅生成可编译 stub，不含真实业务与持久化。

## Done（已完成）

