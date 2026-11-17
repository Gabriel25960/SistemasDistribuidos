# Projeto – Algoritmos Clássicos de Sistemas Distribuídos

**Aluno:** <NOME COMPLETO>
**Propostas implementadas:** 1 (Algoritmo de Berkeley) e 2 (Eleição em Anel – Chang-Roberts)
**Linguagem:** Java (JDK 11 ou superior, sem bibliotecas externas)

## 1. Estrutura

```
projeto-sd/
├── config/            endereços (IP:porta) de cada processo
│   ├── berkeley.properties
│   └── anel.properties
├── src/
│   ├── common/        camada comum: mensagens, TCP, log, configuração
│   ├── berkeley/      Proposta 1 (Participant, Coordinator, SimClock)
│   └── anel/          Proposta 2 (Node, Control)
├── scripts/           compilação e execução
├── logs/              logs gerados na execução
└── evidencias/        logs e prints usados como evidência
```

## 2. Compilar

Linux/Mac: `./scripts/build.sh`
Windows:   `scripts\build.bat`

Os arquivos compilados ficam em `./out`.

## 3. Verificar a comunicação TCP (opcional)

```
java -cp out common.SmokeTest --mode server --port 6000
java -cp out common.SmokeTest --mode client --host 127.0.0.1 --port 6000
```

## 4. Configuração de IP e porta

Cada processo tem uma linha `node.<id>=<host>:<porta>` no arquivo `.properties`.
Para executar em máquinas diferentes, basta trocar `127.0.0.1` pelo IP real.

## 5. Arquitetura de comunicação

- Cada processo é uma instância independente da JVM, executada em terminal separado.
- Comunicação exclusivamente por **sockets TCP**.
- Modelo requisição/resposta em conexão curta: uma linha de texto enviada, uma linha de resposta (ACK ou dado).
- Formato da mensagem: `TIPO;chave=valor;chave=valor` (uma por linha, delimitada por `\n`).
- Falha de conexão ou timeout gera `IOException`, usada como detecção de falhas.

## 6. Proposta 1 – Berkeley

### 6.1 Funcionamento
Rede de 5 processos independentes: 1 coordenador (id 0) e 4 participantes (ids 1 a 4).
Cada um tem um **relógio simulado** = relógio real da máquina + deslocamento inicial (`--offset`, em ms),
por isso todos começam com horários diferentes sem alterar o relógio do sistema operacional.

1. **Consulta:** o coordenador envia `GET_TIME` a cada participante e mede o tempo de ida e volta (RTT).
2. **Diferença:** para cada participante, `dif = relógio_informado − (relógio_do_coordenador_no_envio + RTT/2)`.
   A compensação de RTT/2 reduz o erro causado pelo atraso da rede.
3. **Média:** o coordenador calcula a média das diferenças, **incluindo ele próprio** (diferença 0).
4. **Ajuste:** `ajuste_i = média − dif_i`. O coordenador envia o **ajuste (delta)**, e não um horário absoluto.
5. **Aplicação:** cada participante recebe `ADJUST(delta)`, soma ao seu relógio e responde `ACK`. O coordenador aplica o próprio ajuste (`média`).
6. **Verificação:** o coordenador consulta os relógios novamente e imprime a tabela antes/depois.

Participantes que não respondem (3 tentativas) são excluídos da rodada.

### 6.2 Mensagens
| Mensagem | Origem → destino | Conteúdo | Resposta |
|---|---|---|---|
| `GET_TIME` | coordenador → participante | – | `TIME;time=<ms>` |
| `ADJUST` | coordenador → participante | `delta=<ms>` | `ACK;time=<ms>` |

### 6.3 Como executar (cada processo em um terminal separado)
```
java -cp out berkeley.Participant --id 1 --offset 30000
java -cp out berkeley.Participant --id 2 --offset -45000
java -cp out berkeley.Participant --id 3 --offset 12000
java -cp out berkeley.Participant --id 4 --offset -8000
java -cp out berkeley.Coordinator --offset 5000
```
Argumentos: `--id` (participante), `--offset` (deslocamento inicial em ms), `--config` (padrão `config/berkeley.properties`).
Execução automática: `./scripts/run_berkeley.sh` (Linux/Mac) ou `scripts\run_berkeley.bat` (Windows).

### 6.4 Resultado esperado
Diferenças antes do ajuste (relativas ao coordenador): P1 ≈ +25000, P2 ≈ −50000, P3 ≈ +7000, P4 ≈ −13000 ms.
Média ≈ −6200 ms. Após o ajuste, todos os relógios ficam iguais, com diferença residual de poucos milissegundos
(atraso e precisão da rede, principalmente na primeira conexão).

### 6.5 Observações
- Os horários no prefixo `[HH:mm:ss.SSS]` dos logs são do relógio real da máquina. Os relógios simulados aparecem no texto das mensagens.
- O ajuste é aplicado diretamente ao valor do relógio simulado. Em um sistema real, um ajuste negativo seria feito desacelerando o relógio para que ele nunca volte no tempo.
- Não há descarte de relógios discrepantes (outliers), pois não é exigido pelo enunciado.

## 7. Proposta 2 – Eleição em Anel (Chang-Roberts)

### 7.1 Funcionamento
Anel de 8 processos independentes (ids 1 a 8), na ordem definida em `config/anel.properties`
(`ring=3,7,1,8,5,2,6,4`). Cada processo conhece a lista completa do anel e seu sucessor é o próximo da lista.
O coordenador é o processo **ativo com maior identificador**.

Regras ao receber `ELECTION(id)`:

| Situação | Ação |
|---|---|
| `id` maior que o meu | encaminha sem alterar |
| `id` menor que o meu e eu ainda não participo | substitui por meu id e encaminha |
| `id` menor que o meu e eu já participo | descarta (evita mensagens duplicadas) |
| `id` igual ao meu | a mensagem deu a volta: sou o coordenador e envio `ELECTED` |

`ELECTED(leader)` circula pelo anel informando todos os processos. Cada nó atualiza o coordenador, encaminha a
mensagem e, ao encontrar um nó que já conhece o líder, a circulação termina.

### 7.2 Detecção de falhas e reconfiguração do anel
- **Falha ao enviar:** toda mensagem é enviada por TCP e só é considerada entregue quando o sucessor responde ACK.
  Se a conexão é recusada ou estoura o timeout (`timeout.ms`), o nó **pula** para o próximo processo da lista, de modo
  que as mensagens continuam circulando sem o processo indisponível.
- **Falha do coordenador:** cada nó envia `PING` ao coordenador a cada `heartbeat.ms`. Após 2 falhas seguidas, o nó
  considera o coordenador falho e inicia uma nova eleição (com pequena espera aleatória para reduzir eleições simultâneas;
  se já houver eleição em andamento, ele não inicia outra).
- **Eleição travada:** se um nó fica em estado de participante por mais de `election.timeout.ms` sem receber `ELECTED`
  (por exemplo, a mensagem se perdeu com a queda de um processo), a eleição é reiniciada.
- **Encerramento do coordenador:** o comando `quit` encerra o processo de imediato (`Runtime.halt`), sem avisar ninguém,
  simulando uma falha real.

### 7.3 Mensagens
| Mensagem | Conteúdo | Resposta |
|---|---|---|
| `ELECTION` | `id`, `from` | `ACK` (imediato; o processamento ocorre em seguida) |
| `ELECTED` | `leader`, `origin`, `from` | `ACK` |
| `PING` | `from` | `PONG` |
| `CMD` | `action` = `election`, `status` ou `quit` | `ACK` ou `STATUS` |

### 7.4 Como executar (um terminal por nó)
```
java -cp out anel.Node --id 1
java -cp out anel.Node --id 2
...
java -cp out anel.Node --id 8
```
Comandos digitados no terminal de cada nó: `election`, `status`, `quit`, `help`.
Os mesmos comandos podem ser enviados de fora: `java -cp out anel.Control --to 3 --cmd election`.

**Cenário da proposta (manual):**
1. Suba os 8 nós.
2. No terminal do nó 3, digite `election` (eleição inicial). Todos registram `NOVO COORDENADOR: P8`.
3. No terminal do nó 8, digite `quit` (encerramento do coordenador).
4. Em cerca de 5 segundos os demais nós detectam a falha e uma nova eleição circula pulando o nó 8.
5. Todos registram `NOVO COORDENADOR: P7`.

**Cenário automático:** `./scripts/run_anel.sh` (Linux/Mac) ou `scripts\run_anel.bat` (Windows, abre 8 janelas).

### 7.5 Testes adicionais realizados
- Duas eleições simultâneas (nós 3 e 5): os 8 nós convergem para o mesmo coordenador (P8).
- Queda do coordenador e, em seguida, do novo líder: P8 cai, P7 é eleito; P7 cai, P6 é eleito.
- Queda de um nó do meio do anel (P1): as mensagens continuam circulando e uma nova eleição conclui normalmente.

## 8. Evidências

Os logs de cada processo são gravados em `logs/` (`coordenador.log`, `P1.log` a `P4.log` no Berkeley;
`N1.log` a `N8.log` no anel). Copie-os para `evidencias/` após cada execução e adicione os prints dos terminais.

### Berkeley
| Item exigido | Onde aparece |
|---|---|
| Valores iniciais dos relógios | `coordenador.log` (PASSO 1) e primeira linha de cada `P<n>.log` |
| Comunicação coordenador ↔ participantes | `coordenador.log` (PASSO 2: `GET_TIME`) e `P<n>.log` ("coordenador pediu meu relógio") |
| Cálculo dos ajustes | `coordenador.log` (PASSO 3: média e ajuste de cada processo) |
| Envio e aplicação dos ajustes | `coordenador.log` (PASSO 4: `ADJUST`/ACK) e `P<n>.log` ("recebi ajuste") |
| Valores finais | `coordenador.log` (PASSO 5: tabela antes/depois) |

### Anel
| Item exigido | Onde aparece |
|---|---|
| Eleição inicial | `ELEICAO INICIADA POR P3 (comando manual)` e as linhas `recebi ELECTION(...)` de cada nó |
| Definição e comunicação do coordenador | `EU (P8) SOU O NOVO COORDENADOR` e `NOVO COORDENADOR: P8` em todos os nós |
| Encerramento do coordenador | `N8.log`: `P8 ENCERRADO (simulando falha)` |
| Detecção da falha | `COORDENADOR P8 CONSIDERADO FALHO (timeout / falha TCP)` nos demais nós |
| Reconfiguração do anel | `P8 INDISPONIVEL (...) -> anel reconfigurado: pulando para o proximo` |
| Nova eleição e novo coordenador | `ELEICAO INICIADA ... (falha do coordenador P8)` e `NOVO COORDENADOR: P7` em todos os nós |
