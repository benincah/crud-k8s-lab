# Roteiro de Estudo: PostgreSQL em Container vs Instalado

## 1. As duas formas de rodar PostgreSQL

| Forma | Como |
|-------|------|
| **Instalado na máquina** | `apt install postgresql` → roda como serviço do SO |
| **Em container** | `docker run postgres` → roda isolado dentro do Docker/K8s |

---

## 2. Comparação detalhada

### Performance

| Aspecto | Container | Instalado |
|---------|-----------|-----------|
| I/O de disco | Leve overhead (depende do storage driver) | Acesso direto ao disco |
| CPU/Memória | Praticamente igual (container não é VM) | Nativo |
| Rede | Leve overhead (bridge network) | Nativo |
| **Impacto real** | **~1-3% de overhead** | Baseline |

> Para 99% dos casos, a diferença de performance é irrelevante. Só importa em workloads extremos (milhões de transações/segundo).

### Gerenciamento

| Aspecto | Container | Instalado |
|---------|-----------|-----------|
| Instalação | `docker run` (1 comando) | `apt install` + configurar |
| Atualização | Trocar tag da imagem | `apt upgrade` + cuidar de breaking changes |
| Múltiplas versões | Fácil (cada container com sua versão) | Complexo (portas diferentes, configs) |
| Backup | Volume snapshot ou pg_dump | pg_dump ou pg_basebackup |
| Configuração | Variáveis de ambiente ou ConfigMap | Editar postgresql.conf |
| Reprodutibilidade | 100% (mesma imagem = mesmo resultado) | Depende do SO, versão, configs |

### Segurança

| Aspecto | Container | Instalado |
|---------|-----------|-----------|
| Isolamento | Container isolado do host | Processo no SO (acesso ao filesystem) |
| Superfície de ataque | Menor (imagem mínima alpine) | Maior (SO completo) |
| Atualizações de segurança | Rebuild da imagem | apt update |

---

## 3. PostgreSQL em Container — Cuidados IMPORTANTES

### ⚠️ Cuidado 1: Persistência de dados

**Problema:** Se o container morrer sem volume, TODOS os dados são perdidos.

**Solução:** SEMPRE use volumes.

```yaml
# Docker Compose
volumes:
  - pgdata:/var/lib/postgresql/data

# Kubernetes
volumeMounts:
  - name: pgdata
    mountPath: /var/lib/postgresql/data
```

No nosso `k8s/01-postgres.yaml` usamos `emptyDir` (dados perdidos se pod morre) — isso é OK para **aprendizado**. Em produção, use `PersistentVolumeClaim`:

```yaml
# Produção
volumes:
  - name: pgdata
    persistentVolumeClaim:
      claimName: postgres-pvc
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes: ["ReadWriteOnce"]
  resources:
    requests:
      storage: 10Gi
```

### ⚠️ Cuidado 2: Recursos (CPU/Memória)

Sem limites, o PostgreSQL pode consumir toda a memória do node.

```yaml
# Kubernetes - definir limites
containers:
  - name: postgres
    resources:
      requests:
        memory: "256Mi"
        cpu: "250m"
      limits:
        memory: "512Mi"
        cpu: "500m"
```

### ⚠️ Cuidado 3: Backup

Container não faz backup sozinho. Configure:

```bash
# Backup manual (pg_dump)
kubectl exec -n crud-lab deploy/postgres -- pg_dump -U postgres crudlab > backup.sql

# Restore
cat backup.sql | kubectl exec -i -n crud-lab deploy/postgres -- psql -U postgres crudlab
```

### ⚠️ Cuidado 4: Não escale PostgreSQL com replicas

```yaml
# ❌ ERRADO - múltiplas instâncias escrevendo no mesmo banco = corrupção
replicas: 3

# ✅ CORRETO - banco de dados é stateful, use 1 réplica
replicas: 1
```

Para alta disponibilidade, use soluções específicas (Patroni, CloudNativePG operator).

### ⚠️ Cuidado 5: Senhas em produção

```yaml
# ❌ ERRADO - senha hardcoded
environment:
  POSTGRES_PASSWORD: postgres

# ✅ CORRETO - usar Secret
env:
  - name: POSTGRES_PASSWORD
    valueFrom:
      secretKeyRef:
        name: postgres-secret
        key: POSTGRES_PASSWORD
```

---

## 4. Quando usar cada abordagem

### Use Container quando:

- ✅ Ambiente de desenvolvimento/staging
- ✅ CI/CD (testes automatizados)
- ✅ Precisa de reprodutibilidade (mesma versão sempre)
- ✅ Múltiplos projetos com versões diferentes de PostgreSQL
- ✅ Kubernetes é a plataforma padrão
- ✅ Equipe já trabalha com containers

### Use Instalado quando:

- ✅ Produção com workload muito pesado (milhões de transações)
- ✅ Precisa de tuning fino de kernel (huge pages, NUMA)
- ✅ Equipe de DBA dedicada que gerencia o servidor
- ✅ Compliance exige controle total do ambiente
- ✅ Infraestrutura legada sem containers

### Tendência do mercado:

A maioria das empresas está migrando para **PostgreSQL em container** (Kubernetes) ou **managed services** (AWS RDS, Azure Database). Instalar na máquina está cada vez menos comum.

---

## 5. PostgreSQL no nosso projeto

### Docker Compose (dev local):

```yaml
postgres:
  image: postgres:16-alpine       # Imagem oficial, versão alpine (leve)
  environment:
    POSTGRES_DB: crudlab           # Cria o banco automaticamente
    POSTGRES_USER: postgres        # Usuário admin
    POSTGRES_PASSWORD: postgres    # Senha
  ports:
    - "5432:5432"                  # Acessível do host
  volumes:
    - pgdata:/var/lib/postgresql/data   # Dados persistem
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U postgres"]
    interval: 5s
    timeout: 3s
    retries: 5
```

### Kubernetes (k8s/01-postgres.yaml):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secret
stringData:
  POSTGRES_DB: crudlab
  POSTGRES_USER: postgres
  POSTGRES_PASSWORD: postgres
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
spec:
  replicas: 1                    # SEMPRE 1 para banco de dados
  template:
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          envFrom:
            - secretRef:
                name: postgres-secret   # Credenciais via Secret
          volumeMounts:
            - name: pgdata
              mountPath: /var/lib/postgresql/data
      volumes:
        - name: pgdata
          emptyDir: {}           # ⚠️ Apenas para lab! Produção usa PVC
---
apiVersion: v1
kind: Service
metadata:
  name: postgres                 # DNS interno: postgres:5432
spec:
  ports:
    - port: 5432
```

### Como a app se conecta:

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:crudlab}
    username: ${DB_USER:postgres}
    password: ${DB_PASS:postgres}
```

- No Docker Compose: `DB_HOST=postgres` (nome do serviço)
- No Kubernetes: `DB_HOST=postgres` (nome do Service)
- Localmente: `DB_HOST=localhost` (valor padrão)

---

## 6. Comandos úteis

```bash
# ─── Docker Compose ───────────────────────────────
# Conectar ao banco
docker compose exec postgres psql -U postgres crudlab

# ─── Kubernetes ───────────────────────────────────
# Conectar ao banco
kubectl exec -it -n crud-lab deploy/postgres -- psql -U postgres crudlab

# ─── Dentro do psql ──────────────────────────────
\l                    -- Listar bancos
\dt                   -- Listar tabelas
\d produtos           -- Descrever tabela produtos
SELECT * FROM produtos;
\q                    -- Sair
```

---

## 7. Checklist para produção com PostgreSQL em container

- [ ] PersistentVolumeClaim com storage class adequado
- [ ] Limites de CPU e memória definidos
- [ ] Backup automatizado (CronJob com pg_dump)
- [ ] Senhas em Secrets (nunca hardcoded)
- [ ] `replicas: 1` (ou usar operator como CloudNativePG)
- [ ] Monitoramento (postgres_exporter para Prometheus)
- [ ] Configuração de `shared_buffers`, `work_mem` via ConfigMap
- [ ] Network Policy (só a app acessa o banco)

---

## Ordem de estudo sugerida

1. **Container vs Instalado** → entender trade-offs
2. **Volumes** → entender persistência
3. **Secrets** → entender credenciais seguras
4. **Service** → entender DNS interno (app → postgres)
5. **Healthcheck** → entender dependência de inicialização
6. **Backup/Restore** → entender operações do dia a dia
