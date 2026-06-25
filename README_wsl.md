# CRUD K8s Lab — Windows 11 + WSL

Guia de instalação e execução para **Windows 11 com WSL Ubuntu + K3s**.

> 📖 Visão geral do projeto: [`README.md`](README.md)

---

## Pré-requisitos

Nenhum serviço precisa estar instalado nativamente no Windows. Tudo roda em container via WSL.

```bash
# 1. Docker
sudo apt update && sudo apt install docker.io -y
sudo usermod -aG docker $USER
newgrp docker

# 2. K3s (Kubernetes leve - já inclui containerd e kubectl)
curl -sfL https://get.k3s.io | sh -
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
export KUBECONFIG=~/.kube/config
echo 'export KUBECONFIG=~/.kube/config' >> ~/.bashrc

# 3. Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# 4. Java 17 + Maven (para build local)
sudo apt install openjdk-17-jdk maven -y

# 5. Registry local (para o K3s puxar suas imagens)
docker run -d -p 5000:5000 --restart=always --name registry registry:2
```

### Verificar instalação

```bash
docker --version
kubectl get nodes          # Deve mostrar 1 node "Ready"
helm version
java -version
mvn -version
```

> 💡 O K3s já inclui o Ingress Controller (Traefik). Não precisa instalar separado.

---

## Etapa 1: Entender e buildar a aplicação

> 📖 **Leia:** [`docs/roteiro-springboot.md`](docs/roteiro-springboot.md) e [`docs/roteiro-maven.md`](docs/roteiro-maven.md)

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

> ⚠️ Só ficam acessíveis enquanto `docker compose up` estiver rodando.

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

### Configurar Grafana

1. Acesse `http://localhost:3000` (admin/admin)
2. **Connections > Data Sources > Add data source** → Prometheus
3. URL: `http://prometheus:9090`
4. **Save & Test**
5. **Dashboards > Import** → ID `4701` (JVM Micrometer)

```bash
docker compose down
```

---

## Etapa 3: Build da imagem Docker para Kubernetes

```bash
docker build -t crud-k8s-lab:latest .
docker tag crud-k8s-lab:latest localhost:5000/crud-k8s-lab:latest
docker push localhost:5000/crud-k8s-lab:latest
curl http://localhost:5000/v2/_catalog
```

---

## Etapa 4: Prometheus + Grafana no Kubernetes

> 📖 **Leia:** [`docs/roteiro-observabilidade.md`](docs/roteiro-observabilidade.md)

**Isso precisa ser feito ANTES de deployar a app.**

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  --set grafana.adminPassword=admin \
  --set grafana.service.type=NodePort \
  --set grafana.service.nodePort=30300

kubectl get pods -n monitoring
```

### Acessar Grafana e Prometheus

```bash
# Descobrir IP do WSL
hostname -I | awk '{print $1}'

# Grafana → http://<IP-DO-WSL>:30300 (admin/admin)
# Prometheus → port-forward:
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090 &
```

### Configurar dashboard JVM

1. Acesse Grafana (http://<IP-DO-WSL>:30300)
2. **Dashboards > Browse** (dezenas de dashboards prontos de K8s)
3. **New > Import** → ID `4701` → **Load** → selecione `Prometheus` → **Import**
4. Corrigir datasource via API:
   ```bash
   DASH_UID=$(curl -s -u admin:admin http://<IP-DO-WSL>:30300/api/search | \
     python3 -c "import json,sys;[print(d['uid']) for d in json.load(sys.stdin) if 'JVM' in d.get('title','')]" | head -1)
   curl -s -u admin:admin http://<IP-DO-WSL>:30300/api/dashboards/uid/$DASH_UID | \
     python3 -c "
   import json,sys
   data=json.load(sys.stdin)
   dash=data['dashboard']
   s=json.dumps(dash).replace('\${DS_PROMETHEUS}','prometheus')
   dash=json.loads(s); dash['id']=None
   print(json.dumps({'dashboard':dash,'overwrite':True}))" | \
     curl -s -u admin:admin -X POST -H 'Content-Type: application/json' \
       -d @- http://<IP-DO-WSL>:30300/api/dashboards/db
   ```
5. Selecione **Application** = `crud-k8s-lab`, **Instance** = qualquer IP

> ⚠️ O dashboard JVM só mostra dados após o ServiceMonitor estar ativo (Etapa 7 com Helm cria automaticamente).

---

## Etapa 5: Deploy com YAMLs puros (para aprender K8s)

> 📖 **Leia:** [`docs/roteiro-kubernetes.md`](docs/roteiro-kubernetes.md) e [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md)

```bash
kubectl apply -f k8s/01-postgres.yaml
kubectl apply -f k8s/03-minio.yaml
kubectl apply -f k8s/02-app.yaml

kubectl get all -n crud-lab
kubectl logs -n crud-lab -l app=crud-app -f

# Configurar /etc/hosts para Ingress
# Descobrir IP do WSL:
hostname -I | awk '{print $1}'

# Adicionar ao /etc/hosts (WSL)
sudo sh -c 'echo "<IP-DO-WSL> crud-app.local" >> /etc/hosts'

# No Windows: C:\Windows\System32\drivers\etc\hosts
# Adicionar: <IP-DO-WSL> crud-app.local

# Testar
curl http://crud-app.local/api/produtos

# OU via port-forward
kubectl port-forward -n crud-lab svc/crud-app 8080:80 &
curl http://localhost:8080/api/produtos
```

---

## Etapa 6: Remover deploy manual ANTES de usar Helm

> ⚠️ Etapas 5 e 7 são alternativas. Remova antes de prosseguir:

```bash
kubectl delete -f k8s/02-app.yaml
kubectl delete -f k8s/03-minio.yaml
kubectl delete -f k8s/01-postgres.yaml
```

---

## Etapa 7: Deploy com Helm (forma profissional)

> 📖 **Leia:** [`docs/roteiro-helm.md`](docs/roteiro-helm.md) e [`docs/roteiro-acesso-servicos.md`](docs/roteiro-acesso-servicos.md)

O chart Helm inclui **toda a infraestrutura**:
- App Java (Spring Boot)
- PostgreSQL
- MinIO
- pgAdmin
- ServiceMonitor (para Prometheus)

```bash
helm install crud-lab ./helm/crud-app -n crud-lab --create-namespace
helm list -n crud-lab
kubectl get all -n crud-lab
```

### Configurar hostnames para Ingress

```bash
# Descobrir IP do WSL
hostname -I | awk '{print $1}'

# Adicionar ao /etc/hosts do WSL
sudo sh -c 'cat >> /etc/hosts << EOF
<IP-DO-WSL> crud-app.local
<IP-DO-WSL> pgadmin.local
<IP-DO-WSL> minio.local
EOF'

# No Windows (C:\Windows\System32\drivers\etc\hosts), adicionar:
# <IP-DO-WSL> crud-app.local pgadmin.local minio.local
```

### Acessar serviços via Ingress

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| App (API) | http://crud-app.local/api/produtos | - |
| pgAdmin | http://pgadmin.local | admin@admin.com / admin |
| MinIO Console | http://minio.local | minioadmin / minioadmin |

### Acessar serviços via port-forward (alternativa)

```bash
kubectl port-forward -n crud-lab svc/pgadmin 5050:5050 &
kubectl port-forward -n crud-lab svc/minio 9001:9001 &
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80 &
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090 &
```

> 💡 Port-forward morre ao fechar o terminal. Ingress é permanente.

### Configurar pgAdmin

1. Acesse http://pgadmin.local (ou http://localhost:5050)
2. **Add New Server** → Name: `crud-lab`
3. Host: `postgres`, Port: `5432`, User: `postgres`, Password: `postgres`

### Comandos úteis

```bash
helm upgrade crud-lab ./helm/crud-app -n crud-lab
helm history crud-lab -n crud-lab
helm rollback crud-lab 1 -n crud-lab
helm uninstall crud-lab -n crud-lab
```

---

## Etapa 8: MinIO - Upload de arquivos

> 📖 **Leia:** [`docs/roteiro-minio.md`](docs/roteiro-minio.md)

### Criar o bucket

```bash
kubectl run minio-mc -n crud-lab --image=minio/mc --restart=Never --command -- \
  sh -c "mc alias set myminio http://minio:9000 minioadmin minioadmin && mc mb myminio/produtos"
sleep 10
kubectl logs -n crud-lab minio-mc
kubectl delete pod minio-mc -n crud-lab
```

### Testar upload

```bash
curl -X POST http://crud-app.local/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Manual","descricao":"PDF do produto","preco":0}'

echo "conteudo de teste" > meu-arquivo.pdf

curl -X POST http://crud-app.local/api/produtos/1/upload \
  -F "file=@meu-arquivo.pdf"
```

### Verificar no MinIO Console

1. Acesse http://minio.local (minioadmin / minioadmin)
2. **Object Browser** → bucket **produtos** → `1/` → `meu-arquivo.pdf`

---

## Etapa 9: GUI para Kubernetes (LENS)

> 📖 **Leia:** [`docs/roteiro-lens.md`](docs/roteiro-lens.md)

### Instalar LENS no Windows

1. Baixe em https://k8slens.dev/ e instale
2. Crie conta ou faça login

### Configurar (WSL → Windows)

1. No WSL: `cat ~/.kube/config`
2. No LENS (Windows): **File > Add Cluster** → cole o conteúdo
3. Ajuste o campo `server` para o IP do WSL:
   ```bash
   hostname -I | awk '{print $1}'
   ```
   Troque `https://127.0.0.1:6443` por `https://<IP-DO-WSL>:6443`
4. **Add Cluster** → aguarde conectar

### Navegar pelo cluster

| Seção no LENS | O que mostra | Equivalente kubectl |
|---------------|-------------|-------------------|
| **Workloads > Pods** | Todos os pods, status, restarts | `kubectl get pods -A` |
| **Workloads > Deployments** | Deployments, réplicas | `kubectl get deployments -A` |
| **Network > Services** | Services, portas, IPs | `kubectl get svc -A` |
| **Network > Ingresses** | Rotas de entrada | `kubectl get ingress -A` |
| **Config > Secrets** | Secrets (base64) | `kubectl get secrets -A` |

---

## Etapa 10: EFK — Centralização de Logs

> 📖 **Leia:** [`docs/roteiro-efk.md`](docs/roteiro-efk.md)

### O que é EFK?

```
[App logs → stdout] → [Fluentd coleta] → [Elasticsearch armazena] → [Kibana visualiza]
```

### Passo 1: Pull prévio das imagens pesadas

```bash
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.13.0
docker pull docker.elastic.co/kibana/kibana:8.13.0
```

> 💡 As imagens são ~1GB cada. Baixar via `docker pull` é mais estável que o pull interno do kubelet.

### Passo 2: Rebuild da app

```bash
cd crud-k8s-lab
mvn clean package -DskipTests
docker build -t crud-k8s-lab:latest .
docker tag crud-k8s-lab:latest localhost:5000/crud-k8s-lab:latest
docker push localhost:5000/crud-k8s-lab:latest
```

### Passo 3: Deploy do EFK

```bash
helm upgrade crud-lab ./helm/crud-app -n crud-lab
kubectl get pods -n crud-lab -w
```

### Passo 4: Restart da app

```bash
kubectl rollout restart deployment crud-lab-app -n crud-lab
kubectl rollout status deployment crud-lab-app -n crud-lab --timeout=60s
```

### Passo 5: Recriar Fluentd

```bash
kubectl exec -n crud-lab $(kubectl get pod -n crud-lab -l app=fluentd -o name | head -1) -- rm -f /var/log/fluentd-containers.log.pos
kubectl delete pod -n crud-lab -l app=fluentd
sleep 15
```

### Passo 6: Configurar hostname

```bash
sudo sh -c 'echo "<IP-DO-WSL> kibana.local" >> /etc/hosts'
# No Windows: adicionar <IP-DO-WSL> kibana.local ao hosts
```

### Passo 7: Gerar logs e verificar

```bash
for i in $(seq 1 10); do curl -s http://crud-app.local/api/produtos > /dev/null; done
curl -X POST http://crud-app.local/api/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Teste EFK","descricao":"Log test","preco":10}'

sleep 10
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices
kubectl logs -n crud-lab -l app=fluentd --tail=5
```

### Passo 8: Acessar Kibana

1. Acesse http://kibana.local
2. **Management > Stack Management > Kibana > Data Views**
3. **Create data view**
4. Name: `crud-app-logs`, Index pattern: `crud-app-logs-*`, Timestamp: `@timestamp`
5. **Save data view to Kibana**
6. **Discover** → selecione `crud-app-logs` → logs aparecem em tempo real

### Consultas KQL úteis

```
level: "INFO"
logger_name: "com.lab.crud.controller.ProdutoController"
message: "produto"
level: "ERROR" AND message: "produto"
```

---

## Etapa 11: Rancher — Gerenciamento Kubernetes

> 📖 **Leia:** [`docs/roteiro-rancher.md`](docs/roteiro-rancher.md)

### Instalar cert-manager

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.5/cert-manager.yaml
kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=120s
kubectl get pods -n cert-manager
```

### Instalar Rancher

```bash
helm repo add rancher-stable https://releases.rancher.com/server-charts/stable
helm repo update

helm install rancher rancher-stable/rancher \
  -n cattle-system --create-namespace \
  --set hostname=rancher.local \
  --set bootstrapPassword=admin \
  --set ingress.ingressClassName=traefik \
  --set replicas=1

kubectl get pods -n cattle-system -w
```

> 💡 No WSL com K3s, o ingressClassName é `traefik` (diferente do Docker Desktop Mac que usa `nginx`).

### Configurar hostname

```bash
sudo sh -c 'echo "<IP-DO-WSL> rancher.local" >> /etc/hosts'
# No Windows: adicionar <IP-DO-WSL> rancher.local ao hosts
```

### Primeiro acesso

1. Acesse https://rancher.local (aceite o certificado autoassinado)
2. Password de bootstrap: `admin`
3. Defina nova senha
4. Aceite a URL do server

### Explorar o Rancher

| Seção | O que fazer |
|-------|-------------|
| **Cluster Management** | Ver cluster `local` |
| **Cluster Explorer** | Visão completa (substitui LENS) |
| **Workloads** | Ver pods, deployments |
| **Apps & Marketplace** | Instalar Helm charts pelo browser |
| **Users & Authentication** | Criar usuários, definir permissões (RBAC) |

> 📖 **Próximo passo:** Para fazer o deploy de toda a stack pela GUI do Rancher (sem terminal), veja [`docs/roteiro-deploy-via-rancher.md`](docs/roteiro-deploy-via-rancher.md)

---

## Troubleshooting

<details>
<summary>🔧 ImagePullBackOff no Elasticsearch/Kibana</summary>

```bash
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.13.0
docker pull docker.elastic.co/kibana/kibana:8.13.0
kubectl delete pod -n crud-lab -l app=elasticsearch
kubectl delete pod -n crud-lab -l app=kibana
```
</details>

<details>
<summary>🔧 Fluentd "pattern not matched"</summary>

O K3s usa containerd (formato CRI). O chart já usa o parser `regexp` correto. Se ver esse erro:

```bash
kubectl exec -n crud-lab <fluentd-pod> -- head -3 /var/log/containers/*crud-lab-app*.log
helm upgrade crud-lab ./helm/crud-app -n crud-lab
kubectl delete pod -n crud-lab -l app=fluentd
```
</details>

<details>
<summary>🔧 Fluentd "400 - Rejected by Elasticsearch"</summary>

Índice com mapeamento corrompido:

```bash
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s -X DELETE "http://localhost:9200/crud-app-logs-YYYY.MM.DD"
kubectl exec -n crud-lab $(kubectl get pod -n crud-lab -l app=fluentd -o name | head -1) -- rm -f /var/log/fluentd-containers.log.pos
kubectl delete pod -n crud-lab -l app=fluentd
sleep 15
for i in $(seq 1 5); do curl -s http://crud-app.local/api/produtos > /dev/null; done
kubectl exec -n crud-lab deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices
```
</details>

<details>
<summary>🔧 Kibana "Ready to try Kibana? First, you need data"</summary>

Elasticsearch não recebeu dados. Verifique:
1. A app tem `log.info(...)` nos endpoints
2. Fluentd está rodando: `kubectl logs -n crud-lab -l app=fluentd --tail=5`
3. Gere tráfego e aguarde 10s
4. Confirme índice: `kubectl exec -n crud-lab deploy/elasticsearch -- curl -s http://localhost:9200/_cat/indices`
</details>

<details>
<summary>🔧 Rancher não abre (connection refused)</summary>

Pods ainda subindo: `kubectl get pods -n cattle-system -w`
Verificar logs: `kubectl logs -n cattle-system -l app=rancher --tail=10`
</details>

<details>
<summary>🔧 Helm upgrade com conflito "spec.replicas"</summary>

O LENS ou outro cliente escalou manualmente o deployment:

```bash
kubectl delete deployment crud-lab-app -n crud-lab
helm upgrade crud-lab ./helm/crud-app -n crud-lab
```
</details>

---

## Resumo dos serviços

| Serviço | URL | Credenciais | Protocolo |
|---------|-----|-------------|-----------|
| App (API) | http://crud-app.local/api/produtos | - | HTTP |
| pgAdmin | http://pgadmin.local | admin@admin.com / admin | HTTP |
| MinIO Console | http://minio.local | minioadmin / minioadmin | HTTP |
| Kibana (logs) | http://kibana.local | - | HTTP |
| Grafana (métricas) | http://<IP-DO-WSL>:30300 | admin / admin | HTTP |
| Rancher | https://rancher.local | admin / (sua senha) | HTTPS |
