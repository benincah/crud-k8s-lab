# Roteiro de Estudo: Observabilidade (Prometheus + Grafana)

## 1. O que é Observabilidade?

**Analogia:** É o "painel do carro" da sua aplicação. Sem ele, você dirige no escuro — não sabe se o motor está quente, se o combustível está acabando, ou se algo vai quebrar.

Observabilidade responde 3 perguntas:
- **Métricas:** "Quantas requisições por segundo? Qual o tempo de resposta?"
- **Logs:** "O que aconteceu? Qual erro ocorreu?"
- **Traces:** "Qual caminho a requisição percorreu entre os serviços?"

Neste projeto focamos em **métricas** (Prometheus + Grafana).

---

## 2. Arquitetura da Observabilidade no projeto

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│  crud-app   │ ──────► │  Prometheus  │ ──────► │   Grafana   │
│ (expõe      │  scrape  │ (coleta e    │  query   │ (visualiza  │
│  métricas)  │  /15s    │  armazena)   │         │  dashboards)│
└─────────────┘         └──────────────┘         └─────────────┘
  :8080/actuator/          :9090                    :3000
  prometheus
```

| Componente | Papel | Porta |
|------------|-------|-------|
| App (Spring Boot + Micrometer) | Expõe métricas no formato Prometheus | 8080 |
| Prometheus | Coleta (scrape) métricas periodicamente | 9090 |
| Grafana | Visualiza métricas em dashboards | 3000 |

---

## 3. Como a App expõe métricas

### Dependências no pom.xml:

```xml
<!-- Actuator: expõe endpoints de saúde e métricas -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer: formata métricas no padrão Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Configuração no application.yml:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus    # Quais endpoints expor
  metrics:
    tags:
      application: crud-k8s-lab            # Tag em todas as métricas
```

### Resultado — endpoint /actuator/prometheus:

```bash
curl http://localhost:8080/actuator/prometheus
```

Saída (exemplo):
```
# HELP jvm_memory_used_bytes The amount of used memory
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 2.5165824E7
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 1.2345678E7

# HELP http_server_requests_seconds Duration of HTTP server request handling
http_server_requests_seconds_count{method="GET",uri="/api/produtos",status="200"} 42
http_server_requests_seconds_sum{method="GET",uri="/api/produtos",status="200"} 0.856

# HELP process_cpu_usage The recent cpu usage for the JVM process
process_cpu_usage 0.023
```

Cada linha é uma métrica que o Prometheus coleta.

---

## 4. Prometheus — Coleta e armazena métricas

### O que faz:
- A cada 15 segundos, faz HTTP GET no `/actuator/prometheus` da app
- Armazena as métricas com timestamp (banco de séries temporais)
- Permite consultas via PromQL

### Configuração (prometheus.yml do projeto):

```yaml
global:
  scrape_interval: 15s          # A cada 15s, coleta métricas

scrape_configs:
  - job_name: 'crud-app'                    # Nome do job
    metrics_path: /actuator/prometheus       # Endpoint para coletar
    static_configs:
      - targets: ['app:8080']               # Endereço da app (Docker Compose)
```

### No Kubernetes (auto-discovery via annotations):

```yaml
# No Deployment da app (k8s/02-app.yaml):
metadata:
  annotations:
    prometheus.io/scrape: "true"              # "Prometheus, me monitore!"
    prometheus.io/path: /actuator/prometheus   # Endpoint das métricas
    prometheus.io/port: "8080"                 # Porta
```

O Prometheus do kube-prometheus-stack descobre automaticamente pods com essas annotations.

### PromQL — Linguagem de consulta:

```promql
# Total de requisições HTTP
http_server_requests_seconds_count

# Requisições por segundo (rate nos últimos 5 min)
rate(http_server_requests_seconds_count[5m])

# Tempo médio de resposta
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])

# Uso de memória heap
jvm_memory_used_bytes{area="heap"}

# CPU da JVM
process_cpu_usage
```

Acesse `http://localhost:9090` → aba "Graph" → cole uma query.

---

## 5. Grafana — Dashboards visuais

### Configurar Data Source (Prometheus):

1. Acesse Grafana (`http://localhost:3000`, admin/admin)
2. **Connections > Data Sources > Add data source**
3. Selecione **Prometheus**
4. URL:
   - Docker Compose: `http://prometheus:9090`
   - Kubernetes: `http://monitoring-kube-prometheus-prometheus.monitoring:9090`
5. **Save & Test**

### Importar Dashboard pronto:

1. **Dashboards > Import**
2. ID: `4701` (JVM Micrometer)
3. Selecione o data source Prometheus
4. **Import**

Dashboards úteis para importar:

| ID | Nome | Mostra |
|----|------|--------|
| 4701 | JVM Micrometer | Memória, CPU, threads, GC da JVM |
| 11378 | Spring Boot Statistics | Requisições HTTP, latência, erros |
| 1860 | Node Exporter | Métricas do servidor (CPU, disco, rede) |

### Criar painel customizado:

1. **Dashboards > New > New Dashboard > Add visualization**
2. Data source: Prometheus
3. Query: `rate(http_server_requests_seconds_count{uri="/api/produtos"}[5m])`
4. Título: "Requisições /api/produtos por segundo"
5. **Apply**

---

## 6. Métricas importantes para monitorar

### Aplicação (RED method):

| Métrica | O que monitora | Query PromQL |
|---------|---------------|--------------|
| **R**ate | Requisições por segundo | `rate(http_server_requests_seconds_count[5m])` |
| **E**rrors | Taxa de erros (5xx) | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` |
| **D**uration | Tempo de resposta | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` |

### JVM:

| Métrica | Query |
|---------|-------|
| Memória heap usada | `jvm_memory_used_bytes{area="heap"}` |
| CPU | `process_cpu_usage` |
| Threads ativas | `jvm_threads_live_threads` |
| GC pauses | `rate(jvm_gc_pause_seconds_sum[5m])` |

### Infraestrutura (Kubernetes):

| Métrica | O que monitora |
|---------|---------------|
| `container_cpu_usage_seconds_total` | CPU do container |
| `container_memory_usage_bytes` | Memória do container |
| `kube_pod_status_phase` | Status dos pods |

---

## 7. Alertas (bônus)

O kube-prometheus-stack já vem com alertas pré-configurados. Exemplos:

- Pod reiniciando muitas vezes (CrashLoopBackOff)
- Uso de CPU > 80%
- Disco cheio
- Target down (app parou de responder)

Acesse Prometheus → aba "Alerts" para ver os alertas ativos.

---

## 8. Fluxo completo no projeto

### Docker Compose (Etapa 2):

```
App (:8080/actuator/prometheus)
  ↓ scrape a cada 15s
Prometheus (:9090) ← prometheus.yml configura o target
  ↓ query
Grafana (:3000) ← dashboard importado (ID 4701)
```

### Kubernetes (Etapas 4-5):

```
App (pod com annotations prometheus.io/*)
  ↓ auto-discovery
kube-prometheus-stack (Prometheus no namespace monitoring)
  ↓ query
Grafana (:30300) ← dashboards pré-instalados + custom
```

---

## 9. Testando a observabilidade

```bash
# 1. Gerar tráfego na app
for i in $(seq 1 100); do
  curl -s http://localhost:8080/api/produtos > /dev/null
done

# 2. Ver métricas brutas
curl http://localhost:8080/actuator/prometheus | grep http_server_requests

# 3. No Prometheus (http://localhost:9090), consultar:
#    rate(http_server_requests_seconds_count[1m])

# 4. No Grafana, ver o gráfico subir em tempo real
```

---

## Ordem de estudo sugerida

1. **Actuator + Micrometer** → entender como a app expõe métricas
2. **Formato Prometheus** → entender o formato texto das métricas
3. **prometheus.yml** → entender scrape config
4. **PromQL básico** → consultar métricas
5. **Grafana Data Source** → conectar Prometheus ao Grafana
6. **Dashboards** → importar e criar painéis
7. **Annotations K8s** → auto-discovery no cluster
