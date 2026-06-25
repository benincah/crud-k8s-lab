# 🏗️ Arquitetura da Infraestrutura — CRUD K8s Lab

## Visao geral

```
+====================================================================================+
|                              KUBERNETES CLUSTER                                     |
|                                                                                     |
|  +-------------------------------------------------------------------------------+  |
|  |                     NAMESPACE: ingress (traefik / nginx)                       | |
|  |                                                                                | |
|  |   +---------------------------+                                                | |
|  |   |  Ingress Controller       | <-- Porta 80/443 exposta para o host           | |
|  |   |  (traefik ou nginx)       |                                                | |
|  |   +-------------+-------------+                                                | |
|  |                 |                                                              | |
|  |                 | Roteia por hostname:                                         | |
|  |                 |   crud-app.local -----> svc/crud-lab-app (crud-lab)          | |
|  |                 |   pgadmin.local ------> svc/pgadmin (crud-lab)               | |
|  |                 |   minio.local --------> svc/minio (crud-lab)                 | |
|  |                 |   kibana.local -------> svc/kibana (crud-lab)                | |
|  |                 |   rancher.local ------> svc/rancher (cattle-system) [HTTPS]  | |
|  |                 |                                                              | |
|  +-------------------------------------------------------------------------------+| |
|                                                                                     |
|  +-------------------------------------------------------------------------------+  |
|  |                     NAMESPACE: crud-lab                                        | |
|  |                                                                                | |
|  |   +-------------------+          +----------------------------------------+    | |
|  |   | Secret            |          | App Java (Spring Boot) x2 replicas     |    | |
|  |   | postgres-secret   |          |                                        |    | |
|  |   | +---------------+ |  envFrom | svc/crud-lab-app :80 -> :8080          |    | |
|  |   | | DB/USER/PASS  |-|--------->|                                        |    | |
|  |   | +---------------+ |          | Endpoints:                             |    | |
|  |   +-------------------+          |   /api/produtos (CRUD)                 |    | |
|  |                                  |   /api/produtos/{id}/upload            |    | |
|  |   +-------------------+  :5432   |   /actuator/prometheus (metricas)      |    | |
|  |   | PostgreSQL        |<---------|   /actuator/health (probes)            |    | |
|  |   | (postgres:16)     |          |                                        |    | |
|  |   |                   |  :9000   |                                        |    | |
|  |   | svc/postgres:5432 |    +---->|                                        |    | |
|  |   +-------------------+    |     +----------------------------------------+    | |
|  |                            |                                                   | |
|  |   +-------------------+    |     +----------------------------------------+    | |
|  |   | MinIO             |<---+     | pgAdmin                                |    | |
|  |   | (object storage)  |          | (cliente visual do PostgreSQL)         |    | |
|  |   |                   |          |                                        |    | |
|  |   | svc/minio         |          | svc/pgadmin :5050 -> :80               |    | |
|  |   |   :9000 (API)     |          | Conecta em: postgres:5432              |    | |
|  |   |   :9001 (Console) |          +----------------------------------------+    | |
|  |   +-------------------+                                                        | |
|  |                                                                                | |
|  |   +----------------------------------------------------------------------+     | |
|  |   | EFK Stack (logs centralizados)                                        |    | |
|  |   |                                                                       |    | |
|  |   |  +--------------+    +------------------+    +------------------+     |    | |
|  |   |  | Fluentd      |--->| Elasticsearch    |<---| Kibana           |     |    | |
|  |   |  | (DaemonSet)  |    | :9200            |    | :5601            |     |    | |
|  |   |  | coleta logs  |    | armazena/indexa  |    | visualiza/busca  |     |    | |
|  |   |  +--------------+    +------------------+    +------------------+     |    | |
|  |   +----------------------------------------------------------------------+     | |
|  |                                                                                | |
|  +-------------------------------------------------------------------------------+  |
|                                                                                     |
|  +-------------------------------------------------------------------------------+  |
|  |                     NAMESPACE: monitoring                                      | |
|  |                                                                                | |
|  |   +-------------------+   scrape :8080         +------------------+            | |
|  |   | Prometheus        |- - - - - - - - - - - ->| App (crud-lab)   |            | |
|  |   |                   |  /actuator/prometheus  +------------------+            | |
|  |   | svc/monitoring-   |                                                        | |
|  |   | kube-prometheus-  |                                                        | |
|  |   | prometheus :9090  |<-----------+                                           | |
|  |   +-------------------+            | datasource                                | |
|  |                                    |                                           | |
|  |   +-------------------+            |                                           | |
|  |   | Grafana           |------------+                                           | |
|  |   |                   |                                                        | |
|  |   | svc/monitoring-   |                                                        | |
|  |   | grafana :80       |                                                        | |
|  |   +-------------------+                                                        | |
|  |                                                                                | |
|  |   +----------------+   +-------------------+   +------------------+            | |
|  |   | Alertmanager   |   | Kube State        |   | Node Exporter    |            | |
|  |   | :9093          |   | Metrics :8080     |   | :9100            |            | |
|  |   +----------------+   +-------------------+   +------------------+            | |
|  |                                                                                | |
|  +-------------------------------------------------------------------------------+  |
|                                                                                     |
|  +-------------------------------------------------------------------------------+  |
|  |                     NAMESPACE: cattle-system                                   | |
|  |                                                                                | |
|  |   +-------------------+                                                        | |
|  |   | Rancher           | <-- GUI web, RBAC, catalogo de apps, multi-cluster     | |
|  |   | (HTTPS)           |                                                        | |
|  |   +-------------------+                                                        | |
|  |                                                                                | |
|  +-------------------------------------------------------------------------------+  |
|                                                                                     |
|  +-------------------------------------------------------------------------------+  |
|  |                     NAMESPACE: cert-manager                                    | |
|  |                                                                                | |
|  |   +-------------------+                                                        | |
|  |   | cert-manager      | <-- Gera certificados TLS automaticos para o Rancher   | |
|  |   +-------------------+                                                        | |
|  |                                                                                | |
|  +-------------------------------------------------------------------------------+  |
|                                                                                     |
+====================================================================================+

+====================================================================================+
|                              SEU HOST (Mac / WSL)                                   |
|                                                                                     |
|   /etc/hosts:                                                                       |
|     <IP>  crud-app.local                                                            |
|     <IP>  pgadmin.local                                                             |
|     <IP>  minio.local                                                               |
|     <IP>  kibana.local                                                              |
|     <IP>  rancher.local                                                             |
|                                                                                     |
|   Browser:                             Port-forward (quando necessario):            |
|     http://crud-app.local  --+           kubectl port-forward ... grafana 3000:80   |
|     http://pgadmin.local   --+--> :80 --> Ingress Controller                        |
|     http://minio.local     --+           kubectl port-forward ... prometheus 9090   |
|     http://kibana.local    --+                                                      |
|     https://rancher.local  -----> :443 --> Ingress Controller                       |
|                                                                                     |
|   Registry local:                                                                   |
|     localhost:5000/5001 --> container registry:2                                    |
|                                                                                     |
+====================================================================================+
```

---

## Fluxo de comunicacao entre componentes

### 1. Requisicao HTTP do usuario

```
Browser --> crud-app.local:80 --> Ingress Controller --> svc/crud-lab-app:80 --> Pod app:8080
```

### 2. App acessando o banco de dados

```
Pod app --> svc/postgres:5432 --> Pod postgres
           (DNS interno: "postgres")
```

### 3. App fazendo upload para MinIO

```
Pod app --> svc/minio:9000 --> Pod minio
           (DNS interno: "minio")
```

### 4. Prometheus coletando metricas da app

```
ServiceMonitor (crud-lab ns, label: release=monitoring)
    | selector: app=crud-lab-app
    v
Service crud-lab-app (crud-lab ns, porta "http")
    |
    v
Pod prometheus (monitoring ns) --> GET :8080/actuator/prometheus --> Pod app (crud-lab ns)
```

> O Prometheus nao usa annotations. Usa ServiceMonitor com label `release: monitoring`.

### 5. Grafana consultando Prometheus

```
Pod grafana --> svc/prometheus:9090 --> Pod prometheus
              (datasource: http://monitoring-kube-prometheus-prometheus:9090)
```

### 6. pgAdmin acessando PostgreSQL

```
Pod pgadmin --> svc/postgres:5432 --> Pod postgres
              (configurado manualmente no primeiro acesso)
```

### 7. Fluentd coletando logs -> Elasticsearch -> Kibana

```
Pod app --> stdout --> /var/log/containers/*.log
    |
    v
Pod fluentd (DaemonSet) --> le logs do node --> envia para Elasticsearch :9200
    |
    v
Pod elasticsearch --> indexa e armazena
    |
    v
Pod kibana --> consulta Elasticsearch --> exibe no browser (kibana.local)
```

### 8. Rancher (HTTPS via cert-manager)

```
Browser --> https://rancher.local:443 --> Ingress Controller --> Pod rancher
                                           |
                                           +-- certificado TLS gerado por cert-manager
```

---

## Tabela de Services e portas

### Namespace: crud-lab

| Service       | Porta                 | Destino           | Acesso externo                  |
|---------------|-----------------------|-------------------|---------------------------------|
| crud-lab-app  | 80                    | Pod app:8080      | Ingress `crud-app.local`        |
| postgres      | 5432                  | Pod postgres:5432 | Apenas interno                  |
| minio         | 9000 (API), 9001 (UI) | Pod minio         | Ingress `minio.local` (console) |
| pgadmin       | 5050                  | Pod pgadmin:80    | Ingress `pgadmin.local`         |
| elasticsearch | 9200                  | Pod elastic:9200  | Apenas interno                  |
| kibana        | 5601                  | Pod kibana:5601   | Ingress `kibana.local`          |

### Namespace: monitoring

| Service                                 | Porta | Destino               | Acesso externo     |
|-----------------------------------------|-------|-----------------------|--------------------|
| monitoring-grafana                      | 80    | Pod grafana:3000      | port-forward :3000 |
| monitoring-kube-prometheus-prometheus   | 9090  | Pod prometheus:9090   | port-forward :9090 |
| monitoring-kube-prometheus-alertmanager | 9093  | Pod alertmanager:9093 | port-forward :9093 |

### Namespace: cattle-system

| Service  | Porta | Destino         | Acesso externo                |
|----------|-------|-----------------|-------------------------------|
| rancher  | 443   | Pod rancher:443 | Ingress `rancher.local` (TLS) |

### Namespace: ingress

| Service            | Porta   | Destino        | Acesso externo                 |
|--------------------|---------|----------------|--------------------------------|
| ingress-controller | 80, 443 | Pod controller | LoadBalancer (exposto no host) |

---

## DNS interno do Kubernetes

Dentro do cluster, cada Service ganha um DNS automatico:

```
<service-name>.<namespace>.svc.cluster.local
```

Na pratica, dentro do mesmo namespace basta usar o nome do service:

| De (pod) | Para (service) | DNS usado                              | Exemplo                                   |
|----------|----------------|----------------------------------------|-------------------------------------------|
| app      | postgres       | `postgres`                             | `jdbc:postgresql://postgres:5432/crudlab` |
| app      | minio          | `minio`                                | `http://minio:9000`                       |
| pgadmin  | postgres       | `postgres`                             | Host: `postgres`, Port: `5432`            |
| fluentd  | elasticsearch  | `elasticsearch`                        | `http://elasticsearch:9200`               |
| kibana   | elasticsearch  | `elasticsearch`                        | `http://elasticsearch:9200`               |
| grafana  | prometheus     | `monitoring-kube-prometheus-prometheus`| Namespaces diferentes, nome completo      |

> Quando dois pods estao no **mesmo namespace**, basta o nome do Service.
> Quando estao em **namespaces diferentes**, use: `<service>.<namespace>.svc.cluster.local`

---

## Resumo visual simplificado

```
                  +---------- SEU BROWSER ----------+
                  |                                  |
                  |  crud-app.local    (HTTP)        |
                  |  pgadmin.local     (HTTP)        |
                  |  minio.local       (HTTP)        |
                  |  kibana.local      (HTTP)        |
                  |  rancher.local     (HTTPS)       |
                  +-----------------+----------------+
                                    | :80 / :443
                                    v
                  +----------------------------------+
                  |       INGRESS CONTROLLER         |
                  |      (traefik ou nginx)          |
                  +--+------+------+------+------+--+
                     |      |      |      |      |
                     v      v      v      v      v
                  +------+------+------+------+-------+
                  |App x2|pgAdm |MinIO |Kiban |Rancher|
                  |:8080 | :80  |:9001 |:5601 | :443  |
                  +--+---+--+---+------+--+---+-------+
                     |      |             |
                     |:5432 |:5432        |:9200
                     v      v             v
                  +------+ +------+  +-------------+
                  |Postgr| |Postgr|  |Elasticsearch|
                  +------+ +------+  +------+------+
                     ^                       ^
                     |                       |
                     | :9000          +------+------+
                     v                | Fluentd     |
                  +------+            | (DaemonSet) |
                  |MinIO |            +-------------+
                  +------+


           - - - - - - - - - - - - - - - - - - - - - -

           NAMESPACE: monitoring (acesso via port-forward)

                  +------------+       +---------+
                  | Prometheus |------>|  App    | (scrape metricas)
                  |   :9090    |       |  :8080  |
                  +------+-----+       +---------+
                         |
                         | datasource
                         v
                  +------------+
                  |  Grafana   |
                  |   :3000    |
                  +------------+
```

---

## Ciclo de vida dos dados

| Dado                 | Onde fica                      | Persiste apos restart? |
|----------------------|--------------------------------|------------------------|
| Produtos (registros) | PostgreSQL (emptyDir)          | Nao - perde ao deletar pod |
| Arquivos (uploads)   | MinIO (emptyDir)               | Nao - perde ao deletar pod |
| Metricas             | Prometheus (emptyDir)          | Nao - perde ao deletar pod |
| Logs indexados       | Elasticsearch (emptyDir)       | Nao - perde ao deletar pod |
| Dashboards Grafana   | Grafana (config interna)       | Nao - perde ao deletar pod |

> **Este e um ambiente de aprendizado.** Em producao, cada um desses usaria `PersistentVolumeClaim` para sobreviver a reinicializacoes.
