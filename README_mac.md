# CRUD K8s Lab — Mac Mini M4 (Apple Silicon)

Projeto de aprendizado: App Java (Spring Boot) + PostgreSQL + MinIO + Grafana + Kubernetes + Helm.

> Este guia é a versão **macOS ARM64** do README principal. A estrutura do projeto e os arquivos são os mesmos — apenas os comandos de setup e ferramentas mudam.
> 
> 📖 **Antes de tudo, leia:** [`docs/roteiro-setup_mac.md`](docs/roteiro-setup_mac.md) — explica cada ferramenta instalada e como controlar o que roda no seu Mac.

---

## Pré-requisitos (Mac Mini M4 - máquina limpa)

```bash
# 1. Homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"

# 2. Docker Desktop (inclui Docker + Kubernetes)
brew install --cask docker
# Abra Docker Desktop → Settings → General → DESMARQUE "Start Docker Desktop when you sign in"
# Abra Docker Desktop → Settings → Kubernetes → Enable Kubernetes

# 3. Helm
brew install helm

# 4. Java 17 + Maven
brew install openjdk@17 maven
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 5. Registry local (reinicia com Docker, mas não força Docker a iniciar no boot)
docker run -d -p 5000:5000 --restart=unless-stopped --name registry registry:2

# 6. Ingress Controller (Docker Desktop não inclui por padrão)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml
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

> 📖 **Leia:** [`docs/roteiro-springboot.md`](docs/roteiro-springboot.md)

```bash
cd crud-k8s-lab
mvn clean package -DskipTests
ls target/*.jar
```

---

## Etapa 2: Rodar local com Docker Compose

> 📖 **Leia:** [`docs/roteiro-docker.md`](docs/roteiro-docker.md)

```bash
docker compose up --build -d
docker compose ps

# Testar
curl -X POST http://localhost:8080/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teclado","descricao":"Mecânico","preco":299.90}'

curl http://localhost:8080/api/produtos
```

### Acessar serviços (browser)

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| App (API) | http://localhost:8080/api/produtos | - |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| pgAdmin | http://localhost:5050 | admin@admin.com / admin |

### Configurar pgAdmin

1. Acesse `http://localhost:5050` (admin@admin.com / admin)
2. **Add New Server** → Name: `crud-lab`
3. Aba **Connection**: Host `postgres`, Port `5432`, User `postgres`, Password `postgres`
4. Navegue em Databases > crudlab > Schemas > Tables

```bash
docker compose down
```

---

## Etapa 3: Build da imagem Docker para Kubernetes

```bash
docker build -t crud-k8s-lab:latest .
docker tag crud-k8s-lab:latest localhost:5000/crud-k8s-lab:latest
docker push localhost:5000/crud-k8s-lab:latest
curl http://localhost:5000/v2/_catalog
```

---

## Etapa 4: Prometheus + Grafana no Kubernetes

> 📖 **Leia:** [`docs/roteiro-observabilidade.md`](docs/roteiro-observabilidade.md)

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  --set grafana.adminPassword=admin \
  --set grafana.service.type=NodePort \
  --set grafana.service.nodePort=30300

kubectl get pods -n monitoring

# Acessar Grafana: http://localhost:30300 (admin/admin)
```

---

## Etapa 5: Deploy com YAMLs puros

> 📖 **Leia:** [`docs/roteiro-kubernetes.md`](docs/roteiro-kubernetes.md)

```bash
kubectl apply -f k8s/01-postgres.yaml
kubectl apply -f k8s/03-minio.yaml
kubectl apply -f k8s/02-app.yaml

kubectl get all -n crud-lab
kubectl logs -n crud-lab -l app=crud-app -f

# Configurar /etc/hosts para Ingress
sudo sh -c 'echo "127.0.0.1 crud-app.local" >> /etc/hosts'

# Testar via Ingress
curl http://crud-app.local/api/produtos

# OU via port-forward
kubectl port-forward -n crud-lab svc/crud-app 8080:80 &
curl http://localhost:8080/api/produtos
```

---

## Etapa 6: Remover deploy manual antes do Helm

```bash
kubectl delete -f k8s/02-app.yaml
kubectl delete -f k8s/03-minio.yaml
kubectl delete -f k8s/01-postgres.yaml
```

---

## Etapa 7: Deploy com Helm

> 📖 **Leia:** [`docs/roteiro-helm.md`](docs/roteiro-helm.md)

```bash
helm install crud-lab ./helm/crud-app -n crud-lab --create-namespace
helm list -n crud-lab
kubectl get all -n crud-lab

# Atualizar
helm upgrade crud-lab ./helm/crud-app -n crud-lab

# Rollback
helm rollback crud-lab 1 -n crud-lab

# Remover
helm uninstall crud-lab -n crud-lab
```

---

## Etapa 8: GUI para Kubernetes

No Mac, além do LENS você pode usar:

- **LENS** → `brew install --cask lens`
- **k9s** (terminal) → `brew install k9s` (leve e rápido)

O kubeconfig já está em `~/.kube/config` (Docker Desktop configura automaticamente).

---

## Etapa 9: MinIO - Upload de arquivos

> 📖 **Leia:** [`docs/roteiro-minio.md`](docs/roteiro-minio.md)

```bash
curl -X POST http://crud-app.local/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Manual","descricao":"PDF do produto","preco":0}'

curl -X POST http://crud-app.local/api/produtos/1/upload \
  -F "file=@meu-arquivo.pdf"
```

---

## Notas específicas Mac M4

- Todas as imagens Docker rodam **nativamente ARM64** (sem emulação)
- Performance excelente — M4 tem I/O rápido para containers
- Se precisar buildar imagem multi-arch (para deploy em cluster x86): `docker buildx build --platform linux/amd64,linux/arm64 -t localhost:5000/crud-k8s-lab:latest --push .`
- O shell padrão é **zsh** (configs vão em `~/.zshrc` ao invés de `~/.bashrc`)
- **Nada inicia no boot** — quando quiser trabalhar: `open -a Docker` — quando terminar: `osascript -e 'quit app "Docker"'`
