# CRUD K8s Lab

Projeto de aprendizado: App Java (Spring Boot) + PostgreSQL + MinIO + Grafana + Kubernetes + Helm.

## Estrutura

```
crud-k8s-lab/
├── src/                    # Código Java (Spring Boot)
├── Dockerfile              # Build multi-stage da imagem
├── docker-compose.yml      # Ambiente local completo
├── prometheus.yml          # Config do Prometheus
├── helm/crud-app/          # Helm chart
├── k8s/                    # YAMLs puros (para aprender)
└── docs/                   # Guias de estudo
```

---

## Pré-requisitos (WSL Ubuntu - máquina limpa)

Nenhum serviço precisa estar instalado nativamente. Tudo roda em container.

```bash
# 1. Docker
sudo apt update && sudo apt install docker.io -y
sudo usermod -aG docker $USER
newgrp docker

# 2. K3s (Kubernetes leve - já inclui containerd e kubectl)
curl -sfL https://get.k3s.io | sh -
# Configurar kubectl para seu usuário
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
export KUBECONFIG=~/.kube/config
echo 'export KUBECONFIG=~/.kube/config' >> ~/.bashrc

# 3. Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# 4. Java 17 + Maven (para build local)
sudo apt install openjdk-17-jdk maven -y

# 5. Registry local (para o K3s puxar suas imagens)
docker run -d -p 5000:5000 --restart=always --name registry registry:2
```

### Verificar instalação

```bash
docker --version
kubectl get nodes          # Deve mostrar 1 node "Ready"
helm version
java -version
mvn -version
```

---

## Etapa 1: Entender e buildar a aplicação

> 📖 **Para entender o código Java, leia:** [`docs/roteiro-springboot.md`](docs/roteiro-springboot.md)
> Ele explica cada classe, anotação, o pom.xml e como tudo se conecta no Spring Boot.

```bash
cd crud-k8s-lab

# Build com Maven (gera o .jar)
mvn clean package -DskipTests

# Testar localmente (vai falhar sem banco, mas valida o build)
# O importante aqui é gerar o target/crud-k8s-lab-1.0.0.jar
ls target/*.jar
```

---

## Etapa 2: Rodar local com Docker Compose

> 📖 **Antes de prosseguir, leia o roteiro de estudo:** [`docs/roteiro-docker.md`](docs/roteiro-docker.md)
> Ele explica o Dockerfile multi-stage e cada seção do docker-compose.yml com analogias e exemplos comentados.

O docker-compose.yml sobe TUDO que você precisa como container:
- App Java
- PostgreSQL
- pgAdmin (interface visual para o banco)
- MinIO (object storage)
- Prometheus (coleta métricas)
- Grafana (dashboards)

```bash
# Subir tudo
docker compose up --build -d

# Verificar se tudo subiu
docker compose ps

# Testar CRUD
curl -X POST http://localhost:8080/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teclado","descricao":"Mecânico","preco":299.90}'

curl http://localhost:8080/api/produtos

# Ver métricas Prometheus expostas pela app
curl http://localhost:8080/actuator/prometheus
```

### Acessar serviços (browser)

> ⚠️ Esses serviços só ficam acessíveis enquanto o `docker compose up` estiver rodando. Após `docker compose down`, tudo é removido.

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| App (API) | http://localhost:8080/api/produtos | - |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| pgAdmin | http://localhost:5050 | admin@admin.com / admin |

### Configurar pgAdmin para acessar o banco

1. Acesse `http://localhost:5050` (admin@admin.com / admin)
2. **Add New Server** → Name: `crud-lab`
3. Aba **Connection**:
   - Host: `postgres` (nome do serviço no compose)
   - Port: `5432`
   - Username: `postgres`
   - Password: `postgres`
4. Clique **Save** → navegue em Databases > crudlab > Schemas > Tables

### Configurar Grafana para ver métricas da app

1. Acesse `http://localhost:3000` (admin/admin)
2. Vá em **Connections > Data Sources > Add data source**
3. Selecione **Prometheus**
4. URL: `http://prometheus:9090`
5. Clique **Save & Test**
6. Vá em **Dashboards > Import** → ID `4701` (JVM Micrometer)

### Parar o ambiente local

```bash
docker compose down
```

---

## Etapa 3: Build da imagem Docker para Kubernetes

```bash
# Build da imagem
docker build -t crud-k8s-lab:latest .

# Tag para o registry local
docker tag crud-k8s-lab:latest localhost:5000/crud-k8s-lab:latest

# Push para registry local (K3s vai puxar daqui)
docker push localhost:5000/crud-k8s-lab:latest

# Verificar que a imagem está no registry
curl http://localhost:5000/v2/_catalog
```

---

## Etapa 4: Instalar Prometheus + Grafana no Kubernetes (via Helm)

> 📖 **Para entender o que está sendo instalado, leia:** [`docs/roteiro-observabilidade.md`](docs/roteiro-observabilidade.md)
> Ele explica Prometheus, Grafana, ServiceMonitor, métricas, PromQL e como a app expõe dados para monitoramento.

**Isso precisa ser feito ANTES de deployar a app**, para que a observabilidade já esteja pronta.

```bash
# Adicionar repositório do Prometheus
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Instalar stack completa (Prometheus + Grafana + alertas)
helm install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  --set grafana.adminPassword=admin \
  --set grafana.service.type=NodePort \
  --set grafana.service.nodePort=30300

# Verificar
kubectl get pods -n monitoring

# Acessar Grafana do K8s
# http://<IP-DO-WSL>:30300  (admin/admin)
```

### Configurar Grafana com Prometheus

O `kube-prometheus-stack` já instala o Grafana **com o Prometheus pré-configurado como datasource**. Não precisa adicionar datasource manualmente.

Para ver métricas da **sua aplicação Java (JVM/Micrometer)**:

1. Acesse Grafana (http://<IP-DO-WSL>:30300, admin/admin)
2. Vá em **Dashboards > Browse** (dezenas de dashboards prontos de K8s)
3. Para importar dashboard de JVM:
   - Clique em **New > Import**
   - No campo "Import via grafana.com", digite: `4701`
   - Clique **Load**
   - Em "Prometheus", selecione: `Prometheus`
   - Clique **Import**
4. **Corrigir datasource** (dropdowns "Application" e "Instance" ficam vazios sem isso):
   - No dashboard, clique no ⚙️ (Settings) no topo direito
   - Vá em **Variables** → clique em `application`
   - No campo **Data source**, troque `${DS_PROMETHEUS}` por `Prometheus`
   - Clique **Run query** → deve mostrar `crud-k8s-lab` no Preview
   - Clique **Apply** → repita para `instance`
   - **Save dashboard**

   > 💡 Se o campo Data source não aparecer na UI, use o script via API:
   > ```bash
   > DASH_UID=$(curl -s -u admin:admin http://localhost:3000/api/search | \
   >   python3 -c "import json,sys;[print(d['uid']) for d in json.load(sys.stdin) if 'JVM' in d.get('title','')]" | head -1)
   > curl -s -u admin:admin http://localhost:3000/api/dashboards/uid/$DASH_UID | \
   >   python3 -c "
   > import json,sys
   > data=json.load(sys.stdin)
   > dash=data['dashboard']
   > s=json.dumps(dash).replace('\${DS_PROMETHEUS}','prometheus')
   > dash=json.loads(s); dash['id']=None
   > print(json.dumps({'dashboard':dash,'overwrite':True}))" | \
   >   curl -s -u admin:admin -X POST -H 'Content-Type: application/json' \
   >     -d @- http://localhost:3000/api/dashboards/db
   > ```

5. Selecione nos dropdowns: **Application** = `crud-k8s-lab`, **Instance** = qualquer IP
6. O dashboard "JVM (Micrometer)" mostra: memória, threads, GC, HTTP requests da app

> ⚠️ O dashboard JVM só mostra dados após o ServiceMonitor estar ativo (Etapa 7 com Helm cria automaticamente).

### Verificar que Prometheus está coletando métricas da app

1. Acesse Prometheus (http://<IP-DO-WSL>:30300 → use port 9090 via NodePort ou port-forward)
2. No campo de query, digite: `up{namespace="crud-lab"}`
3. Clique **Execute** → deve mostrar 2 targets com valor `1`

> 💡 Se a app não aparecer, veja a seção de troubleshooting no [`docs/roteiro-observabilidade.md`](docs/roteiro-observabilidade.md) sobre ServiceMonitor.

---

## Etapa 5: Deploy com YAMLs puros (para aprender K8s)

> 📖 **Antes de aplicar os YAMLs, leia o roteiro de estudo:** [`docs/roteiro-kubernetes.md`](docs/roteiro-kubernetes.md)
> Ele explica cada conceito (Namespace, Deployment, Service, Ingress, Secret) com analogias e exemplos comentados usando exatamente os mesmos arquivos que você vai aplicar aqui.

> 📖 **Para entender como acessar serviços no K8s:** [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md)

Aqui você aplica os recursos manualmente para entender cada peça.

```bash
# Aplicar na ordem (namespace primeiro, depois dependências, depois app)
kubectl apply -f k8s/01-postgres.yaml
kubectl apply -f k8s/03-minio.yaml
kubectl apply -f k8s/02-app.yaml

# Verificar
kubectl get all -n crud-lab
kubectl get ingress -n crud-lab

# Logs da app
kubectl logs -n crud-lab -l app=crud-app -f

# Configurar /etc/hosts para Ingress (K3s com Traefik)
sudo sh -c 'echo "<IP-DO-WSL> crud-app.local" >> /etc/hosts'

# Testar (via ingress)
curl http://crud-app.local/api/produtos

# Testar (via port-forward, se ingress não estiver configurado)
kubectl port-forward -n crud-lab svc/crud-app 8080:80 &
curl http://localhost:8080/api/produtos
```

> 💡 O K3s já inclui o Ingress Controller (Traefik). Diferente do Docker Desktop (Mac), não precisa instalar separado.

---

## Etapa 6: Remover deploy manual ANTES de usar Helm

> ⚠️ **IMPORTANTE:** As etapas 5 e 6 são alternativas. Você NÃO pode ter os dois ao mesmo tempo.
> Antes de fazer o deploy via Helm, remova o que foi criado na etapa 5:

```bash
kubectl delete -f k8s/02-app.yaml
kubectl delete -f k8s/03-minio.yaml
kubectl delete -f k8s/01-postgres.yaml
```

---

## Etapa 7: Deploy com Helm (forma profissional)

> 📖 **Antes de prosseguir, leia o roteiro de estudo:** [`docs/roteiro-helm.md`](docs/roteiro-helm.md)
> Ele explica Chart.yaml, values.yaml, templates com variáveis, e todos os comandos Helm usados aqui.

O chart Helm inclui **toda a infraestrutura** da aplicação:
- App Java (Spring Boot)
- PostgreSQL (banco de dados)
- MinIO (object storage)
- pgAdmin (cliente visual para o banco)
- ServiceMonitor (para Prometheus coletar métricas)

Cada componente tem um template próprio em `helm/crud-app/templates/` e pode ser habilitado/desabilitado via `values.yaml`.

```bash
# Instalar o chart
helm install crud-lab ./helm/crud-app -n crud-lab --create-namespace

# Ver status
helm list -n crud-lab
kubectl get all -n crud-lab
```

### Configurar hostnames para Ingress

```bash
# Descobrir IP do WSL
hostname -I | awk '{print $1}'

# Adicionar ao /etc/hosts (no Windows: C:\Windows\System32\drivers\etc\hosts)
# <IP-DO-WSL> crud-app.local pgadmin.local minio.local
```

### Acessar serviços via Ingress

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| App (API) | http://crud-app.local/api/produtos | - |
| pgAdmin | http://pgadmin.local | admin@admin.com / admin |
| MinIO Console | http://minio.local | minioadmin / minioadmin |

### Acessar serviços via port-forward (alternativa)

```bash
# pgAdmin → http://localhost:5050
kubectl port-forward -n crud-lab svc/pgadmin 5050:5050 &

# MinIO Console → http://localhost:9001
kubectl port-forward -n crud-lab svc/minio 9001:9001 &

# Grafana (namespace monitoring) → http://localhost:3000
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80 &

# Prometheus (namespace monitoring) → http://localhost:9090
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090 &
```

> 💡 Port-forward morre ao fechar o terminal. Ingress é permanente. Veja [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md).

### Configurar pgAdmin para acessar o banco

1. Acesse http://pgadmin.local (ou http://localhost:5050)
2. **Add New Server** → Name: `crud-lab`
3. Aba **Connection**:
   - Host: `postgres`
   - Port: `5432`
   - Username: `postgres`
   - Password: `postgres`
4. Navegue em Databases > crudlab > Schemas > Tables

### Comandos úteis (referência para o dia a dia)

> ⚠️ Não execute tudo de uma vez — são comandos para usar conforme necessidade.

```bash
# Atualizar após mudar values.yaml ou templates
helm upgrade crud-lab ./helm/crud-app -n crud-lab

# Ver histórico de releases
helm history crud-lab -n crud-lab

# Rollback para versão anterior (se algo der errado após upgrade)
helm rollback crud-lab 1 -n crud-lab

# Remover tudo (só quando quiser destruir o ambiente)
helm uninstall crud-lab -n crud-lab
```

---

## Etapa 8: MinIO - Upload de arquivos

> 📖 **Para entender MinIO e object storage, leia:** [`docs/roteiro-minio.md`](docs/roteiro-minio.md)
> Ele explica buckets, objects, console web, CLI e integração com a app Java.

> 📖 **Para entender PostgreSQL em container, leia:** [`docs/roteiro-postgresql.md`](docs/roteiro-postgresql.md)
> Ele compara container vs instalado, cuidados com persistência, backup e produção.

### Criar o bucket (necessário apenas na primeira vez)

O MinIO começa vazio — o bucket `produtos` precisa ser criado antes do primeiro upload:

```bash
kubectl run minio-mc -n crud-lab --image=minio/mc --restart=Never --command -- \
  sh -c "mc alias set myminio http://minio:9000 minioadmin minioadmin && mc mb myminio/produtos"

# Aguardar e verificar
sleep 10
kubectl logs -n crud-lab minio-mc

# Limpar o pod temporário (já fez seu trabalho)
kubectl delete pod minio-mc -n crud-lab
```

### Testar upload

```bash
# Criar um produto
curl -X POST http://crud-app.local/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Manual","descricao":"PDF do produto","preco":0}'

# Criar um arquivo de teste
echo "conteudo de teste" > meu-arquivo.pdf

# Upload do arquivo para o produto (id=1)
curl -X POST http://crud-app.local/api/produtos/1/upload \
  -F "file=@meu-arquivo.pdf"
# Resposta esperada: {"id":1,...,"arquivoUrl":"produtos/1/meu-arquivo.pdf"}
```

### Verificar no MinIO Console

1. Acesse http://minio.local (ou via port-forward: `kubectl port-forward -n crud-lab svc/minio 9001:9001 &` → http://localhost:9001)
2. Login: minioadmin / minioadmin
3. No menu lateral, clique em **Object Browser**
4. Clique no bucket **produtos**
5. Navegue em `1/` → verá o `meu-arquivo.pdf`

> 💡 O arquivo é armazenado no caminho `produtos/<id-do-produto>/<nome-arquivo>`. Cada produto tem sua "pasta" dentro do bucket.

---

## Etapa 9: GUI para Kubernetes (LENS)

> 📖 **Leia:** [`docs/roteiro-lens.md`](docs/roteiro-lens.md)

### Por que usar uma GUI?

Até agora você usou `kubectl` para tudo. Uma GUI permite ver tudo de uma vez, acompanhar logs em tempo real, escalar replicas com um clique e identificar problemas visualmente.

### Instalar LENS no Windows

1. Baixe em https://k8slens.dev/ e instale
2. Crie conta ou faça login

### Configurar (WSL → Windows)

1. No WSL, copie o kubeconfig:
   ```bash
   cat ~/.kube/config
   ```
2. No LENS (Windows): **File > Add Cluster** → cole o conteúdo
3. **Importante:** Ajuste o campo `server` no kubeconfig para o IP do WSL:
   ```bash
   # Descobrir IP do WSL
   hostname -I | awk '{print $1}'
   ```
   Troque `https://127.0.0.1:6443` por `https://<IP-DO-WSL>:6443`
4. Clique **Add Cluster** → aguarde conectar

### Navegar pelo cluster

| Seção no LENS | O que mostra | Equivalente kubectl |
|---------------|-------------|--------------------|
| **Workloads > Pods** | Todos os pods, status, restarts | `kubectl get pods -A` |
| **Workloads > Deployments** | Deployments, réplicas desejadas vs atuais | `kubectl get deployments -A` |
| **Network > Services** | Services, portas, IPs internos | `kubectl get svc -A` |
| **Network > Ingresses** | Rotas de entrada, hostnames | `kubectl get ingress -A` |
| **Config > Secrets** | Secrets (dados sensíveis, base64) | `kubectl get secrets -A` |

### Tarefas comuns no LENS

**Ver logs de um pod:**
1. Workloads > Pods > selecione o namespace `crud-lab`
2. Clique no pod (ex: `crud-lab-app-xxx`)
3. Clique no ícone de **logs**

**Escalar um deployment:**
1. Workloads > Deployments > `crud-lab-app`
2. Clique e altere o número de réplicas

**Entrar no terminal de um pod:**
1. Workloads > Pods > selecione o pod
2. Clique no ícone de **terminal** (Shell)

**Ver eventos (troubleshooting):**
1. Selecione o namespace `crud-lab`
2. Vá em **Events** — mostra erros recentes, restarts, falhas de pull

---

## Resumo: O que cada etapa ensina

| Etapa | Conceitos |
|-------|-----------|
| 1 | Maven, build Java, estrutura Spring Boot |
| 2 | Docker Compose, multi-container, variáveis de ambiente |
| 3 | Dockerfile multi-stage, registry de imagens |
| 4 | Helm repos, charts de terceiros, Prometheus, Grafana, ServiceMonitor |
| 5 | Deployment, Service, Ingress, Secret, Namespace (YAMLs) |
| 6 | Limpeza de recursos K8s |
| 7 | Helm charts próprios, templates, values, upgrade, rollback |
| 8 | Object storage (MinIO), bucket, upload via API |
| 9 | LENS como ferramenta visual para K8s |

---

## Documentação adicional

| Doc | Conteúdo |
|-----|----------|
| [`docs/arquitetura-infra.md`](docs/arquitetura-infra.md) | Diagrama completo da infraestrutura e comunicação entre componentes |
| [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md) | Port-forward vs Ingress — como acessar serviços no K8s |
| [`docs/roteiro-observabilidade.md`](docs/roteiro-observabilidade.md) | Prometheus, Grafana, ServiceMonitor, métricas |
| [`docs/roteiro-helm.md`](docs/roteiro-helm.md) | Helm charts, templates, comandos |
| [`docs/roteiro-kubernetes.md`](docs/roteiro-kubernetes.md) | Conceitos K8s (Pod, Deployment, Service, Ingress, Secret) |
| [`docs/roteiro-minio.md`](docs/roteiro-minio.md) | Object storage, buckets, upload |
| [`docs/roteiro-lens.md`](docs/roteiro-lens.md) | LENS, k9s, GUI para Kubernetes |
| [`docs/roteiro-springboot.md`](docs/roteiro-springboot.md) | Código Java, Spring Boot, anotações |
| [`docs/roteiro-docker.md`](docs/roteiro-docker.md) | Dockerfile, Docker Compose |
