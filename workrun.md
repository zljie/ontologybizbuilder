
执行方式：参数为sap_p2p_procurement_ontology_model ，输出指定路径generated/sap_p2p_procurement_ontology_model-service/
``` bash
mvn -q exec:java -Dexec.mainClass=com.ontologybizruntime.codegen.osi.Main -Dexec.args="--model /Users/johnson_mac/code/ontologybizruntime/ontologyraw/example/pp_semantic_model.yaml --output /Users/johnson_mac/code/ontologybizruntime/generated --only-model sap_p2p_procurement_ontology_model --overwrite"
```