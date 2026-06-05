# Roteiro de Estudo: Helm

## 1. O que é Helm?

**Analogia:** Se os YAMLs do Kubernetes são "documentos avulsos", o Helm é um "pacote instalador" que agrupa tudo, permite configurar parâmetros e gerenciar versões.

**Sem Helm:** Você aplica 5+ arquivos YAML manualmente, e se precisar mudar o nome da imagem, edita em vários lugares.
**Com Helm:** `helm install crud-lab ./helm/crud-app` → tudo é criado de uma vez, parametrizado.

---

## 2. Estrutura de um Helm Chart

```
helm/crud-app/
├── Chart.yaml              # Metadados do chart (nome, versão)
├── values.yaml             # Valores padrão (configuráveis)
└── templates/              # Templates YAML com variáveis
    ├── deployment.yaml
    └── service.yaml
```

---

## 3. Chart.yaml — Identidade do chart

```yaml
apiVersion: v2
name: crud-app                              # Nome do chart
description: CRUD App Helm Chart para aprendizado
version: 0.1.0                              # Versão do CHART (infra)
appVersion: "1.0.0"                         # Versão da APP (código)
```

| Campo | Significado |
|-------|-------------|
| `version` | Versão do chart. Incrementa quando muda templates/values |
| `appVersion` | Versão da aplicação. Incrementa quando muda o código |

---

## 4. values.yaml — Parâmetros configuráveis

Este é o "painel de controle" do chart. Quem instala pode sobrescrever qualquer valor.

```yaml
replicaCount: 2                    # Quantos pods da app

image:
  repository: localhost:5000/crud-k8s-lab    # Imagem Docker
  tag: latest                                 # Tag da imagem
  pullPolicy: Always                          # Sempre puxa a imagem

service:
  type: ClusterIP                  # Tipo do Service
  port: 80                         # Porta exposta

ingress:
  enabled: true                    # Criar Ingress ou não
  host: crud-app.local             # Hostname do Ingress

app:
  db:
    host: postgres
    port: 5432
    name: crudlab
    user: postgres
    password: postgres
  minio:
    url: http://minio:9000
    accessKey: minioadmin
    secretKey: minioadmin
    bucket: produtos
```

### Como sobrescrever valores na instalação:

```bash
# Via --set (um valor)
helm install crud-lab ./helm/crud-app --set replicaCount=3

# Via --set (valor aninhado)
helm install crud-lab ./helm/crud-app --set app.db.password=senhaForte

# Via arquivo de valores customizado
helm install crud-lab ./helm/crud-app -f meus-valores.yaml
```

---

## 5. Templates — YAMLs com variáveis

Os templates usam a sintaxe Go Template `{{ }}` para injetar valores do `values.yaml`.

### deployment.yaml do nosso chart:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Release.Name }}-app              # Ex: "crud-lab-app"
spec:
  replicas: {{ .Values.replicaCount }}       # Vem do values.yaml → 2
  selector:
    matchLabels:
      app: {{ .Release.Name }}-app
  template:
    metadata:
      labels:
        app: {{ .Release.Name }}-app
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: /actuator/prometheus
        prometheus.io/port: "8080"
    spec:
      containers:
        - name: app
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          # Resultado: "localhost:5000/crud-k8s-lab:latest"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: 8080
          env:
            - name: DB_HOST
              value: {{ .Values.app.db.host }}        # → "postgres"
            - name: DB_PORT
              value: "{{ .Values.app.db.port }}"      # → "5432"
            - name: DB_NAME
              value: {{ .Values.app.db.name }}        # → "crudlab"
            - name: DB_USER
              value: {{ .Values.app.db.user }}        # → "postgres"
            - name: DB_PASS
              value: {{ .Values.app.db.password }}    # → "postgres"
            - name: MINIO_URL
              value: {{ .Values.app.minio.url }}
            - name: MINIO_ACCESS_KEY
              value: {{ .Values.app.minio.accessKey }}
            - name: MINIO_SECRET_KEY
              value: {{ .Values.app.minio.secretKey }}
            - name: MINIO_BUCKET
              value: {{ .Values.app.minio.bucket }}
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
```

### service.yaml do nosso chart:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Release.Name }}-app
spec:
  selector:
    app: {{ .Release.Name }}-app
  ports:
    - port: {{ .Values.service.port }}       # → 80
      targetPort: 8080
---
{{- if .Values.ingress.enabled }}            # Condicional! Só cria se enabled=true
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ .Release.Name }}-app
spec:
  rules:
    - host: {{ .Values.ingress.host }}       # → "crud-app.local"
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: {{ .Release.Name }}-app
                port:
                  number: {{ .Values.service.port }}
{{- end }}
```

---

## 6. Variáveis built-in do Helm

| Variável | Valor | Exemplo |
|----------|-------|---------|
| `{{ .Release.Name }}` | Nome dado no `helm install` | `crud-lab` |
| `{{ .Release.Namespace }}` | Namespace do deploy | `crud-lab` |
| `{{ .Values.xxx }}` | Valor do values.yaml | `{{ .Values.replicaCount }}` → 2 |
| `{{ .Chart.Name }}` | Nome do Chart.yaml | `crud-app` |
| `{{ .Chart.Version }}` | Versão do Chart.yaml | `0.1.0` |

---

## 7. Condicionais e loops

### Condicional (if):

```yaml
{{- if .Values.ingress.enabled }}
# ... cria o Ingress ...
{{- end }}
```

Se `ingress.enabled: false` no values.yaml, o Ingress NÃO é criado.

### Loop (range):

```yaml
env:
{{- range $key, $value := .Values.app.env }}
  - name: {{ $key }}
    value: {{ $value }}
{{- end }}
```

---

## 8. Comandos Helm essenciais

```bash
# ─── INSTALAR ──────────────────────────────────────
helm install <release-name> <chart-path> -n <namespace> --create-namespace
helm install crud-lab ./helm/crud-app -n crud-lab --create-namespace

# ─── LISTAR RELEASES ──────────────────────────────
helm list -n crud-lab

# ─── VER O QUE SERIA GERADO (sem aplicar) ─────────
helm template crud-lab ./helm/crud-app
# Mostra os YAMLs finais com valores substituídos — ótimo para debug!

# ─── VALIDAR CHART ────────────────────────────────
helm lint ./helm/crud-app

# ─── ATUALIZAR (após mudar values ou templates) ───
helm upgrade crud-lab ./helm/crud-app -n crud-lab

# ─── VER HISTÓRICO DE RELEASES ────────────────────
helm history crud-lab -n crud-lab

# ─── ROLLBACK ─────────────────────────────────────
helm rollback crud-lab 1 -n crud-lab    # Volta para revisão 1

# ─── DESINSTALAR ──────────────────────────────────
helm uninstall crud-lab -n crud-lab

# ─── INSTALAR CHARTS DE TERCEIROS ─────────────────
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm search repo prometheus
helm install monitoring prometheus-community/kube-prometheus-stack -n monitoring
```

---

## 9. helm template — Seu melhor amigo para debug

Antes de aplicar, veja o YAML final gerado:

```bash
helm template crud-lab ./helm/crud-app
```

Saída (exemplo parcial):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: crud-lab-app          # ← .Release.Name + "-app"
spec:
  replicas: 2                 # ← .Values.replicaCount
  ...
    containers:
      - name: app
        image: "localhost:5000/crud-k8s-lab:latest"   # ← .Values.image.*
```

Se algo estiver errado, você vê aqui antes de aplicar no cluster.

---

## 10. Fluxo de trabalho com Helm

```
1. Editar values.yaml ou templates/
        │
2. helm lint ./helm/crud-app          ← Valida sintaxe
        │
3. helm template crud-lab ./helm/crud-app   ← Preview do YAML gerado
        │
4. helm upgrade crud-lab ./helm/crud-app -n crud-lab   ← Aplica no cluster
        │
5. kubectl get all -n crud-lab        ← Verifica resultado
        │
6. (Se deu errado) helm rollback crud-lab <revisão> -n crud-lab
```

---

## 11. YAMLs puros vs Helm — Comparação

| Aspecto | YAMLs puros (`k8s/`) | Helm (`helm/crud-app/`) |
|---------|---------------------|------------------------|
| Parametrização | Valores hardcoded | Configurável via values.yaml |
| Reutilização | Copiar e editar | Mesmo chart, valores diferentes |
| Versionamento | Manual | `helm history`, rollback automático |
| Instalação | `kubectl apply -f` (vários arquivos) | `helm install` (um comando) |
| Remoção | `kubectl delete -f` (vários arquivos) | `helm uninstall` (um comando) |
| Condicionais | Não tem | `{{- if }}` |
| Ecossistema | - | Charts prontos (Prometheus, Grafana, etc.) |

---

## 12. Charts de terceiros (Etapa 4 do README)

Helm tem um ecossistema de charts prontos. Na Etapa 4 usamos:

```bash
# Adicionar repositório
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts

# Buscar charts disponíveis
helm search repo kube-prometheus

# Instalar com valores customizados via --set
helm install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  --set grafana.adminPassword=admin \
  --set grafana.service.type=NodePort \
  --set grafana.service.nodePort=30300
```

Isso instala Prometheus + Grafana + Alertmanager + dashboards prontos — tudo configurado e integrado. É o poder do Helm: reutilizar trabalho da comunidade.

---

## Ordem de estudo sugerida

1. **Chart.yaml** → entender identidade do chart
2. **values.yaml** → entender parametrização
3. **templates/** → entender substituição de variáveis
4. **helm template** → ver o resultado final
5. **helm install/upgrade** → aplicar no cluster
6. **helm history/rollback** → gerenciar versões
7. **Charts de terceiros** → reutilizar da comunidade
