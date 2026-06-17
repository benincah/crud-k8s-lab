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
> Ele explica Prometheus, Grafana, métricas, PromQL e como a app expõe dados para monitoramento.

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

---

## Etapa 5: Deploy com YAMLs puros (para aprender K8s)

> 📖 **Antes de aplicar os YAMLs, leia o roteiro de estudo:** [`docs/roteiro-kubernetes.md`](docs/roteiro-kubernetes.md)
> Ele explica cada conceito (Namespace, Deployment, Service, Ingress, Secret) com analogias e exemplos comentados usando exatamente os mesmos arquivos que você vai aplicar aqui.

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

# Testar (via ingress)
curl http://crud-app.local/api/produtos

# Testar (via port-forward, se ingress não estiver configurado no /etc/hosts)
kubectl port-forward -n crud-lab svc/crud-app 8080:80 &
curl http://localhost:8080/api/produtos
```

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

```bash
# Instalar o chart
helm install crud-lab ./helm/crud-app -n crud-lab --create-namespace

# Ver status
helm list -n crud-lab
kubectl get all -n crud-lab

# Atualizar após mudanças no values.yaml ou templates
helm upgrade crud-lab ./helm/crud-app -n crud-lab

# Ver histórico de releases
helm history crud-lab -n crud-lab

# Rollback para versão anterior
helm rollback crud-lab 1 -n crud-lab

# Remover tudo
helm uninstall crud-lab -n crud-lab
```

---

## Etapa 8: Acessar via LENS

1. Instale o LENS no Windows
2. Copie o kubeconfig do WSL: `cat ~/.kube/config`
3. No LENS: **File > Add Cluster** → cole o conteúdo do kubeconfig
4. Ajuste o `server` no kubeconfig para o IP do WSL (ex: `https://172.x.x.x:6443`)
5. Navegue:
   - **Workloads > Pods** → ver pods rodando
   - **Workloads > Deployments** → escalar replicas
   - **Network > Services** → ver endpoints
   - **Network > Ingresses** → ver rotas
   - **Config > Secrets** → ver secrets criados

---

## Etapa 9: MinIO - Upload de arquivos

> 📖 **Para entender MinIO e object storage, leia:** [`docs/roteiro-minio.md`](docs/roteiro-minio.md)
> Ele explica buckets, objects, console web, CLI e integração com a app Java.

> 📖 **Para entender PostgreSQL em container, leia:** [`docs/roteiro-postgresql.md`](docs/roteiro-postgresql.md)
> Ele compara container vs instalado, cuidados com persistência, backup e produção.

```bash
# Criar um produto primeiro
curl -X POST http://crud-app.local/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Manual","descricao":"PDF do produto","preco":0}'

# Upload de arquivo para o produto (id=1)
curl -X POST http://crud-app.local/api/produtos/1/upload \
  -F "file=@meu-arquivo.pdf"

# O arquivo fica armazenado no MinIO bucket "produtos"
# Acesse o MinIO Console para visualizar
```

---

## Resumo: O que cada etapa ensina

| Etapa | Conceitos |
|-------|-----------|
| 1 | Maven, build Java, estrutura Spring Boot |
| 2 | Docker Compose, multi-container, variáveis de ambiente |
| 3 | Dockerfile multi-stage, registry de imagens |
| 4 | Helm repos, instalação de charts de terceiros |
| 5 | Deployment, Service, Ingress, Secret, Namespace (YAMLs) |
| 6 | Limpeza de recursos K8s |
| 7 | Helm charts próprios, templates, values, upgrade, rollback |
| 8 | LENS como ferramenta visual para K8s |
| 9 | Object storage (MinIO), upload via API |
