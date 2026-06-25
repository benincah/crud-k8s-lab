# Roteiro: Deploy Completo da Stack via Rancher (Sem Linha de Comando)

> **Pré-requisito:** Rancher já instalado e acessível em https://rancher.local (veja [`README_rancher_elastic.md`](../README_rancher_elastic.md) Etapa B).

Este guia mostra como fazer o deploy de toda a stack **crud-lab** usando exclusivamente a interface web do Rancher — zero `kubectl` ou `helm` no terminal.

---

## Visão geral do que será criado

| Componente | Método no Rancher |
|-----------|-------------------|
| Namespace `crud-lab` | Cluster Explorer > Namespaces |
| Secret `postgres-secret` | Storage > Secrets |
| PostgreSQL | Workloads > Deployments |
| MinIO | Workloads > Deployments |
| App Java (crud-lab-app) | Apps > Repositories + Charts **ou** Workloads > Deployments |
| pgAdmin | Workloads > Deployments |
| Services | Service Discovery > Services |
| Ingresses | Service Discovery > Ingresses |

---

## Opção A: Instalar o Helm Chart próprio via Rancher (recomendado)

Se o chart `helm/crud-app/` já está em um registry Git acessível (GitHub, por exemplo), o Rancher pode instalá-lo pela GUI.

### Passo 1: Adicionar repositório Git como fonte de charts

1. Acesse https://rancher.local → clique no cluster **local**
2. Menu lateral: **Apps > Repositories**
3. Clique **Create**
4. Preencha:
   - **Name:** `crud-lab-repo`
   - **Target:** Git Repo
   - **Git Repo URL:** `https://github.com/<seu-usuario>/crud-k8s-lab` (ou URL do seu repositório)
   - **Git Branch:** `main`
   - **Paths:** `helm/crud-app`
5. Clique **Create**
6. Aguarde o status ficar **Active**

### Passo 2: Instalar o chart

1. Vá em **Apps > Charts**
2. Na busca, digite `crud` → o chart `crud-app` deve aparecer
3. Clique nele → **Install**
4. Configure:
   - **Namespace:** Crie `crud-lab` (ou selecione se já existe)
   - **Name:** `crud-lab`
5. Na aba **Values YAML**, o conteúdo do `values.yaml` aparece. Ajuste se necessário (ex: trocar `localhost:5001` por outro registry)
6. Clique **Install**
7. Aguarde todos os pods ficarem Running (acompanhe em **Workloads > Pods**, filtre por namespace `crud-lab`)

### Passo 3: Verificar

- **Workloads > Pods** (namespace: `crud-lab`) → todos Running
- **Service Discovery > Ingresses** → `crud-app.local`, `pgadmin.local`, `minio.local`, `kibana.local`
- Acesse http://crud-app.local/api/produtos no browser

### Upgrade / Rollback

1. **Apps > Installed Apps** → clique em `crud-lab`
2. **Upgrade** → edite o YAML (ex: mudar `replicaCount: 3`) → **Upgrade**
3. **Rollback** → selecione uma revisão anterior → **Rollback**

---

## Opção B: Deploy manual de cada componente via GUI (sem Helm)

Use este caminho para aprender o que cada recurso faz individualmente.

### Passo 1: Criar Namespace

1. **Cluster Explorer** → **Cluster > Projects/Namespaces**
2. Clique **Create Namespace**
3. Name: `crud-lab` → **Create**

### Passo 2: Criar Secret (credenciais do banco)

1. Menu lateral: **Storage > Secrets**
2. Namespace: `crud-lab`
3. Clique **Create** → tipo **Opaque**
4. Name: `postgres-secret`
5. Adicione as chaves:

| Key | Value |
|-----|-------|
| `POSTGRES_DB` | `crudlab` |
| `POSTGRES_USER` | `postgres` |
| `POSTGRES_PASSWORD` | `postgres` |

6. **Create**

### Passo 3: Deploy do PostgreSQL

1. **Workloads > Deployments** → **Create**
2. Configure:
   - **Namespace:** `crud-lab`
   - **Name:** `postgres`
   - **Replicas:** 1
   - **Container Image:** `postgres:16`
   - **Ports:** Container Port `5432`, Protocol TCP
3. Em **Environment Variables** → **Add From** → **Secret** → selecione `postgres-secret`, todas as chaves
4. Em **Storage** → **Add Volume** → **EmptyDir** (para lab) ou **PVC**
   - Mount Path: `/var/lib/postgresql/data`
5. **Create**

#### Criar Service para PostgreSQL

1. **Service Discovery > Services** → **Create** → **ClusterIP**
2. Configure:
   - **Namespace:** `crud-lab`
   - **Name:** `postgres`
   - **Port:** 5432 → Target Port: 5432
   - **Selectors:** `app = postgres` (deve corresponder ao label do Deployment)
3. **Create**

### Passo 4: Deploy do MinIO

1. **Workloads > Deployments** → **Create**
2. Configure:
   - **Namespace:** `crud-lab`
   - **Name:** `minio`
   - **Container Image:** `minio/minio:latest`
   - **Args/Command:** Command: `minio` / Args: `server /data --console-address :9001`
   - **Ports:** `9000` (API) e `9001` (Console)
3. Em **Environment Variables** (Add Variable):

| Variable | Value |
|----------|-------|
| `MINIO_ROOT_USER` | `minioadmin` |
| `MINIO_ROOT_PASSWORD` | `minioadmin` |

4. **Create**

#### Criar Service e Ingress para MinIO

**Service:**
1. **Service Discovery > Services** → **Create** → **ClusterIP**
   - Name: `minio`, Namespace: `crud-lab`
   - Port `9000` → Target `9000` (API)
   - Port `9001` → Target `9001` (Console)
   - Selector: `app = minio`

**Ingress (console):**
1. **Service Discovery > Ingresses** → **Create**
   - Namespace: `crud-lab`
   - Name: `minio-ingress`
   - **Rules:** Host: `minio.local` → Path: `/` → Service: `minio` → Port: `9001`
   - Ingress Class: `nginx`
2. **Create**

### Passo 5: Deploy da App Java

1. **Workloads > Deployments** → **Create**
2. Configure:
   - **Namespace:** `crud-lab`
   - **Name:** `crud-lab-app`
   - **Replicas:** 2
   - **Container Image:** `localhost:5001/crud-k8s-lab:latest`
   - **Pull Policy:** Always
   - **Ports:** Container Port `8080`
3. Em **Environment Variables:**

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/crudlab` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` |
| `MINIO_URL` | `http://minio:9000` |
| `MINIO_ACCESS_KEY` | `minioadmin` |
| `MINIO_SECRET_KEY` | `minioadmin` |
| `MINIO_BUCKET` | `produtos` |
| `SPRING_PROFILES_ACTIVE` | `json` |

4. Em **Health Check** (opcional mas recomendado):
   - **Liveness:** HTTP GET `/actuator/health`, port `8080`, initial delay `30s`
   - **Readiness:** HTTP GET `/actuator/health`, port `8080`, initial delay `20s`
5. **Create**

#### Criar Service e Ingress para a App

**Service:**
1. **Service Discovery > Services** → **Create** → **ClusterIP**
   - Name: `crud-lab-app`, Namespace: `crud-lab`
   - Port `80` → Target `8080`
   - Selector: `app = crud-lab-app`

**Ingress:**
1. **Service Discovery > Ingresses** → **Create**
   - Name: `crud-app-ingress`, Namespace: `crud-lab`
   - **Rules:** Host: `crud-app.local` → Path: `/` → Service: `crud-lab-app` → Port: `80`
   - Ingress Class: `nginx`
2. **Create**

### Passo 6: Deploy do pgAdmin

1. **Workloads > Deployments** → **Create**
2. Configure:
   - **Namespace:** `crud-lab`
   - **Name:** `pgadmin`
   - **Container Image:** `dpage/pgadmin4:latest`
   - **Ports:** `80`
3. Em **Environment Variables:**

| Variable | Value |
|----------|-------|
| `PGADMIN_DEFAULT_EMAIL` | `admin@admin.com` |
| `PGADMIN_DEFAULT_PASSWORD` | `admin` |

4. **Create**

#### Criar Service e Ingress para pgAdmin

**Service:**
1. **Service Discovery > Services** → **Create** → **ClusterIP**
   - Name: `pgadmin`, Namespace: `crud-lab`
   - Port `80` → Target `80`
   - Selector: `app = pgadmin`

**Ingress:**
1. **Service Discovery > Ingresses** → **Create**
   - Name: `pgadmin-ingress`, Namespace: `crud-lab`
   - **Rules:** Host: `pgadmin.local` → Path: `/` → Service: `pgadmin` → Port: `80`
   - Ingress Class: `nginx`
2. **Create**

---

## Passo 7: Validar tudo

No Rancher:
1. **Workloads > Pods** → Filtro: namespace `crud-lab` → todos devem estar **Running**
2. **Service Discovery > Ingresses** → 3 ingresses listados com hosts configurados

No browser:
- http://crud-app.local/api/produtos → resposta JSON (lista vazia ou com dados)
- http://pgadmin.local → tela de login do pgAdmin
- http://minio.local → console do MinIO

---

## Resumo: Terminal vs Rancher GUI

| Ação | Terminal | Rancher GUI |
|------|---------|-------------|
| Criar namespace | `kubectl create ns crud-lab` | Cluster > Namespaces > Create |
| Criar secret | `kubectl create secret ...` | Storage > Secrets > Create |
| Deploy | `kubectl apply -f ...` ou `helm install` | Workloads > Deployments > Create |
| Criar service | `kubectl apply -f svc.yaml` | Service Discovery > Services > Create |
| Criar ingress | `kubectl apply -f ingress.yaml` | Service Discovery > Ingresses > Create |
| Ver logs | `kubectl logs pod/...` | Pod > ícone de logs (📄) |
| Escalar | `kubectl scale deploy --replicas=3` | Deployment > Edit > Replicas |
| Upgrade chart | `helm upgrade ...` | Apps > Installed Apps > Upgrade |
| Rollback | `helm rollback ...` | Apps > Installed Apps > Rollback |
| Deletar tudo | `helm uninstall` / `kubectl delete ns` | Apps > Delete ou Namespace > Delete |

---

## Dicas

- **Opção A (chart via Git)** é mais rápida e reproduzível — qualquer mudança no Git pode ser sincronizada.
- **Opção B (manual)** é excelente para entender o que cada recurso K8s faz por trás do Helm.
- Os **labels/selectors** são críticos: o Service precisa selecionar os pods corretos (ex: `app = crud-lab-app`). Ao criar Deployments pelo Rancher, ele gera labels automaticamente baseados no nome — use esses mesmos labels nos Services.
- Para o registry local (`localhost:5001`), a imagem precisa ter sido `docker push` antes. Isso ainda exige o terminal uma vez (ou um CI/CD).
