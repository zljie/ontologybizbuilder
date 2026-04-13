# OSI - Unified Metadata & Behavior Spec (Markdown)

**Version:** 0.1.2

## Goals

- **Standardization**: Establish uniform language and structure for semantic model definitions.
- **Extensibility**: Support domain-specific extensions while maintaining core compatibility.
- **Actionability (New)**: Integrate Behavior Layer (Action Types & Rules) to support AI Agent planning and execution.
- **Interoperability**: Enable exchange and reuse across different AI and BI applications.

---

## 1. Top-Level Semantic Model

The top-level container that represents a complete semantic model, including datasets, relationships, metrics, and global extensions.

```yaml
semantic_model:
  - name: salesforce_crm_ontology_model
    description: Salesforce CRM 语义本体模型
    ai_context:
      instructions: "用于 CRM 经营分析与问答..."
      synonyms: ["Salesforce", "CRM"]
      examples: ["本月新增线索数是多少？"]
    datasets: []
    relationships: []
    metrics: []
    custom_extensions: []
```

---

## 2. Datasets & Behavior Layer

Logical datasets represent business entities (fact and dimension tables). 

**New in v0.1.2**: To support LLM-driven actions and constraints, the Behavior Layer (`action_types` and `rules`) is embedded within the dataset's `custom_extensions` under the `COMMON` vendor.

```yaml
datasets:
  - name: leads
    source: salesforce.salesforce.lead
    primary_key: [lead_id]
    unique_keys:
      - [lead_sf_id]
    description: 销售线索(潜在客户)
    ai_context:
      synonyms: ["线索", "潜在客户"]
    
    # -----------------------------------------------------
    # NEW: Behavior Layer (Actions & Rules)
    # -----------------------------------------------------
    custom_extensions:
      - vendor_name: COMMON
        data: |
          {
            "namespace": "SALESFORCE_CRM",
            "behavior_layer_version": "0.1",
            "action_types": [
              {
                "id": "leads/convert",
                "title": "线索转化",
                "kind": "command",
                "operation": "convert",
                "aggregate": "Lead",
                "entity_name": "leads",
                "idempotency": "non_idempotent",
                "applies_to": { "entity": "dataset" },
                "io_schema": {
                  "input_schema": {
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["lead_id"],
                    "properties": {
                      "lead_id": { "type": "string" },
                      "create_opportunity": { "type": "boolean" }
                    }
                  }
                },
                "examples": ["将线索转化为商机和客户"],
                "tags": ["crud", "转化", "营销"]
              }
            ],
            "rules": [
              {
                "id": "crm/rule_lead_conversion",
                "title": "线索转化率计算口径",
                "severity": "warn",
                "when": { "entity": "dataset" },
                "constraint": { "type": "filter" },
                "message": "计算线索转化率时，分母应为已处理线索（status != 'New'）。",
                "tags": ["口径", "转化率"]
              }
            ]
          }
          
    # -----------------------------------------------------
    # Standard Fields
    # -----------------------------------------------------
    fields:
      - name: lead_id
        expression:
          dialects:
            - dialect: ANSI_SQL
              expression: leads.lead_id
        description: 线索内部ID
```

### 2.1 Action Types Schema
| Field | Type | Description |
|---|---|---|
| `id` | string | Unique Action ID (e.g., `leads/convert`) |
| `title` | string | Human-readable title |
| `kind` | string | `command` (write/execute) or `query` (read/fetch) |
| `operation` | string | e.g., `create`, `update`, `delete`, `convert` |
| `aggregate` | string | DDD Aggregate root |
| `io_schema` | object | Parameter schema for LLM function calling (JSON Schema format) |
| `examples` | array | Natural language triggers (crucial for semantic routing) |

### 2.2 Rules Schema
| Field | Type | Description |
|---|---|---|
| `id` | string | Unique Rule ID (e.g., `crm/rule_lead_conversion`) |
| `severity` | string | `error`, `warn`, or `info` |
| `when` | object | Scope (`entity`: dataset/metric/field, `selectors`: target names) |
| `constraint` | object | Constraint type (`filter`, `naming`, `security`) |
| `message` | string | Instruction for the LLM to obey when generating SQL or Actions |

---

## 3. Relationships

Defines how logical datasets are connected through foreign key constraints.

```yaml
relationships:
  - name: leads_to_users
    from: leads             # Many-side
    to: users               # One-side
    from_columns: [owner_id]
    to_columns: [user_id]
    ai_context:
      synonyms: ["线索负责人"]
```

---

## 4. Metrics

Quantitative measures defined on business data using SQL dialects. Rules can be bound to these metrics to enforce specific calculation logic.

```yaml
metrics:
  - name: lead_conversion_rate
    expression:
      dialects:
        - dialect: ANSI_SQL
          expression: SUM(CASE WHEN is_converted THEN 1 ELSE 0 END) / SUM(CASE WHEN status != 'New' THEN 1 ELSE 0 END)
    description: 线索转化率
    ai_context:
      synonyms: ["线索转化率"]
```
