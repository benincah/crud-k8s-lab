# 🍃 Roteiro: Entendendo o Código ☕ Java (🍃 Spring Boot)

> 📖 Este documento complementa a **Etapa 1** do [README.md](../README.md) — onde você faz o build com Maven e gera o `.jar`.
> Aqui você vai entender **o que cada arquivo faz**, como as anotações funcionam e como o Spring Boot orquestra tudo.

---

## Visão Geral da Arquitetura

```
Request HTTP
     │
     ▼
┌─────────────────────┐
│  ProdutoController  │  ← Recebe requisições REST
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  ProdutoRepository  │  ← Acessa o banco (PostgreSQL)
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  Produto (Entity)   │  ← Representa a tabela no banco
└─────────────────────┘

MinioClient (injetado no Controller) → Faz upload de arquivos para o MinIO
```

---

## Estrutura de Pacotes

```
com.lab.crud/
├── Application.java          # Ponto de entrada
├── config/
│   └── MinioConfig.java      # Configuração do cliente MinIO
├── controller/
│   └── ProdutoController.java # Endpoints REST (CRUD + upload)
├── model/
│   └── Produto.java          # Entidade JPA (tabela "produtos")
└── repository/
    └── ProdutoRepository.java # Interface de acesso ao banco
```

---

## 1. Application.java — Ponto de Entrada

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### O que faz?
É o `main()` da aplicação. O Spring Boot inicia aqui.

### Anotação: `@SpringBootApplication`
É um atalho que combina 3 anotações:

| Anotação interna | O que faz |
|------------------|-----------|
| `@Configuration` | Marca a classe como fonte de configuração (beans) |
| `@EnableAutoConfiguration` | O Spring configura automaticamente tudo baseado nas dependências do pom.xml |
| `@ComponentScan` | Escaneia o pacote `com.lab.crud` e sub-pacotes buscando classes anotadas (`@Controller`, `@Repository`, `@Configuration`, etc.) |

**Analogia:** É como ligar a chave geral de uma casa — a energia (Spring) percorre todos os cômodos (pacotes) e liga o que encontrar conectado (beans).

---

## 2. Produto.java — A Entidade (Model)

```java
@Entity
@Table(name = "produtos")
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
    private String descricao;
    private Double preco;
    private String arquivoUrl;
    // getters e setters...
}
```

### O que faz?
Representa a tabela `produtos` no PostgreSQL. Cada instância = uma linha no banco.

### Anotações:

| Anotação | Significado |
|----------|-------------|
| `@Entity` | Diz ao JPA: "esta classe é uma tabela no banco" |
| `@Table(name = "produtos")` | Define o nome da tabela (sem isso, usaria o nome da classe) |
| `@Id` | Marca o campo como chave primária |
| `@GeneratedValue(strategy = IDENTITY)` | O banco gera o ID automaticamente (auto-increment) |
| `@Column(nullable = false)` | Coluna obrigatória — NOT NULL no banco |

**Analogia:** É a planta de um formulário. Cada campo (`nome`, `preco`) vira uma coluna na tabela. O `@Id` é o número do protocolo — único e gerado automaticamente.

### Correlação com o README:
- Na **Etapa 2**, quando você faz `curl -X POST .../api/produtos`, o JSON enviado é convertido para um objeto `Produto` e salvo no PostgreSQL.
- O campo `arquivoUrl` é preenchido na **Etapa 9** quando você faz upload de arquivo.

---

## 3. ProdutoRepository.java — Acesso ao Banco

```java
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
}
```

### O que faz?
Fornece todas as operações de banco (CRUD) **sem escrever SQL**. O Spring Data JPA implementa automaticamente.

### Como funciona?
Ao estender `JpaRepository<Produto, Long>`, você ganha de graça:

| Método herdado | SQL equivalente |
|----------------|-----------------|
| `findAll()` | `SELECT * FROM produtos` |
| `findById(id)` | `SELECT * FROM produtos WHERE id = ?` |
| `save(produto)` | `INSERT INTO` ou `UPDATE` |
| `deleteById(id)` | `DELETE FROM produtos WHERE id = ?` |
| `existsById(id)` | `SELECT COUNT(*) > 0 ...` |

**Analogia:** É como ter um assistente que já sabe fazer todas as consultas básicas no banco. Você só pede: "me dá todos" ou "salva esse aqui".

### Por que é uma interface e não uma classe?
O Spring cria a implementação em tempo de execução (proxy). Você nunca precisa escrever o código SQL — o framework gera tudo baseado nos nomes dos métodos e na entidade.

---

## 4. ProdutoController.java — Endpoints REST

```java
@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {
    private static final Logger log = LoggerFactory.getLogger(ProdutoController.class);
    private final ProdutoRepository repository;
    private final MinioClient minioClient;
    // ...
}
```

### O que faz?
Expõe a API HTTP. Cada método é um endpoint que o `curl` (ou frontend) chama.

### Por que o Logger?
Spring Boot **não loga requests HTTP automaticamente** em nível INFO. Sem o `log.info(...)` explícito, ferramentas como EFK não capturam atividade de requests. O Logger usa SLF4J e emite em formato JSON quando o profile `json` está ativo.

### Anotações da classe:

| Anotação | Significado |
|----------|-------------|
| `@RestController` | Combina `@Controller` + `@ResponseBody` — retorna JSON direto (não HTML) |
| `@RequestMapping("/api/produtos")` | Prefixo de URL para todos os endpoints desta classe |

### Anotações dos métodos:

| Anotação | Verbo HTTP | Endpoint | Ação |
|----------|-----------|----------|------|
| `@GetMapping` | GET | `/api/produtos` | Listar todos |
| `@GetMapping("/{id}")` | GET | `/api/produtos/1` | Buscar por ID |
| `@PostMapping` | POST | `/api/produtos` | Criar novo |
| `@PutMapping("/{id}")` | PUT | `/api/produtos/1` | Atualizar |
| `@DeleteMapping("/{id}")` | DELETE | `/api/produtos/1` | Deletar |
| `@PostMapping("/{id}/upload")` | POST | `/api/produtos/1/upload` | Upload de arquivo |

### Anotações dos parâmetros:

| Anotação | O que faz |
|----------|-----------|
| `@PathVariable Long id` | Extrai o `{id}` da URL |
| `@RequestBody Produto produto` | Converte o JSON do body para objeto Java |
| `@RequestParam("file") MultipartFile file` | Recebe arquivo enviado via form-data |

### Injeção de dependência (construtor):
```java
public ProdutoController(ProdutoRepository repository, MinioClient minioClient) {
    this.repository = repository;
    this.minioClient = minioClient;
}
```
O Spring injeta automaticamente o `ProdutoRepository` e o `MinioClient` (criado pela `MinioConfig`). Não precisa de `new`.

**Analogia:** O Controller é o balcão de atendimento. Ele recebe o pedido (request), consulta o estoque (repository) ou envia para o depósito (MinIO), e devolve a resposta.

### Correlação com o README:
- **Etapa 2:** Os comandos `curl` chamam exatamente esses endpoints.
- **Etapa 9:** O endpoint `/upload` salva o arquivo no MinIO e grava a URL no banco.

---

## 5. MinioConfig.java — Configuração do Cliente MinIO

```java
@Configuration
public class MinioConfig {
    @Value("${minio.url}")
    private String url;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```

### O que faz?
Cria e configura o cliente MinIO como um bean Spring — disponível para injeção em qualquer lugar.

### Anotações:

| Anotação | Significado |
|----------|-------------|
| `@Configuration` | Marca a classe como fonte de beans (objetos gerenciados pelo Spring) |
| `@Value("${minio.url}")` | Injeta o valor da propriedade do `application.yml` |
| `@Bean` | O método retorna um objeto que o Spring gerencia e injeta onde for pedido |

**Analogia:** É como uma receita de fábrica. O Spring lê a receita (`@Configuration`), pega os ingredientes (`@Value` do application.yml) e produz o produto final (`@Bean MinioClient`) que fica disponível para quem precisar.

### De onde vêm os valores?
Do `application.yml`:
```yaml
minio:
  url: ${MINIO_URL:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
```
A sintaxe `${VAR:default}` significa: use a variável de ambiente `VAR`, ou o valor padrão se não existir.

### Correlação com o README:
- No **docker-compose.yml** (Etapa 2), as variáveis `MINIO_URL`, `MINIO_ACCESS_KEY` etc. são passadas para o container da app.
- Nos **YAMLs do K8s** (Etapa 5) e no **Helm** (Etapa 7), essas mesmas variáveis são definidas nos Secrets/ConfigMaps.

---

## 6. application.yml — Configuração Central

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:crudlab}
    username: ${DB_USER:postgres}
    password: ${DB_PASS:postgres}
  jpa:
    hibernate:
      ddl-auto: update

minio:
  url: ${MINIO_URL:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:produtos}

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

server:
  port: 8080
```

### Seções explicadas:

| Seção | O que configura |
|-------|-----------------|
| `spring.datasource` | Conexão com PostgreSQL (host, porta, banco, user, senha) |
| `spring.jpa.hibernate.ddl-auto: update` | Cria/atualiza tabelas automaticamente baseado nas `@Entity` |
| `minio.*` | URL e credenciais do MinIO (lidos pela `MinioConfig`) |
| `management.endpoints` | Expõe endpoints do Actuator (`/actuator/prometheus`, `/actuator/health`) |
| `server.port` | Porta HTTP da aplicação |

### Por que variáveis de ambiente?
Para que o **mesmo código** rode em qualquer ambiente:
- **Local:** usa os defaults (`localhost`, `postgres`)
- **Docker Compose:** recebe variáveis do `environment:` no compose
- **Kubernetes:** recebe variáveis dos Secrets e ConfigMaps

### Correlação com o README:
- **Etapa 2:** `docker compose up` injeta as variáveis para conectar nos containers vizinhos.
- **Etapa 4:** O endpoint `/actuator/prometheus` é o que o Prometheus coleta para gerar métricas no Grafana.
- **Etapa 5/7:** Os YAMLs e Helm charts definem essas variáveis para o ambiente K8s.

---

## 7. pom.xml — Gerenciamento de Dependências

O `pom.xml` é o "manifesto" do projeto Maven. Define **o que** o projeto precisa para compilar e rodar.

### Parent (herança):
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```
Herda configurações padrão do Spring Boot 3.2.5 (versões de libs, plugins, encoding, etc.).

### Dependências e o que cada uma habilita:

| Dependência | O que traz | Usado por |
|-------------|-----------|-----------|
| `spring-boot-starter-web` | Tomcat embutido, Spring MVC, JSON (Jackson) | `ProdutoController` — endpoints REST |
| `spring-boot-starter-data-jpa` | Hibernate, Spring Data JPA | `ProdutoRepository`, `Produto` — acesso ao banco |
| `spring-boot-starter-actuator` | Endpoints de saúde e métricas (`/actuator/*`) | Prometheus coleta métricas da app |
| `micrometer-registry-prometheus` | Exporta métricas no formato Prometheus | Endpoint `/actuator/prometheus` |
| `postgresql` (runtime) | Driver JDBC do PostgreSQL | Conexão com o banco |
| `minio` | SDK Java do MinIO | `MinioConfig`, upload no Controller |

### Plugin de build:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```
Gera um `.jar` executável (fat jar) com todas as dependências embutidas. É esse `.jar` que vai dentro do container Docker.

### Correlação com o README:
- **Etapa 1:** `mvn clean package` usa o pom.xml para baixar dependências e gerar `target/crud-k8s-lab-1.0.0.jar`.
- **Etapa 3:** O Dockerfile copia esse `.jar` para dentro da imagem Docker.
- **Etapa 4:** As dependências `actuator` + `micrometer-registry-prometheus` são o que permitem o Prometheus/Grafana monitorar a app.

---

## 8. Como Tudo se Conecta (Fluxo Completo)

```
1. Maven lê pom.xml → baixa dependências → gera .jar
2. Spring Boot inicia (Application.java)
3. @ComponentScan encontra:
   ├── MinioConfig → cria MinioClient bean
   ├── ProdutoRepository → Spring Data cria implementação
   └── ProdutoController → registra endpoints REST
4. Hibernate lê @Entity (Produto) → cria/atualiza tabela no PostgreSQL
5. Actuator expõe /actuator/prometheus → Prometheus coleta → Grafana exibe
6. Request chega → Controller → Repository → Banco (ou MinIO para upload)
```

### Mapa: Código ↔ Infraestrutura

| Classe/Config | Depende de (infra) | Configurado onde (K8s) |
|---------------|--------------------|-----------------------|
| `ProdutoRepository` | PostgreSQL | `k8s/01-postgres.yaml` ou Helm |
| `MinioConfig` | MinIO | `k8s/03-minio.yaml` ou Helm |
| `Actuator + Micrometer` | Prometheus | Helm chart `kube-prometheus-stack` |
| `ProdutoController` | Tudo acima | `k8s/02-app.yaml` ou Helm |

---

## Resumo: Anotações Mais Importantes

| Anotação | Categoria | Resumo rápido |
|----------|-----------|---------------|
| `@SpringBootApplication` | Boot | Liga tudo — scan, config, auto-config |
| `@Entity` | JPA | Classe = tabela no banco |
| `@Id` / `@GeneratedValue` | JPA | Chave primária auto-incrementada |
| `@RestController` | Web | Classe que responde HTTP com JSON |
| `@RequestMapping` | Web | Define prefixo de URL |
| `@GetMapping` / `@PostMapping` / etc. | Web | Mapeia verbo HTTP → método Java |
| `@PathVariable` | Web | Extrai valor da URL |
| `@RequestBody` | Web | Converte JSON → objeto Java |
| `@Configuration` | Spring Core | Classe que define beans |
| `@Bean` | Spring Core | Método que produz objeto gerenciado |
| `@Value` | Spring Core | Injeta valor do application.yml |
