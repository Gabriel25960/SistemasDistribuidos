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
│   ├── berkeley/      Proposta 1
│   └── anel/          Proposta 2
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
(a preencher: descrição, mensagens, como executar, evidências)

## 8. Evidências
(a preencher: tabela "item exigido pelo enunciado → arquivo/print")
