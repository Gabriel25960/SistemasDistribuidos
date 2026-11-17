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
(a preencher: descrição, mensagens, como executar, evidências)

## 7. Proposta 2 – Eleição em Anel (Chang-Roberts)
(a preencher: descrição, mensagens, como executar, evidências)

## 8. Evidências
(a preencher: tabela "item exigido pelo enunciado → arquivo/print")
