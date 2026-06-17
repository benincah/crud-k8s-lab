# Roteiro de Estudo: Docker e Docker Compose

## 1. O que é Docker?

**Analogia:** Uma "caixa" que empacota sua aplicação com tudo que ela precisa para rodar (código, runtime, bibliotecas, configs). Funciona igual em qualquer máquina.

**Sem Docker:** "Na minha máquina funciona" → instalar Java, configurar variáveis, versões diferentes...
**Com Docker:** `docker run minha-app` → funciona em qualquer lugar.

---

## 2. Dockerfile — A receita da imagem

O Dockerfile é um arquivo de instruções para construir uma imagem Docker. Cada linha cria uma "camada".

### Nosso Dockerfile (multi-stage):

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Explicação linha a linha:

| Linha | O que faz |
|-------|-----------|
| `FROM maven:3.9-eclipse-temurin-17 AS build` | Usa imagem com Maven+JDK para compilar. Nomeia esse estágio como "build" |
| `WORKDIR /app` | Define o diretório de trabalho dentro do container |
| `COPY pom.xml .` | Copia só o pom.xml primeiro (otimiza cache de dependências) |
| `RUN mvn dependency:go-offline` | Baixa dependências. Se pom.xml não mudar, usa cache |
| `COPY src ./src` | Copia o código fonte |
| `RUN mvn package -DskipTests` | Compila e gera o .jar |
| `FROM eclipse-temurin:17-jre-jammy` | **Novo estágio!** Imagem mínima só com JRE (sem Maven, sem código fonte) |
| `COPY --from=build /app/target/*.jar app.jar` | Copia APENAS o .jar do estágio anterior |
| `EXPOSE 8080` | Documenta que o container usa porta 8080 |
| `ENTRYPOINT ["java", "-jar", "app.jar"]` | Comando que roda quando o container inicia |

### Por que multi-stage?

| Abordagem | Tamanho da imagem |
|-----------|-------------------|
| Estágio único (Maven + JDK + código) | ~800MB |
| Multi-stage (só JRE + .jar) | ~280MB |

O primeiro estágio compila. O segundo estágio só tem o resultado. Código fonte, Maven, e JDK ficam de fora da imagem final.

### Comandos Docker essenciais:

```bash
# Construir imagem a partir do Dockerfile
docker build -t crud-k8s-lab:latest .

# Listar imagens locais
docker images

# Rodar um container
docker run -p 8080:8080 crud-k8s-lab:latest

# Ver containers rodando
docker ps

# Ver logs de um container
docker logs <container_id>

# Parar container
docker stop <container_id>

# Remover imagem
docker rmi crud-k8s-lab:latest
```

---

## 3. Docker Compose — Orquestrar múltiplos containers

**O que é:** Um arquivo YAML que define e roda múltiplos containers juntos. Cria uma rede interna onde eles se comunicam pelo nome do serviço.

**Analogia:** Se o Dockerfile é a receita de UM prato, o Docker Compose é o cardápio completo do restaurante.

### Nosso docker-compose.yml explicado:

```yaml
services:
  # ─── APLICAÇÃO ───────────────────────────────────────
  app:
    build: .                    # Builda usando o Dockerfile local
    ports:
      - "8080:8080"             # host:container
    environment:                # Variáveis de ambiente
      DB_HOST: postgres         # Nome do serviço = DNS interno
      DB_PORT: 5432
      DB_NAME: crudlab
      DB_USER: postgres
      DB_PASS: postgres
      MINIO_URL: http://minio:9000    # "minio" = nome do serviço abaixo
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
      MINIO_BUCKET: produtos
    depends_on:
      postgres:
        condition: service_healthy    # Só inicia após postgres estar saudável

  # ─── BANCO DE DADOS ─────────────────────────────────
  postgres:
    image: postgres:16-alpine         # Imagem pronta do Docker Hub
    environment:
      POSTGRES_DB: crudlab
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data   # Dados persistem entre restarts
    healthcheck:                           # Define quando o serviço está "saudável"
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  # ─── OBJECT STORAGE ─────────────────────────────────
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"   # Comando customizado
    ports:
      - "9000:9000"             # API
      - "9001:9001"             # Console web
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - miniodata:/data

  # ─── MONITORAMENTO ──────────────────────────────────
  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml   # Monta arquivo local

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin

  # ─── ADMINISTRAÇÃO DO BANCO ─────────────────────────
  pgadmin:
    image: dpage/pgadmin4
    ports:
      - "5050:80"              # Acesse http://localhost:5050
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@admin.com
      PGADMIN_DEFAULT_PASSWORD: admin
    depends_on:
      - postgres              # Só inicia após postgres subir

# ─── VOLUMES NOMEADOS ───────────────────────────────
volumes:
  pgdata:       # Dados do PostgreSQL persistem mesmo após "docker compose down"
  miniodata:    # Dados do MinIO persistem
```

---

## 4. Conceitos-chave do Docker Compose

### Rede interna automática

Todos os serviços ficam na mesma rede. Um acessa o outro pelo **nome do serviço**:

```
app → postgres:5432      (não precisa de IP, usa o nome)
app → minio:9000
prometheus → app:8080
```

### Ports (host:container)

```yaml
ports:
  - "8080:8080"    # Porta do SEU PC : Porta DENTRO do container
  - "3000:3000"
```

Você acessa `localhost:8080` no browser → Docker redireciona para porta 8080 do container.

### Volumes

```yaml
volumes:
  - pgdata:/var/lib/postgresql/data    # Volume nomeado (Docker gerencia)
  - ./prometheus.yml:/etc/prometheus/prometheus.yml   # Bind mount (arquivo local)
```

| Tipo | Uso |
|------|-----|
| Volume nomeado (`pgdata:`) | Dados persistentes gerenciados pelo Docker |
| Bind mount (`./arquivo:`) | Monta um arquivo/pasta do host dentro do container |

### depends_on + healthcheck

```yaml
depends_on:
  postgres:
    condition: service_healthy   # Espera o healthcheck do postgres passar
```

Sem isso, a app poderia iniciar antes do banco estar pronto e dar erro de conexão.

---

## 5. Comandos Docker Compose essenciais

```bash
# Subir todos os serviços (build + start)
docker compose up --build -d

# Ver status dos serviços
docker compose ps

# Ver logs de todos
docker compose logs

# Ver logs de um serviço específico
docker compose logs app -f

# Parar tudo (containers param, dados dos volumes permanecem)
docker compose down

# Parar tudo E remover volumes (APAGA dados do banco!)
docker compose down -v

# Rebuild apenas um serviço
docker compose build app
docker compose up -d app

# Escalar um serviço (ex: 3 instâncias da app)
docker compose up -d --scale app=3
```

---

## 6. Fluxo visual

```
docker compose up --build
        │
        ├── Builda imagem da "app" usando Dockerfile
        │       ├── Stage 1: Maven compila → gera .jar
        │       └── Stage 2: JRE + .jar → imagem final (~280MB)
        │
        ├── Puxa imagem "postgres:16-alpine" do Docker Hub
        ├── Puxa imagem "minio/minio" do Docker Hub
        ├── Puxa imagem "prom/prometheus" do Docker Hub
        ├── Puxa imagem "grafana/grafana" do Docker Hub
        ├── Puxa imagem "dpage/pgadmin4" do Docker Hub
        │
        └── Cria rede interna + inicia todos os containers
                ├── postgres (espera healthcheck)
                ├── minio
                ├── prometheus
                ├── grafana
                ├── pgadmin (após postgres subir)
                └── app (inicia após postgres healthy)
```

---

## 7. Docker Compose vs Kubernetes

| Docker Compose | Kubernetes |
|----------------|------------|
| Ambiente local/dev | Produção/staging |
| Um único host | Múltiplos nodes |
| `docker compose up` | `kubectl apply` |
| Simples, rápido | Complexo, resiliente |
| Sem auto-healing | Reinicia pods automaticamente |
| Sem auto-scaling | Escala baseado em métricas |

**Neste projeto:** Usamos Docker Compose na Etapa 2 (dev local) e Kubernetes nas Etapas 5-7 (simulando produção).

---

## Ordem de estudo sugerida

1. **Dockerfile** → entender como criar imagens
2. **Multi-stage** → entender otimização de imagens
3. **docker compose up** → entender orquestração local
4. **Rede interna** → entender comunicação entre containers
5. **Volumes** → entender persistência de dados
6. **Healthcheck + depends_on** → entender ordem de inicialização
