#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CODEGEN_POM="${REPO_ROOT}/codegen/osi-codegen-java/pom.xml"

if [[ ! -f "${CODEGEN_POM}" ]]; then
  echo "ERROR: codegen pom not found: ${CODEGEN_POM}" >&2
  exit 2
fi

MODEL_PATH="${1:-}"
ONLY_MODEL="${2:-}"
OUTPUT_DIR="${3:-${REPO_ROOT}/generated}"

if [[ -z "${MODEL_PATH}" || -z "${ONLY_MODEL}" ]]; then
  echo "Usage:"
  echo "  bash scripts/osi_codegen.sh <model_yaml_path> <only_model_name> [output_dir]"
  echo ""
  echo "Example:"
  echo "  bash scripts/osi_codegen.sh ontologyraw/example/pp_semantic_model.yaml sap_p2p_procurement_ontology_model"
  exit 2
fi

if [[ "${MODEL_PATH}" != /* ]]; then
  MODEL_PATH="${REPO_ROOT}/${MODEL_PATH}"
fi

mkdir -p "${OUTPUT_DIR}"

mvn -q -f "${CODEGEN_POM}" exec:java \
  -Dexec.mainClass=com.ontologybizruntime.codegen.osi.Main \
  -Dexec.args="--model ${MODEL_PATH} --output ${OUTPUT_DIR} --only-model ${ONLY_MODEL} --overwrite"

OUT_DIR="${OUTPUT_DIR}/${ONLY_MODEL}-service"
if [[ ! -d "${OUT_DIR}" ]]; then
  echo "ERROR: generator finished but output dir not found: ${OUT_DIR}" >&2
  echo "HINT: please check your semantic_model.name value in YAML and pass it as <only_model_name>." >&2
  exit 3
fi

echo "OK: generated into ${OUT_DIR}"
