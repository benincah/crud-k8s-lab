# CRUD K8s Lab — Dataprev Stack (Rancher + EFK)

> **Pré-requisito:** Conclua todas as etapas do [`README_mac.md`](README_mac.md) (etapas 1-9) antes de prosseguir.
> Certifique-se de que a stack base está rodando (`kubectl get pods -n crud-lab` todos Running).

Este guia adiciona à stack existente:
- **Rancher** — Plataforma de gerenciamento Kubernetes (substitui LENS como GUI, com RBAC, certificados TLS, multi-cluster)
- **EFK** (Elasticsearch + Fluentd + Kibana) — Centralização e busca de logs

---

## O que muda em relação à stack base?

| Componente | Stack Base | Dataprev Stack |
|-----------|-----------|---------------|
| GUI K8s | LENS (desktop app) | **Rancher** (web, com RBAC) |
| Logs | `kubectl logs` (manual) | **EFK** (centralizado, pesquisável) |
| Métricas | Prometheus + Grafana | Prometheus + Grafana (mantém) |
| Object Storage | MinIO | MinIO (mantém) |
| Banco | PostgreSQL | PostgreSQL (mantém) |
| Certificados | Nenhum (HTTP) | **cert-manager** + TLS (HTTPS para Rancher) |

---

## Pré-requisitos adicionais

Confirme que você tem recursos disponíveis no Docker Desktop:
- **Settings > Resources**: pelo menos **10GB RAM** e **4 CPUs** alocados

```bash
# Verificar nodes e recursos
kubectl top nodes 2>/dev/null || echo "metrics-server pode não estar ativo — normal"
kubectl get pods -n crud-lab
kubectl get pods -n monitoring
```

---

## Etapa A: EFK — Centralização de Logs

> 📖 **Leia:** [`docs/roteiro-elk_dataprev_stack.md`](docs/roteiro-elk_dataprev_stack.md)

### O que é EFK?

```
[App logs → stdout] → [Fluentd coleta] → [Elasticsearch armazena] → [Kibana visualiza]
```

- **Elasticsearch** — Banco de dados de busca. Armazena logs indexados.
- **Fluentd** — Agente coletor. Roda em cada node e captura logs dos containers.
- **Kibana** — Interface web para pesquisar e visualizar logs.

### Passo 1: Pull prévio das imagens pesadas

As imagens do Elasticsearch e Kibana são pesadas (~1GB cada). Baixe antes via Docker para evitar falhas de timeout:

```bash
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.13.0
docker pull docker.elastic.co/kibana/kibana:8.13.0
```

> 💡 No Docker Desktop, o Kubernetes compartilha o mesmo daemon Docker — imagens que existem no `docker images` ficam disponíveis automaticamente para os pods.

### Passo 2: Rebuild da app (logs em JSON + logging nos endpoints)

A app precisa:
- Emitir logs em formato JSON (para o Fluentd parsear)
- Ter `log.info(...)` nos endpoints (Spring Boot não loga requests HTTP por padrão)

```bash
cd crud-k8s-lab

# Build
mvn clean package -DskipTests

# Rebuild da imagem Docker
docker build -t crud-k8s-lab:latest .
docker tag crud-k8s-lab:latest localhost:5001/crud-k8s-lab:latest
docker push localhost:5001/crud-k8s-lab:latest
```

### Passo 3: Deploy do EFK

O EFK já está incluído no chart Helm (habilitado via `efk.enabled: true` no values.yaml):

```bash
# Upgrade do chart (adiciona Elasticsearch + Fluentd + Kibana)
helm upgrade crud-lab ./helm/crud-app -n crud-lab

# Aguardar pods ficarem prontos (Elasticsearch demora ~2 min)
kubectl get pods -n crud-lab -w
```

### Passo 4: Restart da app (para usar a imagem nova com logging)

```bash
kubectl rollout restart deployment crud-lab-app -n crud-lab
kubectl rollout status deployment crud-lab-app -n crud-lab --timeout=60s
```

### Passo 5: Recriar Fluentd (para pegar os novos arquivos de log)

```bash
kubectl exec -n crud-lab $(kubectl get pod -n crud-lab -l app=fluentd -o name | head -1) -- rm -f /var/log/fluentd-containers.log.pos
kubectl delete pod -n crud-lab -l app=fluentd
sleep 15
```

### Passo 6: Configurar hostname

```bash
sudo sh -c 'echo "127.0.0.1 kibana.local" >> /etc/hosts'
```

### Passo 7: Gerar logs e verificar

```bash
# Gerar tráfego
for i in $(seq 1 10); do curl -s http://crud-app.local/api/produtos > /dev/null; done
curl -X POST http://crud-app.local/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teste EFK","descricao":"Log test","preco":10}'

# Aguardar flush (5s)
sleep 10

# Verificar que o índice foi criado no Elasticsearch
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices
# Esperado: crud-app-logs-YYYY.MM.DD com documentos

# Verificar que Fluentd não tem erros
kubectl logs -n crud-lab -l app=fluentd --tail=5
```

### Passo 8: Acessar Kibana

1. Acesse http://kibana.local
2. Vá em **Management > Stack Management > Kibana > Data Views**
3. Clique **Create data view**
4. Name: `crud-app-logs`
5. Index pattern: `crud-app-logs-*`
6. Timestamp field: `@timestamp`
7. Clique **Save data view to Kibana**
8. Vá em **Discover** (menu lateral) → selecione o data view `crud-app-logs` → logs da app aparecem em tempo real

### Consultas úteis no Kibana (KQL)

```
# Todos os logs INFO
level: "INFO"

# Logs do controller de produtos
logger_name: "com.lab.crud.controller.ProdutoController"

# Busca por texto na mensagem
message: "produto"

# Combinações
level: "ERROR" AND message: "produto"
```

---

### Troubleshooting EFK

<details>
<summary>🔧 ImagePullBackOff no Elasticsearch/Kibana</summary>

Se o pull falhar com `ImagePullBackOff` ou `unexpected EOF`:

```bash
# Pull manual (mais estável que o pull interno do kubelet)
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.13.0
docker pull docker.elastic.co/kibana/kibana:8.13.0

# Recriar os pods para usar as imagens locais
kubectl delete pod -n crud-lab -l app=elasticsearch
kubectl delete pod -n crud-lab -l app=kibana
```

**Via LENS:** Workloads > Pods > selecione o pod com erro > clique no ícone de **lixeira** (Delete).
</details>

<details>
<summary>🔧 Fluentd "pattern not matched"</summary>

O Docker Desktop usa **containerd** (formato CRI). O chart já usa o parser `regexp` correto. Caso veja esse erro após modificações:

```bash
# Verificar formato real dos logs no node
kubectl exec -n crud-lab <fluentd-pod> -- head -3 /var/log/containers/*crud-lab-app*.log

# Após corrigir o ConfigMap, recriar o Fluentd
helm upgrade crud-lab ./helm/crud-app -n crud-lab
kubectl delete pod -n crud-lab -l app=fluentd
```
</details>

<details>
<summary>🔧 Fluentd "400 - Rejected by Elasticsearch"</summary>

Índice com mapeamento corrompido (logs não-JSON misturados). Para resolver:

```bash
# 1. Deletar índice corrompido (nome exato, ES 8.x não aceita wildcard)
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s -X DELETE "http://localhost:9200/crud-app-logs-YYYY.MM.DD"

# 2. Recriar Fluentd
kubectl exec -n crud-lab $(kubectl get pod -n crud-lab -l app=fluentd -o name | head -1) -- rm -f /var/log/fluentd-containers.log.pos
kubectl delete pod -n crud-lab -l app=fluentd

# 3. Gerar tráfego e verificar
sleep 15
for i in $(seq 1 5); do curl -s http://crud-app.local/api/produtos > /dev/null; done
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices
```
</details>

<details>
<summary>🔧 Kibana mostra "No results" ou "Ready to try Kibana? First, you need data"</summary>

Significa que o Elasticsearch não recebeu dados. Verifique:
1. A app tem `log.info(...)` nos endpoints (sem isso, requests HTTP não geram logs)
2. O Fluentd está rodando: `kubectl logs -n crud-lab -l app=fluentd --tail=5`
3. Gere tráfego e aguarde 10s (flush interval é 5s)
4. Confirme índice: `kubectl exec -n crud-lab deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices`
</details>

---

## Etapa B: Rancher — Gerenciamento Kubernetes

> 📖 **Leia:** [`docs/roteiro-rancher_dataprev_stack.md`](docs/roteiro-rancher_dataprev_stack.md)

### O que é Rancher?

Rancher é uma plataforma de gerenciamento Kubernetes que adiciona:
- **GUI web completa** (substitui LENS)
- **RBAC** — controle de acesso por usuário/equipe
- **Catálogo de apps** — instalar Helm charts pelo browser
- **Multi-cluster** — gerenciar múltiplos clusters (em produção)
- **Certificados TLS** — HTTPS automático via cert-manager

### Instalar cert-manager (gerenciador de certificados TLS)

O Rancher exige HTTPS. O cert-manager gera certificados automaticamente:

```bash
# Instalar cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.5/cert-manager.yaml

# Aguardar ficar pronto
kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=120s

# Verificar
kubectl get pods -n cert-manager
```

### Instalar Rancher

```bash
# Adicionar repositório Helm do Rancher
helm repo add rancher-stable https://releases.rancher.com/server-charts/stable
helm repo update

# Instalar Rancher no namespace cattle-system
helm install rancher rancher-stable/rancher \
  -n cattle-system --create-namespace \
  --set hostname=rancher.local \
  --set bootstrapPassword=admin \
  --set ingress.ingressClassName=nginx \
  --set replicas=1

# Aguardar deploy
kubectl get pods -n cattle-system -w
```

### Configurar hostname

```bash
sudo sh -c 'echo "127.0.0.1 rancher.local" >> /etc/hosts'
```

### Primeiro acesso

1. Acesse https://rancher.local (aceite o certificado autoassinado no browser)
2. Password de bootstrap: `admin`
3. Defina a nova senha de admin
4. Aceite a URL do server (https://rancher.local)
5. Pronto — você está no painel do Rancher!

### Explorar o Rancher

| Seção | O que fazer |
|-------|-------------|
| **Cluster Management** | Verá o cluster `local` (Docker Desktop) |
| **Cluster Explorer** | Clique no cluster → visão completa (substitui LENS) |
| **Workloads** | Ver pods, deployments de todos os namespaces |
| **Apps & Marketplace** | Instalar Helm charts pelo browser |
| **Users & Authentication** | Criar usuários, definir permissões (RBAC) |

### Verificar recursos criados pelo Rancher

```bash
# Namespaces criados
kubectl get ns | grep -E "cattle|cert"

# Secrets TLS (certificados gerados automaticamente)
kubectl get secrets -n cattle-system | grep tls

# RBAC — Roles criadas pelo Rancher
kubectl get clusterroles | grep rancher | head -10

# ClusterRoleBindings
kubectl get clusterrolebindings | grep rancher | head -10
```

---

## Resumo dos serviços disponíveis

| Serviço | URL | Credenciais | Protocolo |
|---------|-----|-------------|-----------|
| App (API) | http://crud-app.local/api/produtos | - | HTTP |
| pgAdmin | http://pgadmin.local | admin@admin.com / admin | HTTP |
| MinIO Console | http://minio.local | minioadmin / minioadmin | HTTP |
| Kibana (logs) | http://kibana.local | - | HTTP |
| Grafana (métricas) | http://localhost:3000 | admin / admin | HTTP (port-forward) |
| **Rancher** | https://rancher.local | admin / (sua senha) | **HTTPS** |

---

## Acessar serviços via port-forward (alternativa)

```bash
# Kibana → http://localhost:5601
kubectl port-forward -n crud-lab svc/kibana 5601:5601 &

# Elasticsearch (API) → http://localhost:9200
kubectl port-forward -n crud-lab svc/elasticsearch 9200:9200 &
```

---

## O que foi alterado nos arquivos existentes

| Arquivo | Mudança |
|---------|---------|
| `pom.xml` | Adicionada dependência `logstash-logback-encoder` |
| `src/main/java/.../ProdutoController.java` | Adicionado `Logger` com `log.info(...)` em cada endpoint |
| `src/main/resources/logback-spring.xml` | **Novo** — config de logs JSON (profile `json`) |
| `helm/crud-app/templates/deployment.yaml` | Variável `SPRING_PROFILES_ACTIVE=json` |
| `helm/crud-app/templates/efk.yaml` | **Novo** — Elasticsearch + Fluentd + Kibana (parser `regexp` para formato CRI, coleta apenas da app, `read_from_head: false`) |
| `helm/crud-app/values.yaml` | Adicionado bloco `efk` |

> ⚠️ **Nada foi removido ou substituído.** As ferramentas existentes (Prometheus, Grafana, MinIO, pgAdmin) continuam funcionando normalmente.

---

## Conceitos novos introduzidos

| Conceito | Onde aparece | Doc detalhado |
|----------|-------------|--------------|
| TLS / HTTPS | Rancher (cert-manager gera certificado) | [`docs/roteiro-rancher_dataprev_stack.md`](docs/roteiro-rancher_dataprev_stack.md) |
| cert-manager | Namespace `cert-manager` | Idem |
| RBAC (Roles, Bindings) | Rancher cria automaticamente | Idem |
| cattle-system | Namespace do Rancher | Idem |
| Secrets TLS | Certificados armazenados como Secrets | Idem |
| DaemonSet | Fluentd roda em cada node | [`docs/roteiro-elk_dataprev_stack.md`](docs/roteiro-elk_dataprev_stack.md) |
| ServiceAccount + RBAC | Fluentd precisa permissão para ler logs | Idem |
| Logs estruturados (JSON) | App emite JSON → Fluentd parseia | Idem |
| Index Patterns | Kibana organiza logs por pattern | Idem |
