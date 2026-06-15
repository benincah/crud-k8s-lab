# Setup no Mac Mini M4 (Apple Silicon / ARM64)

## Diferenças em relação ao setup WSL

| Aspecto | WSL Ubuntu | Mac Mini M4 |
|---------|-----------|-------------|
| Docker | `apt install docker.io` | Docker Desktop for Mac (ARM64 nativo) |
| Kubernetes | K3s | Docker Desktop K8s |
| Registry local | Container manual | Mesmo (container) |
| Java/Maven | `apt install` | Homebrew |
| Rede | IP do WSL | `localhost` direto |
| Arquitetura | amd64 | arm64 (todas as imagens usadas já suportam) |

> ✅ **Boa notícia:** Nenhuma alteração no código, Dockerfile, docker-compose.yml ou YAMLs K8s é necessária.
> Todas as imagens base já possuem variantes `linux/arm64`.

---

## Pré-requisitos (Mac Mini M4 - máquina limpa)

### 1. Homebrew (gerenciador de pacotes do macOS)

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Após instalar, siga as instruções do terminal para adicionar ao PATH:
```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

### 2. Docker Desktop (inclui Docker + Kubernetes)

```bash
brew install --cask docker
```

Após instalar:
1. Abra o **Docker Desktop** (Launchpad ou Spotlight)
2. Aceite os termos
3. Vá em **Settings > General** → **desmarque** "Start Docker Desktop when you sign in to your computer"
4. Vá em **Settings > Kubernetes > Enable Kubernetes** → marque e aplique
5. Aguarde o cluster ficar "Running" (ícone verde)

> Docker Desktop no M4 roda nativamente ARM64, sem emulação Rosetta.

#### Impedir início automático com o sistema

O Docker Desktop é o único item que tenta iniciar automaticamente. Para garantir que não inicie no boot:

```bash
# Remover do Login Items via terminal
osascript -e 'tell application "System Events" to delete login item "Docker"' 2>/dev/null
```

Ou manualmente: **System Settings > General > Login Items** → remova "Docker" da lista.

#### Iniciar e parar manualmente

```bash
# Iniciar Docker Desktop (quando quiser trabalhar)
open -a Docker

# Verificar que está rodando
docker info

# Parar Docker Desktop (quando terminar)
osascript -e 'quit app "Docker"'
```

> ⚠️ Quando Docker Desktop para, **tudo para junto**: containers, Kubernetes, registry local. É intencional — nada roda em background quando você não está usando.

### 3. kubectl (já vem com Docker Desktop, mas para garantir versão atualizada)

```bash
brew install kubectl
```

### 4. Helm

```bash
brew install helm
```

### 5. Java 17 + Maven

```bash
brew install openjdk@17 maven
```

Adicione o Java ao PATH:
```bash
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### 6. Registry local

```bash
# --restart=unless-stopped: reinicia junto com o Docker, mas NÃO inicia o Docker no boot
docker run -d -p 5000:5000 --restart=unless-stopped --name registry registry:2
```

> O registry reinicia automaticamente quando você abre o Docker Desktop, mas **não força** o Docker a iniciar no boot.

---

## Verificar instalação

```bash
docker --version
kubectl get nodes          # Deve mostrar 1 node "Ready"
helm version
java -version
mvn -version
```

---

## Diferenças no fluxo de uso

### Docker Compose — Funciona IGUAL

```bash
cd crud-k8s-lab
docker compose up --build -d
```

Tudo acessa via `localhost` normalmente (não precisa descobrir IP como no WSL).

### Build da imagem para K8s

```bash
docker build -t crud-k8s-lab:latest .
docker tag crud-k8s-lab:latest localhost:5000/crud-k8s-lab:latest
docker push localhost:5000/crud-k8s-lab:latest
```

### Registry local no Kubernetes do Docker Desktop

O Docker Desktop K8s já consegue acessar `localhost:5000` sem configuração extra.
A imagem `localhost:5000/crud-k8s-lab:latest` nos YAMLs funciona diretamente.

### Ingress no Docker Desktop

O Docker Desktop **não inclui** Ingress Controller por padrão. Instale o NGINX Ingress:

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml
```

Aguarde ficar pronto:
```bash
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

Adicione ao `/etc/hosts`:
```bash
sudo sh -c 'echo "127.0.0.1 crud-app.local" >> /etc/hosts'
```

### Port-forward (alternativa ao Ingress)

```bash
kubectl port-forward -n crud-lab svc/crud-app 8080:80
curl http://localhost:8080/api/produtos
```

---

---

---

## Controle de inicialização — Resumo

| Componente | Inicia no boot? | Como iniciar | Como parar |
|------------|----------------|--------------|------------|
| Docker Desktop | ❌ Não (após config) | `open -a Docker` | `osascript -e 'quit app "Docker"'` |
| Kubernetes | ❌ Não (depende do Docker) | Inicia junto com Docker Desktop | Para junto com Docker Desktop |
| Registry local | ❌ Não (depende do Docker) | Inicia junto com Docker Desktop (`--restart=unless-stopped`) | Para junto com Docker Desktop |
| Homebrew/Java/Maven/Helm | ❌ Nunca | São CLI, rodam só quando chamados | — |

> **Nada roda em background no seu Mac até você explicitamente iniciar o Docker Desktop.**

---

## Problemas comuns no Mac M4

### "Cannot connect to Docker daemon"
→ Abra o Docker Desktop e aguarde inicializar.

### Imagem não puxa no K8s
→ Verifique se o registry local está rodando: `docker ps | grep registry`

### Port conflict (porta já em uso)
→ `lsof -i :8080` para identificar e `kill <PID>` para liberar.

### Permissão negada no /etc/hosts
→ Use `sudo` ao editar.
