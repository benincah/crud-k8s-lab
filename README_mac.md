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

# 5. Registry local (porta 5001 no host, pois 5000 é usada pelo AirPlay no macOS)
docker run -d -p 5001:5000 --restart=unless-stopped --name registry registry:2

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

> ⚠️ Esses serviços só ficam acessíveis enquanto o `docker compose up` estiver rodando. Após `docker compose down`, tudo é removido.

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
docker tag crud-k8s-lab:latest localhost:5001/crud-k8s-lab:latest
docker push localhost:5001/crud-k8s-lab:latest
curl http://localhost:5001/v2/_catalog
```

---

## Etapa 4: Prometheus + Grafana no Kubernetes

> 📖 **Leia:** [`docs/roteiro-observabilidade.md`](docs/roteiro-observabilidade.md)

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  --set grafana.adminPassword=admin

kubectl get pods -n monitoring
```

### Acessar serviços do Kubernetes (port-forward)

No Docker Desktop, `NodePort` não é acessível diretamente. Use `port-forward`:

```bash
# Grafana (admin/admin)
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
# → http://localhost:3000

# Prometheus
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090
# → http://localhost:9090
```

> 💡 Cada `port-forward` ocupa o terminal. Use `&` no final para rodar em background, ou abra abas separadas.

---

## Etapa 5: Deploy com YAMLs puros

> 📖 **Leia:** [`docs/roteiro-kubernetes.md`](docs/roteiro-kubernetes.md)

> ⚠️ **Ingress Controller:** Se você ainda não instalou na etapa de pré-requisitos (item 6), faça agora — sem ele o Ingress não funciona:
> ```bash
> kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml
> kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=120s
> ```

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
> 📖 **Para entender como acessar os serviços:** [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md)

O chart Helm inclui **toda a infraestrutura** da aplicação:
- App Java (Spring Boot)
- PostgreSQL (banco de dados)
- MinIO (object storage)
- pgAdmin (cliente visual para o banco)

Cada componente tem um template próprio em `helm/crud-app/templates/` e pode ser habilitado/desabilitado via `values.yaml`.

```bash
# Instalar
helm install crud-lab ./helm/crud-app -n crud-lab --create-namespace
helm list -n crud-lab
kubectl get all -n crud-lab
```

### Configurar hostnames (apenas uma vez)

Para acessar os serviços via Ingress (sem port-forward), adicione ao `/etc/hosts`:

```bash
sudo sh -c 'cat >> /etc/hosts << EOF
127.0.0.1 crud-app.local
127.0.0.1 pgadmin.local
127.0.0.1 minio.local
EOF'
```

### Acessar serviços via Ingress (recomendado)

Acesso permanente — funciona enquanto os pods estiverem rodando, sem precisar de comandos extras.

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| App (API) | http://crud-app.local/api/produtos | - |
| pgAdmin | http://pgadmin.local | admin@admin.com / admin |
| MinIO Console | http://minio.local | minioadmin / minioadmin |

### Acessar serviços via port-forward (alternativa)

Se o Ingress não estiver configurado, use port-forward (túnel temporário):

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

> 💡 Port-forward morre ao fechar o terminal. Ingress é permanente. Veja [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md) para entender a diferença.

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

## Etapa 8: GUI para Kubernetes

No Mac, além do LENS você pode usar:

- **LENS** → `brew install --cask lens`
- **k9s** (terminal) → `brew install k9s` (leve e rápido)

O kubeconfig já está em `~/.kube/config` (Docker Desktop configura automaticamente).

---

## Etapa 9: MinIO - Upload de arquivos

> 📖 **Leia:** [`docs/roteiro-minio.md`](docs/roteiro-minio.md)

### Criar o bucket (necessário apenas na primeira vez)

O MinIO começa vazio — o bucket `produtos` precisa ser criado antes do primeiro upload:

```bash
kubectl run minio-mc -n crud-lab --image=minio/mc --restart=Never --command -- \
  sh -c "mc alias set myminio http://minio:9000 minioadmin minioadmin && mc mb myminio/produtos"

# Aguardar e verificar
sleep 10
kubectl logs -n crud-lab minio-mc

# Limpar o pod temporário
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

1. Acesse http://minio.local (minioadmin / minioadmin)
2. No menu lateral, clique em **Object Browser**
3. Clique no bucket **produtos**
4. Navegue em `1/` → verá o `meu-arquivo.pdf`

> 💡 O arquivo é armazenado no caminho `produtos/<id-do-produto>/<nome-arquivo>`. Cada produto tem sua "pasta" dentro do bucket.

---

## Notas específicas Mac M4

- Todas as imagens Docker rodam **nativamente ARM64** (sem emulação)
- Performance excelente — M4 tem I/O rápido para containers
- Se precisar buildar imagem multi-arch (para deploy em cluster x86): `docker buildx build --platform linux/amd64,linux/arm64 -t localhost:5001/crud-k8s-lab:latest --push .`
- O shell padrão é **zsh** (configs vão em `~/.zshrc` ao invés de `~/.bashrc`)
- **Nada inicia no boot** — quando quiser trabalhar: `open -a Docker` — quando terminar: `osascript -e 'quit app "Docker"'`
