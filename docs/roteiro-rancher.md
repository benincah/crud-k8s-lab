# Roteiro de Estudo: Rancher — Gerenciamento Kubernetes

## 1. O que é Rancher?

**Analogia:** Se `kubectl` é dirigir com o manual do carro aberto, e LENS é um GPS visual, o Rancher é o **centro de controle de uma frota** — você gerencia todos os veículos (clusters), define quem pode dirigir (RBAC), e monitora tudo de um painel centralizado.

Rancher é uma plataforma open-source que adiciona ao Kubernetes:
- GUI web completa (substitui LENS)
- Gerenciamento de múltiplos clusters
- Controle de acesso (RBAC) por usuário/equipe
- Catálogo de aplicações (Helm charts via browser)
- Auditoria (quem fez o quê, quando)

---

## 2. Conceitos novos introduzidos

### 2.1. TLS / HTTPS

**O que é:** TLS (Transport Layer Security) criptografa a comunicação entre browser e servidor. HTTPS = HTTP + TLS.

**Por que Rancher exige:** O Rancher gerencia credenciais, secrets e acesso ao cluster. Sem HTTPS, qualquer pessoa na rede poderia interceptar essas informações.

**No projeto:**
```
Browser → https://rancher.local → [Certificado TLS] → Ingress Controller → Rancher Pod
```

O certificado garante:
1. **Criptografia** — dados trafegam cifrados
2. **Autenticidade** — você está falando com o Rancher real (não um impostor)

### 2.2. cert-manager

**O que é:** Um controller K8s que automatiza a criação e renovação de certificados TLS.

**Analogia:** Um "cartório digital" dentro do cluster. Quando alguém precisa de um certificado, o cert-manager emite e renova automaticamente.

**Como funciona no projeto:**
```
Rancher pede certificado → cert-manager gera (autoassinado) → armazena como Secret TLS
```

Em produção, o cert-manager pode gerar certificados válidos via Let's Encrypt (gratuito, reconhecido pelos browsers).

**Namespace:** `cert-manager` (3 pods: controller, webhook, cainjector)

### 2.3. Secrets TLS

**O que é:** Um Secret do Kubernetes que armazena certificado + chave privada.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: tls-rancher-ingress
  namespace: cattle-system
type: kubernetes.io/tls
data:
  tls.crt: <certificado em base64>
  tls.key: <chave privada em base64>
```

**Ver no cluster:**
```bash
kubectl get secrets -n cattle-system | grep tls
kubectl describe secret tls-rancher-ingress -n cattle-system
```

### 2.4. cattle-system (namespace do Rancher)

**O que é:** Namespace onde o Rancher Server roda. O nome "cattle" vem da metáfora "pets vs cattle" (animais de estimação vs gado) — em K8s, pods são "gado" (descartáveis, substituíveis).

**O que contém:**
```bash
kubectl get all -n cattle-system
# Pods: rancher (3 réplicas em produção, 1 no lab)
# Services: rancher (ClusterIP)
# Ingress: rancher (hostname: rancher.local, TLS)
# Secrets: tls-rancher-ingress, bootstrap-secret
```

### 2.5. RBAC (Role-Based Access Control)

**O que é:** Sistema de permissões do Kubernetes. Define **quem** pode fazer **o quê** em **qual recurso**.

**Analogia:** Crachá de acesso em um prédio:
- Visitante (viewer) → só olha
- Funcionário (editor) → olha e modifica
- Segurança (admin) → faz tudo, inclusive dar/tirar acessos

**4 recursos RBAC:**

| Recurso | Escopo | Descrição |
|---------|--------|-----------|
| **Role** | Namespace | Permissões dentro de um namespace |
| **ClusterRole** | Cluster inteiro | Permissões globais |
| **RoleBinding** | Namespace | Liga um usuário/SA a uma Role |
| **ClusterRoleBinding** | Cluster inteiro | Liga um usuário/SA a um ClusterRole |

**Exemplo real (Fluentd precisa ler pods):**
```
ClusterRole "fluentd"          → pode: get, list, watch pods/namespaces
ClusterRoleBinding "fluentd"   → liga: ServiceAccount "fluentd" ao ClusterRole "fluentd"
```

**Exemplo conceitual (equipe de dev):**
```
Role "dev-crud-lab"            → pode: get, list, create, update pods/services no namespace crud-lab
RoleBinding "joao-dev"         → liga: User "joao" ao Role "dev-crud-lab"
```

Resultado: João pode criar pods em `crud-lab`, mas NÃO pode mexer em `monitoring` ou `cattle-system`.

### 2.6. Roles e RoleBindings — Detalhado

```yaml
# ClusterRole: define O QUE pode ser feito
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: pod-reader
rules:
  - apiGroups: [""]           # "" = core API (pods, services, secrets)
    resources: ["pods"]       # Em quais recursos
    verbs: ["get", "list"]    # Quais ações permitidas

---
# ClusterRoleBinding: define QUEM pode fazer
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: read-pods-global
roleRef:
  kind: ClusterRole
  name: pod-reader            # Referencia o ClusterRole acima
subjects:
  - kind: User
    name: joao                # Quem recebe a permissão
  - kind: ServiceAccount
    name: fluentd             # Ou uma ServiceAccount (para pods)
    namespace: crud-lab
```

**Verbos disponíveis:**

| Verbo | Ação |
|-------|------|
| `get` | Ler um recurso específico |
| `list` | Listar recursos |
| `watch` | Receber atualizações em tempo real |
| `create` | Criar novos recursos |
| `update` | Modificar recursos existentes |
| `delete` | Remover recursos |
| `*` | Todas as ações (admin) |

---

## 3. O que o Rancher cria automaticamente

Ao instalar o Rancher, ele cria dezenas de ClusterRoles e Bindings:

```bash
# Listar roles criadas pelo Rancher
kubectl get clusterroles | grep rancher | wc -l
# ~50+ roles

# Exemplos:
# rancher-admin       → acesso total
# rancher-view        → só leitura
# rancher-member      → gerenciar workloads
```

Você **não precisa criar RBAC manualmente** para começar — o Rancher faz isso pela GUI.

---

## 4. Criar usuário no Rancher (RBAC na prática)

1. Acesse https://rancher.local
2. **Users & Authentication > Users**
3. **Create** → Username: `dev-joao`, Password: definir
4. **Cluster Permissions**:
   - Cluster: `local`
   - Role: `Member` (pode ver e gerenciar workloads, mas não admin)
5. **Save**

Agora `dev-joao` pode:
- Ver pods, deployments, services
- Escalar réplicas
- Ver logs

Mas NÃO pode:
- Instalar Helm charts no cluster
- Deletar namespaces
- Modificar RBAC
- Acessar secrets de outros namespaces

---

## 5. Rancher vs LENS vs kubectl

| Aspecto | kubectl | LENS | Rancher |
|---------|---------|------|---------|
| Tipo | CLI | App Desktop | Web |
| RBAC / Usuários | Não (usa kubeconfig) | Não | ✅ Sim |
| Multi-cluster | Manual (contextos) | Sim (abas) | ✅ Sim (centralizado) |
| Catálogo apps | `helm install` | Parcial | ✅ Completo |
| Auditoria | Não | Não | ✅ Quem fez o quê |
| TLS/HTTPS | N/A | N/A | ✅ Obrigatório |
| Custo | Gratuito | Gratuito (básico) | ✅ Gratuito |
| Peso | Zero | ~500MB | ~2GB RAM |

---

## 6. Fluxo HTTPS completo (Rancher)

```
[Browser]
    │ https://rancher.local
    ▼
[Ingress Controller (nginx)]
    │ Lê Secret TLS "tls-rancher-ingress"
    │ Descriptografa com a chave privada
    ▼
[Service rancher → Pod rancher]
    │ HTTP interno (dentro do cluster não precisa TLS)
    ▼
[Rancher Server responde]
    │
    ▼
[Ingress criptografa resposta com TLS]
    │
    ▼
[Browser recebe HTTPS]
```

O certificado autoassinado faz o browser mostrar "⚠️ Não seguro" — normal em dev. Em produção, usa-se cert-manager + Let's Encrypt para certificados válidos.

---

## 7. Verificar a instalação

```bash
# Pods do Rancher
kubectl get pods -n cattle-system

# Ingress (deve ter TLS)
kubectl get ingress -n cattle-system
kubectl describe ingress rancher -n cattle-system

# Secret TLS
kubectl get secrets -n cattle-system -o name | grep tls

# cert-manager pods
kubectl get pods -n cert-manager

# RBAC criado pelo Rancher
kubectl get clusterroles | grep rancher | head -10
kubectl get clusterrolebindings | grep rancher | head -10
```

---

## 8. Instalar Helm Chart via Rancher (Apps & Marketplace)

Um dos grandes benefícios do Rancher é instalar charts pelo browser sem precisar de `helm install` no terminal. Vamos instalar o **Redis** como exemplo prático.

### Passo a passo

1. Acesse https://rancher.local
2. No menu lateral, clique no cluster **local**
3. Vá em **Apps > Charts** (ou **Apps & Marketplace > Charts**)
4. Na barra de busca, digite: `redis`
5. Clique no chart **Redis** (do repositório Bitnami)
6. Clique **Install**

### Configuração

| Campo | Valor |
|-------|-------|
| Namespace | `crud-lab` (selecione ou crie) |
| Name | `redis-lab` |
| Chart Version | (manter a padrão) |

7. Na aba **Values YAML** (ou **Edit YAML**), ajuste para um lab leve:

```yaml
architecture: standalone          # Sem réplica (lab)
auth:
  enabled: false                  # Sem senha (só lab!)
master:
  resources:
    requests:
      memory: "128Mi"
    limits:
      memory: "256Mi"
```

8. Clique **Install** (ou **Next > Install**)
9. Aguarde o status mudar para **Deployed**

### Verificar

No terminal ou pelo próprio Rancher (Workloads > Pods):

```bash
# Ver pod do Redis
kubectl get pods -n crud-lab | grep redis

# Testar conexão
kubectl exec -n crud-lab deploy/redis-lab-master -- redis-cli ping
# Esperado: PONG
```

### O que aconteceu por trás

O Rancher executou o equivalente a:
```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install redis-lab bitnami/redis -n crud-lab \
  --set architecture=standalone \
  --set auth.enabled=false \
  --set master.resources.requests.memory=128Mi \
  --set master.resources.limits.memory=256Mi
```

Você fez tudo pelo browser — sem digitar um comando.

### Gerenciar o chart instalado

1. Vá em **Apps > Installed Apps**
2. Clique em `redis-lab`
3. Opções disponíveis:
   - **Upgrade** — alterar values (ex: habilitar auth)
   - **Rollback** — voltar para versão anterior
   - **Delete** — remover completamente

### Remover (após testar)

Pelo Rancher: **Apps > Installed Apps > redis-lab > Delete**

Ou pelo terminal:
```bash
helm uninstall redis-lab -n crud-lab
```

### Analogia Rancher vs Terminal

| Ação | Terminal | Rancher GUI |
|------|---------|-------------|
| Buscar chart | `helm search repo redis` | Apps > Charts > buscar |
| Instalar | `helm install ...` | Install > preencher campos |
| Alterar config | `helm upgrade --set ...` | Installed Apps > Upgrade |
| Rollback | `helm rollback redis-lab 1` | Installed Apps > Rollback |
| Remover | `helm uninstall redis-lab` | Installed Apps > Delete |

---

## 9. Troubleshooting

### Rancher não abre (connection refused)
→ Pods ainda subindo: `kubectl get pods -n cattle-system -w`
→ Ingress não tem IP: verificar se Ingress Controller está rodando

### Certificado inválido no browser
→ Normal para certificados autoassinados. Clique "Avançado > Continuar"
→ Em Chrome: `thisisunsafe` (digite no teclado na página de erro)

### Rancher pods em CrashLoopBackOff
→ Verificar logs: `kubectl logs -n cattle-system -l app=rancher`
→ Geralmente: cert-manager não está pronto. Aguarde 2-3 min.

### Memória insuficiente
→ Docker Desktop Settings > Resources > aumente para 10-12GB

---

## Ordem de estudo sugerida

1. **cert-manager** → entender certificados TLS e por que são necessários
2. **Secrets TLS** → entender como certificados são armazenados no K8s
3. **Instalar Rancher** → ver o Helm install e namespace cattle-system
4. **HTTPS/Ingress** → ver como Ingress usa TLS Secret
5. **Primeiro acesso** → login, explorar o painel
6. **RBAC conceitual** → Role, ClusterRole, Binding
7. **Criar usuário** → RBAC na prática via GUI do Rancher
8. **Instalar chart via GUI** → Apps & Marketplace (Redis como exemplo)
9. **Comparar** → Rancher GUI vs kubectl para as mesmas tarefas
