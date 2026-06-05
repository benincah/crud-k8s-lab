# Roteiro de Estudo: Kubernetes Essencial

## 1. Namespace

**O que é:** Um "espaço isolado" dentro do cluster. Serve para organizar recursos por projeto/equipe/ambiente.

**Analogia:** Pastas no seu computador. Cada pasta agrupa arquivos relacionados.

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: crud-lab    # Nome do namespace
```

**Comandos úteis:**
```bash
kubectl get namespaces              # Listar todos
kubectl create namespace meu-ns     # Criar
kubectl get pods -n crud-lab        # Listar pods de um namespace
kubectl get all -n crud-lab         # Listar TUDO de um namespace
```

---

## 2. Pod

**O que é:** A menor unidade no Kubernetes. Um Pod é um ou mais containers rodando juntos.

**Analogia:** Um Pod é como um "servidor virtual" que roda sua aplicação.

> Você raramente cria Pods diretamente. Usa Deployments que criam Pods para você.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: meu-pod
  namespace: crud-lab
spec:
  containers:
    - name: app
      image: localhost:5000/crud-k8s-lab:latest
      ports:
        - containerPort: 8080
```

**Comandos úteis:**
```bash
kubectl get pods -n crud-lab                    # Listar pods
kubectl describe pod <nome> -n crud-lab         # Detalhes do pod
kubectl logs <nome> -n crud-lab                 # Ver logs
kubectl logs <nome> -n crud-lab -f              # Logs em tempo real
kubectl exec -it <nome> -n crud-lab -- /bin/sh  # Entrar no container
kubectl delete pod <nome> -n crud-lab           # Deletar (Deployment recria)
```

---

## 3. Deployment

**O que é:** Gerencia Pods. Define quantas réplicas rodar, qual imagem usar, e garante que os Pods estejam sempre rodando.

**Analogia:** Um "gerente" que garante que sempre tenha X funcionários trabalhando. Se um sai, ele contrata outro.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: crud-app              # Nome do deployment
  namespace: crud-lab
spec:
  replicas: 2                 # Quantos pods manter rodando
  selector:
    matchLabels:
      app: crud-app           # Como identificar os pods deste deployment
  template:                   # Template do Pod que será criado
    metadata:
      labels:
        app: crud-app         # Label que o selector usa para encontrar
    spec:
      containers:
        - name: crud-app
          image: localhost:5000/crud-k8s-lab:latest   # Imagem Docker
          ports:
            - containerPort: 8080                      # Porta do container
          env:                                         # Variáveis de ambiente
            - name: DB_HOST
              value: postgres
          readinessProbe:       # "Está pronto para receber tráfego?"
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
          livenessProbe:        # "Ainda está vivo?"
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
```

### Campos importantes explicados:

| Campo | O que faz |
|-------|-----------|
| `replicas` | Quantas cópias do pod manter |
| `selector.matchLabels` | Como o Deployment encontra seus pods |
| `template.metadata.labels` | Labels que os pods terão (deve bater com selector) |
| `image` | Imagem Docker a usar |
| `env` | Variáveis de ambiente passadas ao container |
| `readinessProbe` | K8s só envia tráfego quando essa checagem passa |
| `livenessProbe` | Se falhar, K8s reinicia o container |

**Comandos úteis:**
```bash
kubectl get deployments -n crud-lab
kubectl describe deployment crud-app -n crud-lab
kubectl scale deployment crud-app --replicas=3 -n crud-lab   # Escalar
kubectl rollout status deployment crud-app -n crud-lab       # Status do rollout
kubectl rollout restart deployment crud-app -n crud-lab      # Reiniciar pods
```

---

## 4. Service

**O que é:** Expõe os Pods na rede interna do cluster. Dá um nome DNS fixo para acessar os pods, mesmo que eles morram e renasçam com IPs diferentes.

**Analogia:** Um "balanceador de carga" interno. Você acessa pelo nome (ex: `postgres`) e ele direciona para o pod correto.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: crud-app              # Este nome vira DNS interno: crud-app.crud-lab.svc.cluster.local
  namespace: crud-lab
spec:
  selector:
    app: crud-app             # Direciona tráfego para pods com esta label
  ports:
    - port: 80                # Porta que o Service expõe
      targetPort: 8080        # Porta real do container
```

### Tipos de Service:

| Tipo | Uso |
|------|-----|
| `ClusterIP` (padrão) | Acessível apenas dentro do cluster |
| `NodePort` | Expõe numa porta do node (30000-32767) |
| `LoadBalancer` | Cria um load balancer externo (cloud) |

```yaml
# Exemplo NodePort (acessível de fora do cluster)
spec:
  type: NodePort
  selector:
    app: crud-app
  ports:
    - port: 80
      targetPort: 8080
      nodePort: 30080       # Acesse via http://<IP-NODE>:30080
```

**Como funciona o DNS interno:**
```
# Dentro do cluster, a app acessa o postgres assim:
jdbc:postgresql://postgres:5432/crudlab
#                 ^^^^^^^^
#                 Nome do Service = DNS automático
```

**Comandos úteis:**
```bash
kubectl get svc -n crud-lab
kubectl describe svc crud-app -n crud-lab
kubectl port-forward svc/crud-app 8080:80 -n crud-lab   # Acessar do seu PC
```

---

## 5. Ingress

**O que é:** Roteador HTTP externo. Recebe requisições de fora do cluster e direciona para o Service correto baseado no hostname ou path.

**Analogia:** Um "recepcionista" na entrada do prédio. Você diz "quero falar com o Grafana" e ele te direciona para a sala certa.

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: crud-app
  namespace: crud-lab
spec:
  rules:
    - host: crud-app.local          # Quando acessar este domínio...
      http:
        paths:
          - path: /                  # ...qualquer path...
            pathType: Prefix
            backend:
              service:
                name: crud-app      # ...vai para este Service...
                port:
                  number: 80        # ...nesta porta
```

### Múltiplos hosts no mesmo Ingress:

```yaml
spec:
  rules:
    - host: crud-app.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: crud-app
                port:
                  number: 80
    - host: minio.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: minio
                port:
                  number: 9001
```

**Pré-requisito:** Precisa de um Ingress Controller instalado (K3s já vem com Traefik).

**Comandos úteis:**
```bash
kubectl get ingress -n crud-lab
kubectl describe ingress crud-app -n crud-lab
```

---

## 6. Secret

**O que é:** Armazena dados sensíveis (senhas, tokens, chaves) de forma segura no cluster. Os pods acessam via variáveis de ambiente ou volumes.

**Analogia:** Um cofre. Você guarda a senha lá e dá acesso apenas a quem precisa.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secret
  namespace: crud-lab
type: Opaque
stringData:                     # stringData = texto puro (K8s codifica em base64)
  POSTGRES_DB: crudlab
  POSTGRES_USER: postgres
  POSTGRES_PASSWORD: postgres
```

### Como usar no Deployment:

```yaml
# Opção 1: Uma variável específica
env:
  - name: DB_PASS
    valueFrom:
      secretKeyRef:
        name: postgres-secret       # Nome do Secret
        key: POSTGRES_PASSWORD      # Chave dentro do Secret

# Opção 2: Todas as chaves do Secret como variáveis de ambiente
envFrom:
  - secretRef:
      name: postgres-secret
```

**Comandos úteis:**
```bash
kubectl get secrets -n crud-lab
kubectl describe secret postgres-secret -n crud-lab
kubectl get secret postgres-secret -n crud-lab -o yaml   # Ver conteúdo (base64)

# Decodificar valor
echo "cG9zdGdyZXM=" | base64 -d    # Resultado: postgres
```

---

## 7. ConfigMap (bônus)

**O que é:** Igual ao Secret, mas para dados NÃO sensíveis (configurações, URLs, flags).

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: crud-lab
data:
  MINIO_URL: http://minio:9000
  MINIO_BUCKET: produtos
```

---

## 8. Fluxo completo visual

```
[Usuário] → [Ingress] → [Service] → [Pod 1]
                                   → [Pod 2]  ← Deployment gerencia
                                   → [Pod N]

[Pod] lê credenciais do → [Secret]
[Pod] lê configs do     → [ConfigMap]
[Pod] grava dados no    → [PostgreSQL Service] → [PostgreSQL Pod]
```

---

## 9. Labels e Selectors (conceito transversal)

Labels são "etiquetas" nos recursos. Selectors são "filtros" que usam essas etiquetas.

```
Deployment (selector: app=crud-app)
    └── cria Pods com label: app=crud-app

Service (selector: app=crud-app)
    └── direciona tráfego para Pods com label: app=crud-app
```

É assim que Service sabe para quais Pods enviar tráfego, e Deployment sabe quais Pods são "seus".

---

## 10. Comandos essenciais do dia a dia

```bash
# Visão geral
kubectl get all -n crud-lab

# Debug
kubectl describe pod <nome> -n crud-lab    # Ver eventos e erros
kubectl logs <nome> -n crud-lab            # Ver logs
kubectl get events -n crud-lab             # Eventos do namespace

# Aplicar/remover YAMLs
kubectl apply -f arquivo.yaml              # Criar/atualizar
kubectl delete -f arquivo.yaml             # Remover

# Acesso rápido
kubectl port-forward svc/crud-app 8080:80 -n crud-lab

# Escalar
kubectl scale deployment crud-app --replicas=5 -n crud-lab
```

---

## Ordem de estudo sugerida

1. **Namespace** → entender isolamento
2. **Pod** → entender a unidade básica
3. **Deployment** → entender gerenciamento de pods
4. **Service** → entender rede interna
5. **Secret/ConfigMap** → entender configuração
6. **Ingress** → entender acesso externo
7. **Helm** → entender empacotamento de tudo acima
