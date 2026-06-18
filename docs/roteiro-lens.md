# Roteiro de Estudo: LENS — GUI para Kubernetes

## 1. Por que usar uma GUI para Kubernetes?

**Analogia:** `kubectl` é como dirigir olhando apenas o velocímetro. LENS é como ter um painel completo com GPS, câmera de ré e alerta de colisão — tudo ao mesmo tempo.

### O problema com kubectl puro

```bash
# Para ter visão geral, você precisa executar vários comandos:
kubectl get pods -n crud-lab
kubectl get svc -n crud-lab
kubectl get ingress -n crud-lab
kubectl get deployments -n crud-lab
kubectl logs -n crud-lab -l app=crud-lab-app
kubectl get events -n crud-lab --sort-by='.lastTimestamp'
```

Com uma GUI, tudo isso aparece **em uma tela**, atualizado em tempo real.

### Quando usar kubectl vs GUI

| Situação | Ferramenta |
|----------|-----------|
| Automatizar tarefas (scripts, CI/CD) | `kubectl` |
| Visão geral do cluster | GUI (LENS) |
| Troubleshooting (logs + eventos + status) | GUI (LENS) |
| Aplicar YAMLs | `kubectl apply` |
| Escalar rapidamente | GUI (1 clique) |
| Ensinar alguém sobre K8s | GUI (visual) |

---

## 2. O que é LENS?

LENS é um IDE para Kubernetes — uma aplicação desktop que se conecta ao seu cluster e mostra tudo visualmente.

- Gratuito para uso pessoal
- Funciona com qualquer cluster K8s (Docker Desktop, K3s, EKS, GKE, etc.)
- Suporta múltiplos clusters simultâneos
- Disponível para macOS, Windows e Linux

---

## 3. Instalação

### macOS

```bash
brew install --cask lens
```

### Windows

1. Baixe em https://k8slens.dev/
2. Execute o instalador
3. Crie uma conta gratuita (ou faça login)

---

## 4. Configuração

### macOS (Docker Desktop)

O LENS detecta automaticamente o kubeconfig em `~/.kube/config`. Basta abrir e selecionar o cluster `docker-desktop`.

### Windows + WSL (K3s)

O cluster roda no WSL, mas o LENS roda no Windows. Precisa "exportar" o kubeconfig:

1. No WSL, exiba o kubeconfig:
   ```bash
   cat ~/.kube/config
   ```

2. No LENS (Windows): **File > Add Cluster** → cole o conteúdo

3. **Ajuste o IP do server** — o kubeconfig aponta para `127.0.0.1`, mas o Windows precisa acessar pelo IP do WSL:
   ```bash
   # No WSL, descubra o IP
   hostname -I | awk '{print $1}'
   # Ex: 172.28.160.1
   ```
   No LENS, edite o kubeconfig colado e troque:
   ```
   server: https://127.0.0.1:6443
   ```
   Por:
   ```
   server: https://172.28.160.1:6443
   ```

4. Clique **Add Cluster** → aguarde conectar

---

## 5. Navegação — O que cada seção mostra

### Workloads

| Seção | Mostra | Para que serve |
|-------|--------|---------------|
| **Pods** | Containers rodando | Ver status, restarts, idade |
| **Deployments** | Gerenciadores de pods | Ver réplicas desejadas vs atuais |
| **DaemonSets** | Pods que rodam em cada node | Ex: node-exporter (métricas) |
| **StatefulSets** | Pods com identidade fixa | Ex: Prometheus, bancos de dados |
| **Jobs** | Tarefas de execução única | Ex: pod temporário do mc (MinIO) |

### Network

| Seção | Mostra | Para que serve |
|-------|--------|---------------|
| **Services** | Endpoints internos | Ver portas, tipo (ClusterIP/NodePort) |
| **Ingresses** | Rotas externas | Ver hostnames configurados |

### Config

| Seção | Mostra | Para que serve |
|-------|--------|---------------|
| **Secrets** | Dados sensíveis (base64) | Ver senhas, tokens |
| **ConfigMaps** | Configurações não-sensíveis | Ver variáveis de ambiente |

### Custom Resources

| Seção | Mostra | Para que serve |
|-------|--------|---------------|
| **ServiceMonitor** | Configurações de scrape | Ver o que o Prometheus monitora |

---

## 6. Tarefas comuns

### Ver logs de um pod

1. **Workloads > Pods** → selecione namespace `crud-lab`
2. Clique no pod desejado
3. Clique no ícone de **logs** (📄) no painel lateral
4. Os logs aparecem em tempo real (equivale a `kubectl logs -f`)

### Escalar um Deployment

1. **Workloads > Deployments** → `crud-lab-app`
2. Clique no deployment
3. No painel, clique no ícone de **escalar** ou edite o YAML
4. Altere `replicas` para o valor desejado
5. Equivale a: `kubectl scale deployment crud-lab-app --replicas=3 -n crud-lab`

### Abrir terminal dentro de um pod

1. **Workloads > Pods** → selecione o pod
2. Clique no ícone de **terminal** (>_)
3. Equivale a: `kubectl exec -it <pod> -n crud-lab -- /bin/sh`

### Editar um recurso (YAML)

1. Clique em qualquer recurso (Pod, Service, Deployment...)
2. Clique no ícone de **editar** (✏️)
3. O YAML aparece para edição direta
4. Salve → aplicado imediatamente ao cluster

### Ver eventos (troubleshooting)

1. Selecione o namespace `crud-lab` no seletor do topo
2. Vá em **Events**
3. Eventos recentes mostram:
   - Pods criados/destruídos
   - Erros de pull de imagem
   - Falhas de probes (liveness/readiness)
   - Restarts

---

## 7. Diagnóstico visual rápido

O LENS usa cores para indicar status:

| Cor | Significado |
|-----|-------------|
| 🟢 Verde | Saudável (Running, Ready) |
| 🟡 Amarelo | Em progresso (Pending, Creating) |
| 🔴 Vermelho | Erro (CrashLoopBackOff, Failed, ImagePullBackOff) |

Ao ver um pod vermelho:
1. Clique nele
2. Veja a aba **Events** — mostra o que deu errado
3. Veja os **Logs** — mostra o erro da aplicação

---

## 8. Alternativa terminal: k9s

Se preferir uma interface no terminal (sem sair do shell):

```bash
# Instalar
brew install k9s        # Mac
sudo snap install k9s   # Linux

# Executar
k9s
```

### Comandos básicos do k9s:

| Tecla | Ação |
|-------|------|
| `:pods` | Navegar para pods |
| `:svc` | Navegar para services |
| `:deploy` | Navegar para deployments |
| `:ns` | Trocar namespace |
| `l` | Ver logs do pod selecionado |
| `s` | Abrir shell no pod |
| `d` | Describe (detalhes) |
| `ctrl+d` | Deletar recurso |
| `/` | Filtrar/buscar |
| `q` | Sair |

---

## 9. LENS vs kubectl vs k9s — Comparação

| Aspecto | kubectl | k9s | LENS |
|---------|---------|-----|------|
| Interface | Texto (CLI) | Terminal interativo | GUI desktop |
| Curva de aprendizado | Alta | Média | Baixa |
| Visão geral | ❌ (um comando por vez) | ✅ (tela cheia) | ✅ (múltiplos painéis) |
| Automação/Scripts | ✅ | ❌ | ❌ |
| Múltiplos clusters | Manual (troca contexto) | Sim | Sim (abas) |
| Logs em tempo real | `kubectl logs -f` | `l` no pod | Clique |
| Edição de YAML | `kubectl edit` | Sim | Sim (visual) |
| Peso | Zero | Leve | Pesado (~500MB) |

---

## Ordem de estudo sugerida

1. **Instalar** → LENS ou k9s
2. **Conectar** → apontar para o cluster
3. **Navegar** → explorar namespaces, pods, services
4. **Logs** → acompanhar logs de um pod
5. **Terminal** → entrar em um pod
6. **Escalar** → aumentar/diminuir réplicas
7. **Events** → usar para troubleshooting
