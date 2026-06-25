# 🔥 Roteiro de Estudo: Observabilidade (🔥 Prometheus + 📊 Grafana)

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
│ (expõe      │  scrape │ (coleta e    │  query  │ (visualiza  │
│  métricas)  │  /15s   │  armazena)   │         │  dashboards)│
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

### No Kubernetes (ServiceMonitor):

> ⚠️ **Importante:** O `kube-prometheus-stack` **NÃO usa annotations** (`prometheus.io/scrape`) para descobrir targets. Ele usa um recurso customizado chamado **ServiceMonitor**.

As annotations no Deployment são ignoradas pelo kube-prometheus-stack. Sem um ServiceMonitor, o Prometheus simplesmente não coleta métricas da sua app.

---

## 4.1. ServiceMonitor — Como o Prometheus descobre sua app

### O que é?

Um **ServiceMonitor** é um recurso customizado (CRD) do Prometheus Operator. Ele diz ao Prometheus: "existe um Service com métricas para coletar, nesta porta, neste path".

**Analogia:** Se o Prometheus é um "inspetor" que coleta dados, o ServiceMonitor é o "endereço" que você entrega para ele saber onde ir.

### Exemplo do projeto:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: crud-lab-app
  namespace: crud-lab
  labels:
    release: monitoring          # ← OBRIGATÓRIO (explicado abaixo)
spec:
  selector:
    matchLabels:
      app: crud-lab-app          # ← Encontra o Service com esta label
  namespaceSelector:
    matchNames:
      - crud-lab                 # ← Em qual namespace procurar
  endpoints:
    - port: http                 # ← Nome da porta no Service (não o número!)
      path: /actuator/prometheus # ← Path das métricas
      interval: 15s              # ← A cada 15s faz scrape
```

### Fluxo de descoberta:

```
ServiceMonitor (label: release=monitoring)
    │
    │ selector: app=crud-lab-app
    ▼
Service (name: crud-lab-app, label: app=crud-lab-app, port name: "http")
    │
    │ selector: app=crud-lab-app
    ▼
Pods (2 réplicas da app, porta 8080)
    │
    ▼
/actuator/prometheus → métricas coletadas!
```

---

## 4.2. Label `release: monitoring` — Por que é obrigatória?

O Prometheus não processa **qualquer** ServiceMonitor. Ele filtra por labels.

Ao instalar o `kube-prometheus-stack` com `helm install monitoring ...`, ele configura o Prometheus com:

```yaml
# Configuração interna do Prometheus:
serviceMonitorSelector:
  matchLabels:
    release: monitoring      # Só processa ServiceMonitors com esta label
```

**Resultado:**
- ServiceMonitor **com** `release: monitoring` → ✅ Prometheus processa
- ServiceMonitor **sem** `release: monitoring` → ❌ Prometheus ignora

Para verificar qual label o seu Prometheus exige:

```bash
kubectl get prometheus -n monitoring -o jsonpath='{.items[0].spec.serviceMonitorSelector}'
# Resultado: {"matchLabels":{"release":"monitoring"}}
```

> 💡 O nome `monitoring` vem do `helm install monitoring ...`. Se tivesse instalado com `helm install meu-prom ...`, a label seria `release: meu-prom`.

---

## 4.3. namespaceSelector — Permissão entre namespaces

### O problema

O Prometheus roda no namespace `monitoring`. A app roda no namespace `crud-lab`. Por padrão, recursos de um namespace não enxergam recursos de outro.

### Como o Prometheus resolve

O Prometheus Operator tem uma configuração chamada `serviceMonitorNamespaceSelector`:

```bash
kubectl get prometheus -n monitoring -o jsonpath='{.items[0].spec.serviceMonitorNamespaceSelector}'
```

| Valor | Significado |
|-------|-------------|
| `{}` | Monitora ServiceMonitors de **todos** os namespaces ✅ |
| `{"matchLabels":{"monitoring":"enabled"}}` | Só namespaces com a label específica |
| (campo ausente) | Só monitora o próprio namespace (`monitoring`) |

O `kube-prometheus-stack` por padrão usa `{}` (todos os namespaces), então nosso ServiceMonitor no namespace `crud-lab` é detectado sem config extra.

### Se estivesse bloqueado

Se o `serviceMonitorNamespaceSelector` exigisse uma label no namespace, você teria que:

```bash
# Adicionar label ao namespace para ser monitorado
kubectl label namespace crud-lab monitoring=enabled
```

---

## 4.4. Requisitos no Service — Label e porta nomeada

O ServiceMonitor encontra o Service via **label** e conecta na **porta pelo nome** (não pelo número).

### Service CORRETO:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: crud-lab-app
  labels:
    app: crud-lab-app        # ← ServiceMonitor usa esta label para encontrar
spec:
  selector:
    app: crud-lab-app
  ports:
    - name: http             # ← NOME obrigatório! ServiceMonitor referencia por nome
      port: 80
      targetPort: 8080
```

### Service INCORRETO (ServiceMonitor não funciona):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: crud-lab-app
  # ❌ Sem labels → ServiceMonitor não encontra
spec:
  ports:
    - port: 80               # ❌ Sem name → ServiceMonitor não sabe qual porta usar
      targetPort: 8080
```

### Checklist:

| Requisito | Onde | Exemplo |
|-----------|------|--------|
| Label no Service | `metadata.labels` | `app: crud-lab-app` |
| Nome na porta | `spec.ports[].name` | `http` |
| ServiceMonitor selector | `spec.selector.matchLabels` | `app: crud-lab-app` |
| ServiceMonitor port | `spec.endpoints[].port` | `http` (o nome, não o número) |

---

## 4.5. Targets — O que o Prometheus coleta

Um **target** é cada endpoint que o Prometheus está monitorando. Cada pod da app vira um target separado.

Para ver os targets:

1. Acesse Prometheus UI (http://localhost:9090)
2. Vá em **Status > Targets**
3. Procure o job `crud-lab-app`
4. Deve mostrar 2 targets (2 réplicas) com estado `UP`

Ou via API:

```bash
curl -s http://localhost:9090/api/v1/targets | python3 -c "
import json,sys
data=json.load(sys.stdin)
for t in data['data']['activeTargets']:
    if 'crud-lab' in str(t.get('labels',{})):
        print(f'{t[\"health\"]} → {t[\"scrapeUrl\"]}')
"
# Esperado:
# up → http://10.244.0.25:8080/actuator/prometheus
# up → http://10.244.0.26:8080/actuator/prometheus
```

### Status dos targets:

| Estado | Significado |
|--------|-------------|
| `UP` | Prometheus coletou métricas com sucesso |
| `DOWN` | Falhou ao conectar (pod morto, porta errada, path errado) |
| (não aparece) | ServiceMonitor não foi detectado (label errada ou namespace bloqueado) |

---

## 4.6. Resumo: Cadeia completa de descoberta

```
Prometheus (namespace: monitoring)
    │
    │ serviceMonitorSelector: release=monitoring
    │ serviceMonitorNamespaceSelector: {} (todos)
    ▼
ServiceMonitor (namespace: crud-lab, label: release=monitoring)
    │
    │ selector: app=crud-lab-app
    │ namespaceSelector: crud-lab
    ▼
Service (namespace: crud-lab, label: app=crud-lab-app, porta: "http")
    │
    │ selector: app=crud-lab-app
    ▼
Pods (namespace: crud-lab, label: app=crud-lab-app)
    │
    ▼
Scrape: GET :8080/actuator/prometheus a cada 15s
```

Se qualquer elo dessa cadeia estiver errado, o Prometheus não coleta métricas.

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

### Configuração do Data Source (Prometheus):

**Docker Compose:** precisa configurar manualmente:
1. Acesse Grafana (`http://localhost:3000`, admin/admin)
2. **Connections > Data Sources > Add data source**
3. Selecione **Prometheus**
4. URL: `http://prometheus:9090`
5. **Save & Test**

**Kubernetes (kube-prometheus-stack):** já vem configurado automaticamente!
O chart instala o Grafana com o datasource do Prometheus pré-configurado. Não precisa adicionar nada.

### Importar Dashboard pronto:

1. **Dashboards > New > Import**
2. ID: `4701` (JVM Micrometer)
3. Selecione o data source `Prometheus`
4. **Import**

### Corrigir datasource das variáveis (obrigatório após import):

O dashboard 4701 usa `${DS_PROMETHEUS}` como referência ao datasource. No kube-prometheus-stack essa variável não é resolvida automaticamente, fazendo os dropdowns "Application" e "Instance" ficarem vazios.

**Correção manual (via Grafana UI — versões mais antigas):**

1. No dashboard importado, clique no ⚙️ (**Settings**) no topo direito
2. Vá em **Variables**
3. Clique na variável `application`
4. No campo **Data source**, troque `${DS_PROMETHEUS}` por `Prometheus`
5. Clique **Run query** → deve aparecer `crud-k8s-lab` no Preview
6. Clique **Apply**
7. Repita para `instance` (e outras variáveis se necessário)
8. **Save dashboard** (Ctrl+S / Cmd+S)

> ⚠️ Em versões mais recentes do Grafana (11+), o ícone de engrenagem não aparece diretamente — é necessário clicar em **Edit** primeiro. Além disso, o campo "Data source" pode não estar visível na edição de variáveis. Nesses casos, **use a correção via script (recomendado)**.

**Correção via script (alternativa):**

```bash
# Port-forward do Grafana
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80 &

# Baixar, corrigir e reenviar o dashboard
DATA=$(curl -s -u admin:admin http://localhost:3000/api/dashboards/uid/$(curl -s -u admin:admin http://localhost:3000/api/search | python3 -c "import json,sys; [print(d['uid']) for d in json.load(sys.stdin) if 'JVM' in d.get('title','')]"))
echo $DATA | python3 -c "
import json,sys
data=json.load(sys.stdin)
dash=data['dashboard']
dash_str=json.dumps(dash).replace('\${DS_PROMETHEUS}','prometheus')
dash=json.loads(dash_str)
dash['id']=None
print(json.dumps({'dashboard':dash,'overwrite':True}))" | \
curl -s -u admin:admin -X POST -H 'Content-Type: application/json' -d @- http://localhost:3000/api/dashboards/db
```

Após corrigir, selecione nos dropdowns:
- **Application**: `crud-k8s-lab`
- **Instance**: qualquer IP listado

> 💡 Esse problema é comum em dashboards da comunidade. Eles são criados com variáveis de datasource que só funcionam quando exportados/importados dentro do mesmo Grafana.

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

### Kubernetes (Etapas 4-7):

```
App (pod com label app=crud-lab-app)
  ↑
Service (label: app=crud-lab-app, porta nomeada "http")
  ↑
ServiceMonitor (label: release=monitoring, selector: app=crud-lab-app)
  ↑
Prometheus (filtra por serviceMonitorSelector: release=monitoring)
  ↓ query
Grafana (:3000) ← datasource já configurado pelo kube-prometheus-stack
```

> 💡 No K8s com kube-prometheus-stack, o datasource do Prometheus já vem pré-configurado no Grafana. Não precisa adicionar manualmente.

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
3. **prometheus.yml** → entender scrape config (Docker Compose)
4. **ServiceMonitor** → entender como o Prometheus descobre apps no K8s
5. **Labels e namespaceSelector** → entender filtragem e permissões
6. **PromQL básico** → consultar métricas
7. **Grafana Data Source** → conectar Prometheus ao Grafana
8. **Dashboards** → importar e criar painéis
