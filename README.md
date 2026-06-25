# ☸️ CRUD K8s Lab

Projeto de aprendizado prático: deploy de uma aplicação ☕ Java em ☸️ Kubernetes com toda a stack de suporte — banco de dados, object storage, observabilidade, logs centralizados e gerenciamento.

---

## 🛠️ Tecnologias

| Camada | Tecnologias |
|--------|-------------|
| Aplicação | ☕ Java 17, 🍃 Spring Boot, 🏗️ Maven |
| Banco de dados | 🐘 PostgreSQL 16, 🖥️ pgAdmin |
| Object Storage | 🪣 MinIO |
| Containerização | 🐳 Docker, 🐳 Docker Compose, 🐳 Dockerfile multi-stage |
| Orquestração | ☸️ Kubernetes (K3s / Docker Desktop) |
| Deploy | ⎈ Helm Charts, 📄 YAMLs puros |
| Observabilidade | 🔥 Prometheus, 📊 Grafana, 📡 ServiceMonitor, 📏 Micrometer |
| Logs centralizados | 🔍 Elasticsearch, 🌊 Fluentd, 📈 Kibana (EFK) |
| Gerenciamento | 🐄 Rancher, 🔐 RBAC, 🔒 cert-manager, 🔒 TLS |
| Ferramentas visuais | 🔭 LENS, 🖥️ k9s |
| Ingress | 🚦 Traefik (K3s) / 🚦 NGINX (Docker Desktop) |

---

## 📚 Etapas de aprendizado

| Etapa | Conceitos |
|-------|-----------|
| 1 | 🏗️ Maven, build ☕ Java, estrutura 🍃 Spring Boot |
| 2 | 🐳 Docker Compose, multi-container, variáveis de ambiente |
| 3 | 🐳 Dockerfile multi-stage, registry de imagens |
| 4 | ⎈ Helm repos, charts de terceiros, 🔥 Prometheus, 📊 Grafana, 📡 ServiceMonitor |
| 5 | ☸️ Deployment, Service, Ingress, Secret, Namespace (📄 YAMLs) |
| 6 | 🧹 Limpeza de recursos ☸️ K8s |
| 7 | ⎈ Helm charts próprios, templates, values, upgrade, rollback |
| 8 | 🪣 MinIO, bucket, upload via API |
| 9 | 🔭 LENS como ferramenta visual para ☸️ K8s |
| 10 | 🔍 EFK, logs centralizados, DaemonSet, 🔍 Elasticsearch, 📈 Kibana |
| 11 | 🐄 Rancher, 🔐 RBAC, 🔒 TLS, 🔒 cert-manager, catálogo de apps |

---

## 📁 Estrutura do projeto

```
crud-k8s-lab/
├── src/                    # ☕ Código Java (Spring Boot)
├── Dockerfile              # 🐳 Build multi-stage da imagem
├── docker-compose.yml      # 🐳 Ambiente local completo
├── pom.xml                 # 🏗️ Dependências Maven
├── prometheus.yml          # 🔥 Config do Prometheus (Docker Compose)
├── helm/crud-app/          # ⎈ Helm chart (deploy K8s)
├── k8s/                    # 📄 YAMLs puros (para aprender)
├── docs/                   # 📚 Guias de estudo
├── README.md               # 📖 Este arquivo (visão geral)
├── README_wsl.md           # 🪟 Guia para Windows 11 + WSL
├── README_mac.md           # 🍎 Guia para macOS (Apple Silicon)
└── README_rancher_elastic.md # 🐄 Stack avançada (Rancher + EFK) para Mac
```

---

## 🗺️ Guias por plataforma

| Plataforma | Guia |
|-----------|------|
| 🪟 Windows 11 + WSL (K3s) | [`README_wsl.md`](README_wsl.md) |
| 🍎 macOS Apple Silicon (Docker Desktop) | [`README_mac.md`](README_mac.md) |
| 🐄 Stack avançada — Rancher + EFK (Mac) | [`README_rancher_elastic.md`](README_rancher_elastic.md) |

---

## 📖 Documentação de estudo

| Doc | Conteúdo |
|-----|----------|
| [`docs/arquitetura-infra.md`](docs/arquitetura-infra.md) | 🏗️ Diagrama da infraestrutura completa |
| [`docs/roteiro-springboot.md`](docs/roteiro-springboot.md) | 🍃 Código Java, Spring Boot, anotações |
| [`docs/roteiro-maven.md`](docs/roteiro-maven.md) | 🏗️ Maven, pom.xml, dependências, lifecycle |
| [`docs/roteiro-docker.md`](docs/roteiro-docker.md) | 🐳 Dockerfile, Docker Compose |
| [`docs/roteiro-kubernetes.md`](docs/roteiro-kubernetes.md) | ☸️ Conceitos K8s (Pod, Deployment, Service, Ingress) |
| [`docs/roteiro-helm.md`](docs/roteiro-helm.md) | ⎈ Helm charts, templates, comandos |
| [`docs/roteiro-observabilidade.md`](docs/roteiro-observabilidade.md) | 🔥 Prometheus, 📊 Grafana, 📡 ServiceMonitor |
| [`docs/roteiro-minio.md`](docs/roteiro-minio.md) | 🪣 Object storage, buckets, upload |
| [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md) | 🚦 Port-forward vs Ingress |
| [`docs/roteiro-efk.md`](docs/roteiro-efk.md) | 🔍 EFK, logs centralizados, 📈 Kibana |
| [`docs/roteiro-rancher.md`](docs/roteiro-rancher.md) | 🐄 Rancher, 🔐 RBAC, 🔒 TLS, cert-manager |
| [`docs/roteiro-deploy-via-rancher.md`](docs/roteiro-deploy-via-rancher.md) | 🐄 Deploy completo da stack via GUI do Rancher (sem terminal) |
| [`docs/roteiro-lens.md`](docs/roteiro-lens.md) | 🔭 LENS, 🖥️ k9s, GUI para Kubernetes |
| [`docs/roteiro-postgresql.md`](docs/roteiro-postgresql.md) | 🐘 PostgreSQL, configuração, pgAdmin |
| [`docs/roteiro-setup_mac.md`](docs/roteiro-setup_mac.md) | 🍎 Setup detalhado do ambiente Mac |

---

## 🌐 Resumo dos serviços

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| 🍃 App (API) | http://crud-app.local/api/produtos | - |
| 🖥️ pgAdmin | http://pgadmin.local | admin@admin.com / admin |
| 🪣 MinIO Console | http://minio.local | minioadmin / minioadmin |
| 📈 Kibana (logs) | http://kibana.local | - |
| 📊 Grafana (métricas) | http://localhost:3000 (port-forward) | admin / admin |
| 🐄 Rancher | https://rancher.local | admin / (sua senha) |
