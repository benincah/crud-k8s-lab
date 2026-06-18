# Roteiro de Estudo: MinIO (Object Storage)

## 1. O que é MinIO?

**Analogia:** Um "Google Drive programável". Você armazena arquivos (objetos) em pastas (buckets) e acessa via API.

MinIO é compatível com a API do Amazon S3 — ou seja, tudo que funciona com S3 funciona com MinIO. É ideal para:
- Upload de arquivos (PDFs, imagens, backups)
- Armazenamento de logs
- Data lake

---

## 2. Conceitos fundamentais

| Conceito | Analogia | Exemplo no projeto |
|----------|----------|-------------------|
| **Bucket** | Pasta raiz | `produtos` |
| **Object** | Arquivo dentro do bucket | `1/manual.pdf` |
| **Key** | Caminho do arquivo | `1/manual.pdf` (id do produto + nome) |
| **Endpoint** | URL do servidor MinIO | `http://minio:9000` |
| **Access Key** | Usuário | `minioadmin` |
| **Secret Key** | Senha | `minioadmin` |

---

## 3. MinIO no projeto

### Docker Compose:

```yaml
minio:
  image: minio/minio
  command: server /data --console-address ":9001"
  ports:
    - "9000:9000"     # API (aplicação usa esta)
    - "9001:9001"     # Console web (você acessa no browser)
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
  volumes:
    - miniodata:/data   # Dados persistem entre restarts
```

| Porta | Uso |
|-------|-----|
| 9000 | API S3 (a app Java se conecta aqui) |
| 9001 | Console web (interface visual) |

### Kubernetes (k8s/03-minio.yaml):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: minio
  namespace: crud-lab
spec:
  replicas: 1
  selector:
    matchLabels:
      app: minio
  template:
    metadata:
      labels:
        app: minio
    spec:
      containers:
        - name: minio
          image: minio/minio
          args: ["server", "/data", "--console-address", ":9001"]
          ports:
            - containerPort: 9000
            - containerPort: 9001
          env:
            - name: MINIO_ROOT_USER
              value: minioadmin
            - name: MINIO_ROOT_PASSWORD
              value: minioadmin
---
apiVersion: v1
kind: Service
metadata:
  name: minio
  namespace: crud-lab
spec:
  selector:
    app: minio
  ports:
    - name: api
      port: 9000
    - name: console
      port: 9001
```

---

## 4. Como a App Java usa o MinIO

### Configuração (MinioConfig.java):

```java
@Bean
public MinioClient minioClient() {
    return MinioClient.builder()
            .endpoint(url)              // http://minio:9000
            .credentials(accessKey, secretKey)
            .build();
}
```

### Upload de arquivo (ProdutoController.java):

```java
@PostMapping("/{id}/upload")
public ResponseEntity<Produto> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
    // ...
    minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucket)                              // "produtos"
            .object(id + "/" + file.getOriginalFilename()) // "1/manual.pdf"
            .stream(file.getInputStream(), file.getSize(), -1)
            .contentType(file.getContentType())
            .build());
    // ...
}
```

### Fluxo:

```
[Usuário] → POST /api/produtos/1/upload (com arquivo)
    → [App Java] → putObject → [MinIO bucket "produtos"]
                                    └── 1/manual.pdf
```

---

## 5. Console Web do MinIO

Acesse no browser:
- **Docker Compose:** `http://localhost:9001`
- **Kubernetes (Ingress):** `http://minio.local`
- **Kubernetes (port-forward):** `kubectl port-forward -n crud-lab svc/minio 9001:9001` → `http://localhost:9001`

Credenciais: minioadmin / minioadmin

- **Object Browser** → navegar arquivos dentro dos buckets
- **Buckets** → criar/listar buckets
- **Access Keys** → criar chaves para outras aplicações
- **Monitoring** → métricas do MinIO

### Criar bucket pelo Console:

1. Acesse o Console
2. **Buckets > Create Bucket**
3. Nome: `produtos`
4. **Create**

---

## 6. Criar bucket no Kubernetes (pod temporário)

No Kubernetes, você não tem o `mc` instalado na sua máquina, e o MinIO só é acessível dentro do cluster (via `minio:9000`). A solução é criar um **pod temporário** que roda o `mc` dentro do cluster:

```bash
kubectl run minio-mc -n crud-lab --image=minio/mc --restart=Never --command -- \
  sh -c "mc alias set myminio http://minio:9000 minioadmin minioadmin && mc mb myminio/produtos"
```

### O que acontece aqui?

1. `kubectl run minio-mc` → cria um pod chamado `minio-mc`
2. `--image=minio/mc` → usa a imagem oficial do MinIO Client
3. `--restart=Never` → roda uma vez e para (não reinicia em loop)
4. `mc alias set` → configura a conexão com o MinIO do cluster
5. `mc mb myminio/produtos` → cria o bucket

### Por que limpar o pod depois?

```bash
kubectl delete pod minio-mc -n crud-lab
```

O pod com `--restart=Never` **não se deleta sozinho** após terminar — ele fica com status `Completed` ocupando espaço na listagem. A limpeza é boa prática para não poluir o namespace com pods mortos.

> 💡 **Analogia:** é como chamar um eletricista (pod temporário) para instalar uma tomada (criar bucket). Depois que ele termina o serviço, você não precisa mais dele ali — então ele vai embora (delete).

### Alternativa: criar pelo Console web

Se preferir não usar CLI, acesse http://minio.local → **Buckets > Create Bucket** → nome `produtos` → **Create**.

---

## 7. MinIO CLI (mc) — uso local

```bash
# Instalar
curl https://dl.min.io/client/mc/release/linux-amd64/mc -o /usr/local/bin/mc
chmod +x /usr/local/bin/mc

# Configurar alias
mc alias set local http://localhost:9000 minioadmin minioadmin

# Listar buckets
mc ls local

# Criar bucket
mc mb local/produtos

# Upload de arquivo
mc cp meu-arquivo.pdf local/produtos/

# Listar objetos no bucket
mc ls local/produtos

# Download
mc cp local/produtos/1/manual.pdf ./download.pdf

# Ver info do objeto
mc stat local/produtos/1/manual.pdf
```

---

## 8. MinIO vs S3 (Amazon)

| Aspecto | MinIO | Amazon S3 |
|---------|-------|-----------|
| Onde roda | Self-hosted (seu servidor) | Cloud AWS |
| Custo | Gratuito | Pago por uso |
| API | Compatível com S3 | API original |
| Uso ideal | Dev/staging, on-premise | Produção cloud |
| Migração | Trocar endpoint e credenciais | - |

**Vantagem:** Você desenvolve com MinIO local e em produção troca para S3 mudando apenas a URL e credenciais no `application.yml`.

---

## 9. Boas práticas

- **Não armazene arquivos no banco de dados** — use object storage (MinIO/S3)
- **Use nomes de bucket descritivos** — `produtos`, `backups`, `logs`
- **Organize objetos por prefixo** — `{id}/{filename}` facilita listagem
- **Configure lifecycle policies** — deletar objetos antigos automaticamente
- **Em produção:** use replicação e versionamento de objetos

---

## Ordem de estudo sugerida

1. **Conceitos** → bucket, object, key
2. **Console web** → criar bucket, navegar objetos
3. **API via app** → entender MinioClient no Java
4. **mc CLI** → operações manuais
5. **Integração K8s** → Service `minio:9000` como DNS interno
