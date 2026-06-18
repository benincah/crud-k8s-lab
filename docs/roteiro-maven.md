# Roteiro de Estudo: Maven

> 📖 Este documento complementa a **Etapa 1** do [README.md](../README.md) — onde você faz `mvn clean package` para gerar o `.jar`.
> Aqui você vai entender **o que é o Maven**, como ele funciona e o que cada parte do `pom.xml` significa.

---

## 1. O que é Maven?

**Analogia:** Maven é o "gerente de obra" do seu projeto Java. Ele sabe:
- Quais materiais (dependências/bibliotecas) buscar
- Onde buscar (repositórios remotos como Maven Central)
- Em que ordem construir (lifecycle)
- Como empacotar o resultado final (.jar)

**Sem Maven:** Você baixaria manualmente cada `.jar` de cada biblioteca, colocaria no classpath, compilaria na mão e rezaria para não esquecer nada.

**Com Maven:** `mvn clean package` → ele baixa tudo, compila, testa e empacota automaticamente.

---

## 2. Conceitos Fundamentais

### Coordenadas Maven (GAV)

Cada artefato no mundo Maven é identificado por 3 coordenadas:

| Coordenada | Significado | Exemplo no projeto |
|------------|-------------|--------------------|
| `groupId` | Organização/empresa (padrão de pacote Java invertido) | `com.lab` |
| `artifactId` | Nome do projeto/módulo | `crud-k8s-lab` |
| `version` | Versão do artefato | `1.0.0` |

**Analogia:** É o "CPF" de uma biblioteca. Com essas 3 informações, o Maven encontra qualquer dependência no mundo.

### Repositório

| Tipo | O que é | Analogia |
|------|---------|----------|
| **Local** (`~/.m2/repository`) | Cache no seu PC | Despensa de casa |
| **Central** (repo.maven.apache.org) | Repositório público global | Supermercado |
| **Remoto/privado** | Repo da empresa (Nexus, Artifactory) | Fornecedor exclusivo |

Fluxo: Maven precisa de uma dependência → procura no local → se não tem, baixa do Central → armazena no local para próximas vezes.

### Lifecycle (Ciclo de Vida)

Maven executa fases em sequência. Ao chamar uma fase, todas as anteriores executam:

```
validate → compile → test → package → verify → install → deploy
```

| Comando | O que executa | Resultado |
|---------|--------------|-----------|
| `mvn compile` | validate + compile | Classes compiladas em `target/classes` |
| `mvn test` | validate + compile + test | Roda testes unitários |
| `mvn package` | validate + compile + test + package | Gera o `.jar` em `target/` |
| `mvn install` | ... + install | Coloca o `.jar` no repositório local (`~/.m2`) |
| `mvn clean` | Remove a pasta `target/` | Limpa builds anteriores |

**No projeto:** `mvn clean package -DskipTests` = limpa tudo + compila + empacota (pula testes para ser mais rápido).

---

## 3. Estrutura Padrão de Diretórios

Maven segue **convenção sobre configuração** — se você coloca os arquivos nos lugares certos, ele sabe o que fazer sem precisar configurar nada:

```
crud-k8s-lab/
├── pom.xml                          # Manifesto do projeto
├── src/
│   ├── main/
│   │   ├── java/                    # Código fonte
│   │   │   └── com/lab/crud/
│   │   │       ├── Application.java
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── model/
│   │   │       └── repository/
│   │   └── resources/               # Configs e arquivos estáticos
│   │       ├── application.yml
│   │       └── logback-spring.xml
│   └── test/
│       └── java/                    # Testes (mesma estrutura de pacotes)
└── target/                          # Gerado pelo Maven (NÃO commita!)
    ├── classes/                     # Classes compiladas
    └── crud-k8s-lab-1.0.0.jar      # Artefato final
```

| Diretório | Propósito |
|-----------|-----------|
| `src/main/java` | Código da aplicação |
| `src/main/resources` | Configurações (application.yml, templates, etc.) |
| `src/test/java` | Testes unitários e de integração |
| `target/` | Saída do build (gerado automaticamente, não versionar) |

---

## 4. O pom.xml Detalhado

O `pom.xml` (Project Object Model) é o coração do Maven. Vamos analisar cada seção do nosso projeto:

### 4.1 Cabeçalho e Coordenadas

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
```

| Elemento | Significado |
|----------|-------------|
| `xmlns` / `xsi:schemaLocation` | Define o schema XML — garante que o IDE valide a estrutura |
| `modelVersion` | Versão do modelo POM (sempre `4.0.0` desde Maven 2) |

### 4.2 Parent (Herança)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```

**O que faz:** Herda configurações prontas do Spring Boot. É como "copiar a base de um template".

**O que o parent traz:**

| Configuração herdada | Benefício |
|---------------------|-----------|
| Versões das dependências Spring | Não precisa declarar `<version>` em cada starter |
| Encoding UTF-8 | Evita problemas com acentos |
| Java version default | Configura compilador |
| Plugin configurations | spring-boot-maven-plugin pré-configurado |
| Dependency management | Versões compatíveis entre si (evita conflitos) |

**Analogia:** É como herdar o código genético de uma família. O Spring Boot parent já resolve versões, plugins e configurações padrão. Seu pom.xml só precisa declarar **o que é específico** do seu projeto.

**Por que `3.2.5` e não `<version>` em cada dependência?** O parent define um BOM (Bill of Materials) que diz: "use Spring Web 6.1.6 com Hibernate 6.4.4 com Jackson 2.17.0..." — todas versões testadas juntas. Isso previne o "inferno de dependências".

### 4.3 Identidade do Projeto

```xml
<groupId>com.lab</groupId>
<artifactId>crud-k8s-lab</artifactId>
<version>1.0.0</version>
<name>crud-k8s-lab</name>
```

| Elemento | Valor | Significado |
|----------|-------|-------------|
| `groupId` | `com.lab` | Identifica a organização |
| `artifactId` | `crud-k8s-lab` | Nome do artefato gerado |
| `version` | `1.0.0` | Versão atual |
| `name` | `crud-k8s-lab` | Nome legível (usado em relatórios) |

O `.jar` gerado terá o nome: `crud-k8s-lab-1.0.0.jar` (artifactId + version).

### 4.4 Properties

```xml
<properties>
    <java.version>17</java.version>
</properties>
```

Define variáveis reutilizáveis. O parent do Spring Boot lê `java.version` para configurar o compilador (`maven-compiler-plugin` com `source` e `target` = 17).

Outras properties comuns:

| Property | Uso |
|----------|-----|
| `java.version` | Versão do Java para compilação |
| `project.build.sourceEncoding` | Encoding dos fontes (herdado: UTF-8) |
| `minha.variavel` | Qualquer custom, referenciada com `${minha.variavel}` |

### 4.5 Dependencies (Dependências)

Cada `<dependency>` declara uma biblioteca que o projeto precisa:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    ...
</dependencies>
```

#### Tabela completa das dependências do projeto:

| Dependência | O que traz | Por que usamos | Versão |
|-------------|-----------|----------------|--------|
| `spring-boot-starter-web` | Tomcat embutido, Spring MVC, Jackson (JSON) | Criar endpoints REST (Controller) | Herdada do parent |
| `spring-boot-starter-data-jpa` | Hibernate, Spring Data JPA, HikariCP (pool de conexões) | Acesso ao banco sem escrever SQL | Herdada do parent |
| `spring-boot-starter-actuator` | Endpoints de saúde e métricas (`/actuator/*`) | Monitoramento por Prometheus | Herdada do parent |
| `micrometer-registry-prometheus` | Exporta métricas no formato Prometheus | Endpoint `/actuator/prometheus` | Herdada do parent |
| `postgresql` | Driver JDBC do PostgreSQL | Conectar ao banco | Herdada do parent |
| `minio` | SDK Java do MinIO | Upload de arquivos | `8.5.9` (explícita) |
| `logstash-logback-encoder` | Logs em formato JSON estruturado | Coleta pelo Fluentd/EFK | `7.4` (explícita) |

#### Quando precisa declarar `<version>` e quando não?

| Cenário | Exemplo | Por quê |
|---------|---------|---------|
| **Sem version** | `spring-boot-starter-web` | O parent já define via BOM |
| **Com version** | `minio` (8.5.9) | Não faz parte do ecossistema Spring Boot |

#### Scope (Escopo) de dependências

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

| Scope | Quando está disponível | Exemplo |
|-------|----------------------|---------|
| `compile` (default) | Compilação + execução + testes | `spring-boot-starter-web` |
| `runtime` | Execução + testes (não compila contra) | `postgresql` (só precisa em runtime) |
| `test` | Apenas nos testes | `spring-boot-starter-test` (JUnit) |
| `provided` | Compilação, mas NÃO vai no .jar final | Servlet API (container fornece) |

**Analogia:**
- `compile` = ingrediente que aparece na receita E no prato final
- `runtime` = gás de cozinha (precisa para funcionar, mas não aparece no prato)
- `test` = provador de comida (só participa durante a preparação)
- `provided` = fogão do restaurante (está lá, mas você não leva pra casa)

### 4.6 Build (Plugins)

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

**O que faz:** Transforma o `.jar` comum em um **fat jar** (uber jar) executável:

| Sem o plugin | Com o plugin |
|-------------|-------------|
| `.jar` com só suas classes (~50KB) | `.jar` com todas as dependências embutidas (~50MB) |
| Precisa de classpath externo | `java -jar app.jar` roda direto |
| Não tem `MANIFEST.MF` com Main-Class | Tem tudo configurado |

**Analogia:** Sem o plugin, você entrega a planta da casa. Com o plugin, você entrega a casa pronta com móveis dentro.

**Por que é essencial:** O Dockerfile faz `java -jar app.jar` — isso só funciona porque o plugin empacotou tudo dentro do `.jar`.

---

## 5. Como Maven Resolve Dependências

```
Seu pom.xml declara:
  └── spring-boot-starter-web
        ├── spring-web
        ├── spring-webmvc
        ├── spring-boot-starter-tomcat
        │     └── tomcat-embed-core
        ├── jackson-databind
        │     ├── jackson-core
        │     └── jackson-annotations
        └── ...
```

Cada dependência pode ter suas próprias dependências (**transitivas**). Maven resolve a árvore completa automaticamente.

### Comando útil: ver árvore de dependências

```bash
# Mostra toda a árvore (transitivas incluídas)
mvn dependency:tree

# Exemplo de saída:
# com.lab:crud-k8s-lab:jar:1.0.0
# ├── org.springframework.boot:spring-boot-starter-web:jar:3.2.5
# │   ├── org.springframework.boot:spring-boot-starter-tomcat:jar:3.2.5
# │   │   └── org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.20
# │   └── org.springframework:spring-webmvc:jar:6.1.6
# └── io.minio:minio:jar:8.5.9
#     ├── com.squareup.okhttp3:okhttp:jar:4.12.0
#     └── ...
```

---

## 6. Comandos Maven Essenciais

```bash
# Limpar + compilar + gerar .jar (pula testes)
mvn clean package -DskipTests

# Apenas compilar (sem empacotar)
mvn compile

# Rodar testes
mvn test

# Ver árvore de dependências
mvn dependency:tree

# Baixar dependências sem compilar
mvn dependency:resolve

# Forçar atualização de dependências (re-download)
mvn clean install -U

# Ver versão efetiva do pom (com herança resolvida)
mvn help:effective-pom
```

### Flags úteis:

| Flag | O que faz |
|------|-----------|
| `-DskipTests` | Não roda testes (ainda compila as classes de teste) |
| `-Dmaven.test.skip=true` | Nem compila nem roda testes |
| `-U` | Força atualização de snapshots/dependências |
| `-X` | Modo debug (verboso) |
| `-o` | Modo offline (usa só cache local) |
| `-pl módulo` | Build apenas de um módulo específico |

---

## 7. O Repositório Local (~/.m2)

Quando Maven baixa uma dependência, ela fica em `~/.m2/repository/` seguindo a estrutura GAV:

```
~/.m2/repository/
├── org/springframework/boot/
│   └── spring-boot-starter-web/
│       └── 3.2.5/
│           ├── spring-boot-starter-web-3.2.5.jar
│           └── spring-boot-starter-web-3.2.5.pom
├── io/minio/
│   └── minio/
│       └── 8.5.9/
│           └── minio-8.5.9.jar
└── ...
```

**Dica:** Se algo der errado com uma dependência corrupta, delete a pasta dela em `~/.m2/repository/` e rode `mvn clean install` novamente.

---

## 8. Maven Wrapper (mvnw)

Projetos profissionais incluem o **Maven Wrapper** — um script que garante que todos usam a mesma versão do Maven:

```bash
# Usar wrapper em vez de mvn (não precisa ter Maven instalado)
./mvnw clean package

# Gerar wrapper no projeto (se não existir)
mvn wrapper:wrapper
```

| Arquivo | Propósito |
|---------|-----------|
| `mvnw` | Script para Linux/Mac |
| `mvnw.cmd` | Script para Windows |
| `.mvn/wrapper/maven-wrapper.properties` | Define versão do Maven a usar |

**Analogia:** É como incluir o instalador do Maven junto com o projeto. Qualquer dev que clonar o repo pode buildar sem instalar Maven antes.

---

## 9. Correlação com o Projeto (como Maven se encaixa)

| Etapa do README | Como Maven participa |
|-----------------|---------------------|
| **Etapa 1** (build local) | `mvn clean package -DskipTests` gera o `.jar` |
| **Etapa 2** (Docker Compose) | O Dockerfile roda `mvn package` no stage de build |
| **Etapa 3** (imagem Docker) | O `.jar` gerado pelo Maven vai para dentro da imagem |

### Fluxo completo:

```
pom.xml (define dependências)
    │
    ▼
mvn clean package
    │
    ├── Baixa dependências (Maven Central → ~/.m2)
    ├── Compila src/main/java → target/classes
    ├── Processa src/main/resources → target/classes
    ├── (Roda testes se não tiver -DskipTests)
    └── spring-boot-maven-plugin empacota tudo
            │
            ▼
    target/crud-k8s-lab-1.0.0.jar (fat jar executável)
            │
            ▼
    Dockerfile COPY → imagem Docker → Kubernetes
```

---

## 10. Problemas Comuns e Soluções

| Problema | Causa provável | Solução |
|----------|---------------|---------|
| `Could not resolve dependencies` | Sem internet ou repo indisponível | `mvn clean install -U` |
| `Java version mismatch` | JDK instalado ≠ `java.version` no pom | Instalar JDK correto |
| `Tests failed` | Teste quebrado | `mvn package -DskipTests` (ou corrigir o teste) |
| Build muito lento | Primeira vez baixando tudo | Normal — segunda vez usa cache |
| `.jar` não executa | Falta spring-boot-maven-plugin | Adicionar o plugin no `<build>` |
| `OutOfMemoryError` no build | Projeto grande | `export MAVEN_OPTS="-Xmx1024m"` |

---

## Resumo Visual

```
┌─────────────────────────────────────────────────────────────┐
│                         pom.xml                              │
├─────────────────────────────────────────────────────────────┤
│  <parent>         → Herda configs do Spring Boot            │
│  <groupId>        → Quem é você (organização)               │
│  <artifactId>     → Nome do projeto                         │
│  <version>        → Versão atual                            │
│  <properties>     → Variáveis (java.version, etc.)          │
│  <dependencies>   → O que o projeto precisa para funcionar  │
│  <build><plugins> → Como empacotar (fat jar)                │
└─────────────────────────────────────────────────────────────┘
         │
         ▼  mvn clean package
┌─────────────────────┐
│  target/app-1.0.jar │  ← Pronto para Docker/K8s
└─────────────────────┘
```
