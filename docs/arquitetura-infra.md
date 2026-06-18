# Arquitetura da Infraestrutura — CRUD K8s Lab

## Visão geral

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              KUBERNETES CLUSTER (Docker Desktop)                      │
│                                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                        NAMESPACE: ingress-nginx                                  │ │
│  │                                                                                  │ │
│  │   ┌─────────────────────────┐                                                   │ │
│  │   │  Ingress Controller     │ ← Porta 80 exposta para o host (LoadBalancer)      │ │
│  │   │  (nginx)                │                                                    │ │
│  │   └────────┬────────────────┘                                                   │ │
│  │            │ Roteia por hostname                                                  │ │
│  └────────────┼─────────────────────────────────────────────────────────────────────┘ │
│               │                                                                      │
│               ├── crud-app.local ──────────────────────┐                             │
│               ├── pgadmin.local ───────────────────┐   │                             │
│               └── minio.local ─────────────────┐   │   │                             │
│                                                │   │   │                             │
│  ┌─────────────────────────────────────────────┼───┼───┼───────────────────────────┐ │
│  │                        NAMESPACE: crud-lab  │   │   │                            │ │
│  │                                             │   │   │                            │ │
│  │   ┌─────────────────┐                      │   │   │                            │ │
│  │   │ Secret          │                      │   │   │                            │ │
│  │   │ postgres-secret │                      │   │   │                            │ │
│  │   │ ┌─────────────┐ │                      │   │   │                            │ │
│  │   │ │ DB/USER/PASS │ │                      │   │   │                            │ │
│  │   │ └──────┬──────┘ │                      │   │   │                            │ │
│  │   └────────┼────────┘                      │   │   │                            │ │
│  │            │ envFrom                        │   │   │                            │ │
│  │            ▼                                │   │   │                            │ │
│  │   ┌─────────────────┐    :5432     ┌───────┼───┼───┼──────────────────────┐     │ │
│  │   │ PostgreSQL      │◄─────────────│  App Java (Spring Boot)  ×2 réplicas │     │ │
│  │   │ (postgres:16)   │              │                                      │     │ │
│  │   │                 │              │  svc/crud-lab-app :80 → :8080  ◄──────┼─────┤ │
│  │   │ svc/postgres    │    :9000     │                                      │     │ │
│  │   │   :5432         │  ┌──────────►│  Endpoints:                          │     │ │
│  │   └─────────────────┘  │           │   - /api/produtos (CRUD)             │     │ │
│  │                        │           │   - /api/produtos/{id}/upload         │     │ │
│  │   ┌─────────────────┐  │           │   - /actuator/prometheus (métricas)  │     │ │
│  │   │ MinIO           │◄─┘           │   - /actuator/health (probes)        │     │ │
│  │   │ (object storage)│              └──────────────────────────────────────┘     │ │
│  │   │                 │                                                           │ │
│  │   │ svc/minio       │              ┌──────────────────────────────────────┐     │ │
│  │   │   :9000 (API)   │              │ pgAdmin                              │     │ │
│  │   │   :9001 (Console)◄─────────────│ (cliente visual do PostgreSQL)       │     │ │
│  │   └────────┬────────┘              │                                      │     │ │
│  │            │                        │ svc/pgadmin :5050 → :80      ◄──────┼─────┤ │
│  │            │ :9001                  │                                      │     │ │
│  │            ◄────────────────────────│ Conecta em: postgres:5432            │     │ │
│  │            │                        └──────────────────────────────────────┘     │ │
│  │            │                                                                     │ │
│  │            ◄─────────────────────── Ingress minio.local ─────────────────────────┤ │
│  │                                                                                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                      │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐ │
│  │                        NAMESPACE: monitoring                                      │ │
│  │                                                                                   │ │
│  │   ┌─────────────────┐   scrape :8080      ┌─────────────────┐                   │ │
│  │   │ Prometheus       │─ ─ ─ ─ ─ ─ ─ ─ ─ ─▶ │ App (crud-lab ns)│                   │ │
│  │   │                  │  /actuator/prometheus │                 │                   │ │
│  │   │ svc/monitoring-  │                      └─────────────────┘                   │ │
│  │   │ kube-prometheus- │                                                            │ │
│  │   │ prometheus :9090 │◄──────────┐                                                │ │
│  │   └─────────────────┘           │ datasource                                     │ │
│  │                                  │                                                │ │
│  │   ┌─────────────────┐           │                                                │ │
│  │   │ Grafana          ├───────────┘                                                │ │
│  │   │                  │                                                            │ │
│  │   │ svc/monitoring-  │                                                            │ │
│  │   │ grafana :80      │                                                            │ │
│  │   └─────────────────┘                                                            │ │
│  │                                                                                   │ │
│  │   ┌─────────────────┐   ┌──────────────────┐   ┌─────────────────┐              │ │
│  │   │ Alertmanager     │   │ Kube State       │   │ Node Exporter   │              │ │
│  │   │ :9093            │   │ Metrics :8080    │   │ :9100           │              │ │
│  │   └─────────────────┘   └──────────────────┘   └─────────────────┘              │ │
│  │                                                                                   │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                      │
└──────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              SEU MAC (host)                                            │
│                                                                                      │
│   /etc/hosts:                                                                        │
│     127.0.0.1  crud-app.local                                                        │
│     127.0.0.1  pgadmin.local                                                         │
│     127.0.0.1  minio.local                                                           │
│                                                                                      │
│   Browser:                              Port-forward (quando necessário):             │
│     http://crud-app.local  ──┐            kubectl port-forward ... grafana 3000:80    │
│     http://pgadmin.local   ──┼─→ :80 ──→ Ingress Controller                         │
│     http://minio.local     ──┘            kubectl port-forward ... prometheus 9090    │
│                                                                                      │
│   Registry local:                                                                    │
│     localhost:5001 ──→ container registry:2 (porta 5001:5000)                        │
│                                                                                      │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Fluxo de comunicação entre componentes

### 1. Requisição HTTP do usuário

```
Browser → crud-app.local:80 → Ingress Controller → svc/crud-lab-app:80 → Pod app:8080
```

### 2. App acessando o banco de dados

```
Pod app → svc/postgres:5432 → Pod postgres
         (DNS interno: "postgres")
```

### 3. App fazendo upload para MinIO

```
Pod app → svc/minio:9000 → Pod minio
         (DNS interno: "minio")
```

### 4. Prometheus coletando métricas da app

```
Pod prometheus → svc/crud-lab-app:8080/actuator/prometheus → Pod app
                (scrape via annotations no Deployment)
```

### 5. Grafana consultando Prometheus

```
Pod grafana → svc/prometheus:9090 → Pod prometheus
             (datasource configurado como http://monitoring-kube-prometheus-prometheus:9090)
```

### 6. pgAdmin acessando PostgreSQL

```
Pod pgadmin → svc/postgres:5432 → Pod postgres
             (configurado manualmente no primeiro acesso)
```

---

## Tabela de Services e portas

### Namespace: crud-lab

| Service | Porta | Destino | Acesso externo |
|---------|-------|---------|---------------|
| crud-lab-app | 80 | Pod app:8080 | Ingress `crud-app.local` |
| postgres | 5432 | Pod postgres:5432 | Apenas interno |
| minio | 9000 (API), 9001 (Console) | Pod minio | Ingress `minio.local` (console) |
| pgadmin | 5050 | Pod pgadmin:80 | Ingress `pgadmin.local` |

### Namespace: monitoring

| Service | Porta | Destino | Acesso externo |
|---------|-------|---------|---------------|
| monitoring-grafana | 80 | Pod grafana:3000 | port-forward :3000 |
| monitoring-kube-prometheus-prometheus | 9090 | Pod prometheus:9090 | port-forward :9090 |
| monitoring-kube-prometheus-alertmanager | 9093 | Pod alertmanager:9093 | port-forward :9093 |

### Namespace: ingress-nginx

| Service | Porta | Destino | Acesso externo |
|---------|-------|---------|---------------|
| ingress-nginx-controller | 80, 443 | Pod nginx:80,443 | LoadBalancer (exposto no host) |

---

## DNS interno do Kubernetes

Dentro do cluster, cada Service ganha um DNS automático:

```
<service-name>.<namespace>.svc.cluster.local
```

Na prática, dentro do mesmo namespace basta usar o nome do service:

| De (pod) | Para (service) | DNS usado | Exemplo |
|----------|---------------|-----------|---------|
| app | postgres | `postgres` | `jdbc:postgresql://postgres:5432/crudlab` |
| app | minio | `minio` | `http://minio:9000` |
| pgadmin | postgres | `postgres` | Host: `postgres`, Port: `5432` |
| grafana | prometheus | `monitoring-kube-prometheus-prometheus` | Mesmo namespace, usa nome completo |

> 💡 Quando dois pods estão no **mesmo namespace**, basta o nome do Service.
> Quando estão em **namespaces diferentes**, use: `<service>.<namespace>.svc.cluster.local`

---

## Resumo visual simplificado

```
                    ┌─────── INTERNET / SEU BROWSER ───────┐
                    │                                       │
                    │  crud-app.local                       │
                    │  pgadmin.local                        │
                    │  minio.local                          │
                    └──────────────┬────────────────────────┘
                                   │ :80
                                   ▼
                    ┌──────────────────────────────┐
                    │     INGRESS CONTROLLER       │
                    │         (nginx)              │
                    └──┬──────────┬──────────┬─────┘
                       │          │          │
              crud-app.local  pgadmin.local  minio.local
                       │          │          │
                       ▼          ▼          ▼
                 ┌─────────┐ ┌────────┐ ┌────────┐
                 │ App ×2  │ │pgAdmin │ │ MinIO  │
                 │ :8080   │ │ :80    │ │ :9001  │
                 └──┬───┬──┘ └───┬────┘ └────────┘
                    │   │        │
               :5432│   │:9000   │:5432
                    │   │        │
                    ▼   ▼        ▼
              ┌──────┐ ┌──────┐ ┌──────┐
              │Postgres│MinIO │ │Postgres│ (mesmo pod)
              └──────┘ └──────┘ └──────┘

         ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─

         NAMESPACE: monitoring (acesso via port-forward)

              ┌────────────┐      ┌─────────┐
              │ Prometheus │─────▶│  App    │ (scrape métricas)
              │   :9090    │      │  :8080  │
              └─────┬──────┘      └─────────┘
                    │
                    │ datasource
                    ▼
              ┌────────────┐
              │  Grafana   │
              │   :3000    │
              └────────────┘
```

---

## Ciclo de vida dos dados

| Dado | Onde fica | Persiste após restart? |
|------|-----------|----------------------|
| Produtos (registros) | PostgreSQL (emptyDir) | ❌ Perde ao deletar o pod |
| Arquivos (uploads) | MinIO (emptyDir) | ❌ Perde ao deletar o pod |
| Métricas | Prometheus (emptyDir) | ❌ Perde ao deletar o pod |
| Dashboards Grafana | Grafana (configuração interna) | ❌ Perde ao deletar o pod |

> ⚠️ **Este é um ambiente de aprendizado.** Em produção, cada um desses usaria `PersistentVolumeClaim` para sobreviver a reinicializações.
