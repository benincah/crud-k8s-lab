# 🚦 Roteiro de Estudo: Acessando Serviços no ☸️ Kubernetes

## O problema

Quando você roda serviços no Kubernetes, eles ficam **isolados dentro do cluster**. Diferente do Docker Compose (onde `-p 8080:8080` já expõe a porta no seu Mac), no K8s você precisa explicitamente criar um caminho de acesso externo.

Existem duas formas principais:

| Método | Complexidade | Persistente? | Uso |
|--------|-------------|-------------|-----|
| **Port-forward** | Simples | ❌ Morre ao fechar o terminal | Debug, acesso rápido |
| **Ingress** | Moderada | ✅ Permanente enquanto o recurso existir | Acesso estável via hostname |

---

## 1. Port-forward — Túnel temporário

### O que é?

O `kubectl port-forward` cria um **túnel** entre uma porta do seu Mac e um Service/Pod dentro do cluster. É como furar um buraco temporário na parede do cluster para espiar o que tem dentro.

### Como funciona?

```
[Seu browser] → localhost:5050 → [túnel kubectl] → svc/pgadmin:5050 → [Pod pgadmin:80]
```

### Sintaxe

```bash
kubectl port-forward -n <namespace> svc/<service-name> <porta-local>:<porta-service>
```

### Exemplos práticos

```bash
# pgAdmin — acessa em http://localhost:5050
kubectl port-forward -n crud-lab svc/pgadmin 5050:5050

# MinIO Console — acessa em http://localhost:9001
kubectl port-forward -n crud-lab svc/minio 9001:9001

# Grafana — acessa em http://localhost:3000
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80

# Prometheus — acessa em http://localhost:9090
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090
```

### Rodar em background

Cada port-forward ocupa o terminal. Para rodar em background:

```bash
# Adicione & no final
kubectl port-forward -n crud-lab svc/pgadmin 5050:5050 &

# Ver port-forwards rodando em background
jobs

# Matar um port-forward específico
kill %1    # Onde 1 é o número do job

# Matar todos os port-forwards
kill $(jobs -p)
```

### Vantagens

- Zero configuração — funciona imediatamente
- Não precisa alterar nenhum YAML
- Bom para debug rápido

### Desvantagens

- **Morre** ao fechar o terminal (ou se a conexão cair)
- Precisa reexecutar toda vez que reiniciar o Mac/Docker
- Um comando por serviço — fica verboso com muitos serviços
- Não funciona se esquecer de rodar

---

## 2. Ingress — Acesso permanente via hostname

### O que é?

O Ingress é um **roteador HTTP permanente** que roda dentro do cluster. Ele recebe requisições de fora e direciona para o Service correto baseado no hostname.

### Como funciona?

```
[Seu browser] → http://pgadmin.local → [Ingress Controller] → svc/pgadmin → [Pod]
                http://minio.local   → [Ingress Controller] → svc/minio   → [Pod]
                http://crud-app.local → [Ingress Controller] → svc/crud-app → [Pod]
```

### Por que é mais elegante?

- **Permanente** — não precisa reexecutar nada ao reiniciar
- **Um ponto de entrada** — todos os serviços acessíveis por hostname
- **Simula produção** — em ambiente real, é assim que funciona
- **Sem portas para memorizar** — usa nomes legíveis

### Pré-requisitos

1. **Ingress Controller instalado** (já fizemos nos pré-requisitos):
```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/cloud/deploy.yaml
```

2. **Hostnames no /etc/hosts** (mapeia nomes para localhost):
```bash
sudo sh -c 'cat >> /etc/hosts << EOF
127.0.0.1 crud-app.local
127.0.0.1 pgadmin.local
127.0.0.1 minio.local
EOF'
```

### Como configurar

Cada serviço que você quer acessar via browser precisa de um recurso Ingress:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: pgadmin
  namespace: crud-lab
spec:
  ingressClassName: nginx
  rules:
    - host: pgadmin.local        # Hostname que você digita no browser
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: pgadmin    # Service de destino
                port:
                  number: 5050   # Porta do Service
```

### Resultado final

| Serviço | URL (Ingress) | Credenciais |
|---------|---------------|-------------|
| App (API) | http://crud-app.local/api/produtos | - |
| pgAdmin | http://pgadmin.local | admin@admin.com / admin |
| MinIO Console | http://minio.local | minioadmin / minioadmin |

> 💡 Grafana e Prometheus ficam em outro namespace (`monitoring`), então precisam de Ingress separado ou continuam via port-forward.

### Vantagens

- Permanente — sobrevive a reinicializações
- Nenhum comando extra após o deploy
- Simula ambiente de produção
- URLs legíveis e fáceis de lembrar

### Desvantagens

- Precisa configurar `/etc/hosts` para cada hostname
- Precisa de Ingress Controller instalado
- Um pouco mais de YAML para configurar

---

## 3. Comparação lado a lado

| Aspecto | Port-forward | Ingress |
|---------|-------------|---------|
| Configuração | Nenhuma | Criar recurso Ingress + /etc/hosts |
| Persistência | ❌ Morre com o terminal | ✅ Permanente |
| Acesso | `localhost:<porta>` | `http://nome.local` |
| Múltiplos serviços | Um comando por serviço | Um Ingress com múltiplas regras |
| Produção | ❌ Nunca usado | ✅ Padrão da indústria |
| Debug rápido | ✅ Ideal | Overkill |

---

## 4. Recomendação para este projeto

Use **ambos** — cada um tem seu lugar:

- **Ingress** para serviços que você acessa frequentemente (app, pgAdmin, MinIO)
- **Port-forward** para acessos pontuais (Grafana, Prometheus — que ficam no namespace `monitoring`)

---

## 5. Troubleshooting

### Ingress retorna 404
→ O Ingress resource não tem `ingressClassName: nginx`. Adicione ao spec.

### Port-forward morre sozinho
→ Normal se o pod reiniciar. Reexecute o comando.

### "Connection refused" no browser
→ O port-forward pode ter morrido. Verifique com `jobs` ou reexecute.

### Hostname não resolve
→ Verifique se está no `/etc/hosts`: `grep pgadmin /etc/hosts`
