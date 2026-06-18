# Roteiro de Estudo: EFK (Elasticsearch + Fluentd + Kibana)

## 1. O que é a stack EFK?

**Analogia:** Se Prometheus + Grafana é o "painel de instrumentos" (métricas numéricas), EFK é o "gravador de caixa preta" (logs completos de tudo que aconteceu).

| Componente | Papel | Analogia |
|-----------|-------|----------|
| **Elasticsearch** | Armazena e indexa logs | Banco de dados de busca (como Google para seus logs) |
| **Fluentd** | Coleta logs de todos os containers | Carteiro que pega correspondência em cada casa |
| **Kibana** | Interface web para pesquisar | Google Search, mas para seus logs |

---

## 2. Por que centralizar logs?

Sem EFK:
```bash
# Precisa saber qual pod olhar
kubectl logs crud-lab-app-7bdbdc68ff-pgbsp -n crud-lab
kubectl logs crud-lab-app-7bdbdc68ff-vpb44 -n crud-lab
# E se o pod morreu? Logs perdidos!
```

Com EFK:
- Logs de **todos os pods** em um lugar só
- Busca por texto ("erro", "NullPointer", "produto id=5")
- Logs **persistem** mesmo após pod morrer
- Filtros por tempo, nível (ERROR, INFO), pod, namespace

---

## 3. Arquitetura no projeto

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  App Pod 1      │     │  App Pod 2      │     │  Postgres Pod   │
│  (stdout: JSON) │     │  (stdout: JSON) │     │  (stdout: text) │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         │  /var/log/containers/ (logs do node)          │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │       Fluentd           │
                    │  (DaemonSet - roda em   │
                    │   cada node do cluster) │
                    └────────────┬────────────┘
                                 │ envia logs
                                 ▼
                    ┌─────────────────────────┐
                    │    Elasticsearch        │
                    │  (armazena indexado)    │
                    │  Index: crud-app-logs-* │
                    └────────────┬────────────┘
                                 │ query
                                 ▼
                    ┌─────────────────────────┐
                    │       Kibana            │
                    │  (interface de busca)   │
                    │  http://kibana.local    │
                    └─────────────────────────┘
```

---

## 4. Fluentd — O coletor

### O que é um DaemonSet?

O Fluentd roda como **DaemonSet** — isso garante que **exatamente uma cópia** roda em cada node do cluster. Diferente de um Deployment (onde você define quantas réplicas), o DaemonSet ajusta automaticamente ao número de nodes.

```yaml
apiVersion: apps/v1
kind: DaemonSet        # ← Não é Deployment!
metadata:
  name: fluentd
```

**Analogia:** Um Deployment é "contrate 3 funcionários". Um DaemonSet é "coloque 1 porteiro em cada prédio" — se construir novo prédio, automaticamente ganha porteiro.

### De onde o Fluentd lê?

O Kubernetes grava stdout/stderr de cada container em:
```
/var/log/containers/<pod-name>_<namespace>_<container-name>-<container-id>.log
```

O Fluentd monta esse diretório via `hostPath` e lê os arquivos:
```yaml
volumeMounts:
  - name: varlog
    mountPath: /var/log
volumes:
  - name: varlog
    hostPath:
      path: /var/log
```

### Formato dos logs no node (CRI vs Docker)

O formato dos logs depende do container runtime:

| Runtime | Formato do log | Exemplo |
|---------|---------------|---------|
| **containerd (CRI)** | `timestamp stream flags message` | `2026-06-18T12:00:00.123Z stdout F {"level":"INFO",...}` |
| **Docker (legacy)** | JSON puro | `{"log":"{\"level\":\"INFO\",...}","stream":"stdout","time":"..."}` |

> ⚠️ **Docker Desktop com Kubernetes usa containerd** — os logs vêm no formato CRI.
> O parser do Fluentd precisa usar `regexp` para extrair o campo `log` antes de parsear o JSON da app.

```yaml
# Parser correto para formato CRI (containerd)
<parse>
  @type regexp
  expression /^(?<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z) (?<stream>stdout|stderr) (?<flags>[^ ]*) (?<log>.*)$/
  time_format %Y-%m-%dT%H:%M:%S.%NZ
</parse>
```

Se usasse `@type json` direto, o Fluentd não conseguiria parsear e exibiria `pattern not matched` nos logs.

### RBAC do Fluentd

O Fluentd precisa consultar a API do K8s para enriquecer os logs com metadados (nome do pod, namespace, labels). Para isso, precisa de permissões:

```yaml
# 1. ServiceAccount — identidade do Fluentd
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fluentd

# 2. ClusterRole — permissões (ler pods e namespaces)
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: fluentd
rules:
  - apiGroups: [""]
    resources: ["pods", "namespaces"]
    verbs: ["get", "list", "watch"]

# 3. ClusterRoleBinding — liga a conta às permissões
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: fluentd
roleRef:
  kind: ClusterRole
  name: fluentd
subjects:
  - kind: ServiceAccount
    name: fluentd
    namespace: crud-lab
```

**Cadeia de permissão:**
```
ServiceAccount (quem?) → ClusterRoleBinding (conecta) → ClusterRole (pode o quê?)
     fluentd                                                get/list pods
```

---

## 5. Logs estruturados (JSON)

### Por que JSON?

Logs em texto puro:
```
2026-06-18 12:00:00 INFO ProdutoController - Produto criado: id=5
```

Logs em JSON:
```json
{"@timestamp":"2026-06-18T12:00:00","level":"INFO","logger":"ProdutoController","message":"Produto criado: id=5","app":"crud-k8s-lab"}
```

O JSON permite que o Fluentd/Elasticsearch **entenda a estrutura** — você pode filtrar por `level=ERROR`, buscar por `logger=ProdutoController`, etc.

### Como configuramos na app

- **Dependência**: `logstash-logback-encoder` no pom.xml
- **Config**: `logback-spring.xml` com encoder JSON
- **Ativação**: Variável `SPRING_PROFILES_ACTIVE=json` no Deployment do K8s
- **Logging explícito**: `log.info(...)` em cada endpoint do Controller (Spring Boot não loga requests HTTP automaticamente em nível INFO)
- **Dev local**: Sem a variável, logs ficam em texto legível (profile padrão)

> ⚠️ **Importante:** Sem `log.info(...)` nos endpoints, a app não gera logs ao receber requests HTTP. O EFK só funciona se a app efetivamente escrever no stdout.

---

## 6. Elasticsearch — O armazenamento

### Conceitos básicos

| Conceito | Analogia | Exemplo |
|----------|----------|---------|
| **Index** | Tabela de banco de dados | `crud-app-logs-2026.06.18` |
| **Document** | Linha na tabela | Uma entrada de log |
| **Field** | Coluna | `level`, `message`, `@timestamp` |
| **Index Pattern** | View/filtro | `crud-app-logs-*` (todos os dias) |

### Por que single-node?

Em produção, Elasticsearch roda em cluster (3+ nodes para redundância). No nosso lab, usamos `discovery.type: single-node` para simplificar.

### Segurança desabilitada

`xpack.security.enabled: false` — em produção seria `true` com autenticação. Para estudo local, simplificamos.

---

## 7. Kibana — A interface

### Criar Data View (Index Pattern)

1. Acesse http://kibana.local
2. **Management > Stack Management > Data Views**
3. **Create data view**
4. Name: `crud-app-logs`
5. Index pattern: `crud-app-logs-*`
6. Timestamp field: `@timestamp`
7. **Save**

### Discover — Pesquisar logs

1. Vá em **Discover**
2. Selecione o data view `crud-app-logs`
3. Ajuste o período (last 15 minutes, last 1 hour, etc.)
4. Use a barra de busca (KQL):

```
# Todos os erros
level: "ERROR"

# Logs de um controller específico
logger_name: "ProdutoController"

# Busca por texto livre
message: "produto"

# Combinações
level: "ERROR" AND logger_name: "ProdutoController"
```

### Criar visualização

1. **Visualize Library > Create**
2. Tipo: **Lens** (recomendado)
3. Arraste fields para criar gráficos:
   - Barras de erros por hora
   - Tabela com últimos erros
   - Contagem de logs por pod

---

## 8. Métricas vs Logs — Quando usar cada

| Preciso saber... | Ferramenta | Exemplo |
|-----------------|-----------|---------|
| "Quantas req/s?" | Prometheus + Grafana | `rate(http_server_requests[5m])` |
| "O que causou o erro 500?" | EFK + Kibana | Buscar `level:ERROR` no timestamp |
| "CPU/memória do pod?" | Prometheus + Grafana | `container_memory_usage_bytes` |
| "Qual SQL executou?" | EFK + Kibana | Buscar logs do Hibernate |
| "Pod reiniciou — por quê?" | EFK + Kibana | Logs antes do crash |

---

## 9. Troubleshooting

### Kibana não mostra logs
1. Verifique se Elasticsearch está rodando: `kubectl get pods -n crud-lab | grep elastic`
2. Verifique se Fluentd está rodando: `kubectl get pods -n crud-lab | grep fluentd`
3. Verifique se há índices: `curl http://localhost:9200/_cat/indices` (via port-forward)
4. A app precisa gerar logs (faça requisições)

### Fluentd com "pattern not matched"
→ O container runtime mudou ou o parser está errado.
→ Verifique o formato real dos logs: `kubectl exec -n crud-lab <fluentd-pod> -- head -5 /var/log/containers/*crud-lab-app*.log`
→ Se o formato for `timestamp stdout F mensagem`, use o parser `regexp` (formato CRI). Se for JSON puro, use `@type json`.
→ Após corrigir o ConfigMap, delete o pod do Fluentd para recriar: `kubectl delete pod -n crud-lab -l app=fluentd`

### Fluentd "400 - Rejected by Elasticsearch"
→ O índice tem mapeamento corrompido (logs não-JSON misturados com JSON no mesmo índice).
→ **Causa raiz:** O banner do Spring Boot e logs iniciais do logback são texto puro (não JSON). Com `read_from_head: true`, o Fluentd tentava indexar esses logs antigos e corrompia o mapeamento do Elasticsearch.
→ **Solução:**
```bash
# 1. Deletar índice corrompido (usar nome exato, sem wildcard — ES 8.x bloqueia wildcards)
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s -X DELETE "http://localhost:9200/crud-app-logs-2026.06.18"

# 2. Aplicar config corrigida
helm upgrade crud-lab ./helm/crud-app -n crud-lab

# 3. Limpar pos_file e recriar Fluentd
kubectl exec -n crud-lab $(kubectl get pod -n crud-lab -l app=fluentd -o name | head -1) -- rm -f /var/log/fluentd-containers.log.pos
kubectl delete pod -n crud-lab -l app=fluentd

# 4. Aguardar e gerar tráfego novo
sleep 15
for i in $(seq 1 5); do curl -s http://crud-app.local/api/produtos > /dev/null; done

# 5. Verificar (deve mostrar novo índice sem erros)
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices
kubectl logs -n crud-lab -l app=fluentd --tail=5
```
→ **Prevenção:** A config do Fluentd usa `read_from_head: false` para ignorar logs antigos de startup e coleta apenas da app (`crud-lab-app-*.log`), excluindo logs do próprio Elasticsearch, Kibana e Fluentd.

### Fluentd em CrashLoopBackOff
→ Verifique logs do Fluentd: `kubectl logs -n crud-lab -l app=fluentd`
→ Geralmente é permissão (RBAC) ou path de logs errado

### Elasticsearch OOMKilled
→ Aumente o limite de memória no values.yaml ou em Docker Desktop Settings > Resources

---

## Ordem de estudo sugerida

1. **Conceito** → entender a diferença entre métricas e logs
2. **Logs JSON** → entender por que estruturar logs
3. **Fluentd** → entender DaemonSet e coleta de logs
4. **RBAC** → entender ServiceAccount, Role, Binding
5. **Elasticsearch** → entender índices e busca
6. **Kibana** → criar data views, pesquisar, visualizar
7. **KQL** → dominar a linguagem de query do Kibana
