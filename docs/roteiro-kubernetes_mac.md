# Roteiro de Estudo: Kubernetes Essencial (Mac)

> Este roteiro é idêntico ao [`roteiro-kubernetes.md`](roteiro-kubernetes.md) exceto pelas seções que diferem no Mac.
> **Leia o roteiro original primeiro** — aqui estão apenas os complementos e diferenças para Docker Desktop no macOS.

---

## Diferença principal: Ingress Controller

No Docker Desktop, **não vem** Ingress Controller. Instale o NGINX Ingress:

```bash
# Instalar NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml

# Verificar que está rodando
kubectl get pods -n ingress-nginx
```

Após isso, o Ingress do `02-app.yaml` funciona normalmente.

---

## Diferença: /etc/hosts

No Mac, edite diretamente:

```bash
sudo sh -c 'echo "127.0.0.1 crud-app.local" >> /etc/hosts'
```

Depois `curl http://crud-app.local/api/produtos` funciona.

---

## Diferença: port-forward

`localhost` funciona diretamente (sem descobrir IP):

```bash
kubectl port-forward -n crud-lab svc/crud-app 8080:80
# Acesse: http://localhost:8080/api/produtos
```

---

## Diferença: NodePort

No Docker Desktop, NodePort é acessível via `localhost:<nodePort>`:

```yaml
spec:
  type: NodePort
  ports:
    - port: 80
      targetPort: 8080
      nodePort: 30080
```

Acesse: `http://localhost:30080` (sem precisar de IP do node).

---

## Diferença: decodificar base64

No Mac, o comando `base64` tem sintaxe ligeiramente diferente:

```bash
# Linux
echo "cG9zdGdyZXM=" | base64 -d

# macOS
echo "cG9zdGdyZXM=" | base64 -D
# OU (se tiver coreutils via brew)
echo "cG9zdGdyZXM=" | base64 --decode
```

---

## Alternativa ao Ingress: usar port-forward direto

Se não quiser instalar Ingress Controller, port-forward resolve para estudo:

```bash
# App
kubectl port-forward -n crud-lab svc/crud-app 8080:80 &

# MinIO Console
kubectl port-forward -n crud-lab svc/minio 9001:9001 &

# Grafana (se instalado via Helm na etapa 4)
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80 &
```

---

## Todo o restante é igual

Todos os conceitos (Namespace, Pod, Deployment, Service, Secret, ConfigMap, Labels) funcionam de forma idêntica. O Kubernetes é o mesmo — muda apenas como você acessa de fora do cluster.
