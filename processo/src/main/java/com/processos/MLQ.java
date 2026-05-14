package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

public class MLQ {
    private static List<Processo> processos = new ArrayList<>();
    private static Processo processoEmExecucao = null;
    private static int tempo = 0;

    private static FilaMLQ filaMaiorPrioridade = new FilaMLQ();
    private static FilaMLQ filaMenorPrioridade = new FilaMLQ();

    public static int iniciarSimulacao() {
        System.out.println("========== INICIANDO SIMULAÇÃO MLQ ==========");
        lerProcessos();
        System.out.println("Processos carregados com sucesso. Iniciando execução...");
        tempo = executarProcessos();
        System.out.println("========== SIMULAÇÃO FINALIZADA ==========");
        System.out.println(String.format("Tempo total de execução: %d unidades", tempo));
        return tempo;
    }

    private static void lerProcessos() {
        System.out.println("\n[LEITURA] Iniciando leitura de processos...");
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        System.out.println(String.format("[LEITURA] Total de processos carregados: %d", processos.size()));

        Processo novoProcesso = processos.getFirst();
        System.out.println(String.format("[LEITURA] Primeiro processo (ID: %d, Prioridade: %d) definido como pronto",
                novoProcesso.getPid(), novoProcesso.getPrioridade()));
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    private static int executarProcessos() {
        System.out.println("\n[EXECUÇÃO] Iniciando loop de execução de processos\n");

        int proximoIO;
        int[] instantesIO;
        Processo novoProcesso;

        while (temProcessosProntos() || temProcessosEmEspera()) {

            while (temProcessosEmEspera() && !temProcessosProntos()) {
                System.out.println(String.format("[EXECUÇÃO] Tempo %d: Aguardando processos I/O completarem...", tempo));
                esperar();
                tempo++;
                verificarNovosProcessos();
            }

            //#region Round Robin (fila de maior prioridade)
            while (filaMaiorPrioridade.temProcessosProntos()) {

                int quantum = 2;
                int tempoNoProcessador = 0;
                processoEmExecucao = filaMaiorPrioridade.proximoProcessoPronto();

                System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d (Prioridade 1 - RoundRobin) escalonado com quantum=%d",
                        tempo, processoEmExecucao.getPid(), quantum));

                proximoIO = processoEmExecucao.proximoTempoDeIO();
                instantesIO = processoEmExecucao.getInstantesIO();

                while (processoEmExecucao.estadoProcesso() == EEstadoProcesso.EXECUTANDO) {

                    if (tempo >= 10000) {
                        throw new IllegalStateException("Tempo de execução excedeu o limite");
                    }

                    // verifica se o processo terminou o burst
                    if (processoEmExecucao.getTurnaround() >= processoEmExecucao.tempoTotalDeExecucao()) {
                        processoEmExecucao.alterarEstado(EEstadoProcesso.FINALIZADO);
                        System.out.println(String.format("[FINALIZADO] Tempo %d: Processo %d finalizou",
                                tempo, processoEmExecucao.getPid()));
                        break;
                    }

                    // verifica se o processo deve ir para I/O
                    if (instantesIO != null && processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {
                        System.out.println(String.format("[I/O] Tempo %d: Processo %d iniciou I/O",
                                tempo, processoEmExecucao.getPid()));
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);
                        colocarProcessoEmEspera(processoEmExecucao, filaMaiorPrioridade);
                        break;
                    }

                    // verifica se o quantum foi esgotado
                    if (tempoNoProcessador == quantum) {
                        System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d completou quantum, retornando à fila",
                                tempo, processoEmExecucao.getPid()));
                        mandarParaFinalDaFilaDePronto(processoEmExecucao, filaMaiorPrioridade);
                        break;
                    }

                    processoEmExecucao.executarProcesso();
                    tempoNoProcessador++;
                    tempo++;
                    esperar();
                    verificarNovosProcessos();

                    System.out.println(String.format("[DEBUG] Tempo %d: Processo %d | turnaround: %d | total: %d",
                            tempo, processoEmExecucao.getPid(),
                            processoEmExecucao.getTurnaround(),
                            processoEmExecucao.tempoTotalDeExecucao()));
                }
            }
            //#endregion

            //#region FCFS (fila de menor prioridade)
            while (filaMenorPrioridade.temProcessosProntos() && !filaMaiorPrioridade.temProcessosProntos()) {

                processoEmExecucao = filaMenorPrioridade.proximoProcessoPronto();
                System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d (Prioridade 2 - FCFS) escalonado",
                        tempo, processoEmExecucao.getPid()));

                proximoIO = processoEmExecucao.proximoTempoDeIO();
                instantesIO = processoEmExecucao.getInstantesIO();

                while (processoEmExecucao.estadoProcesso() == EEstadoProcesso.EXECUTANDO
                        && !filaMaiorPrioridade.temProcessosProntos()) {

                    if (tempo >= 10000) {
                        throw new IllegalStateException(String.format("Tempo limite excedido | tempo: %d", tempo));
                    }

                    // verifica se o processo terminou o burst
                    if (processoEmExecucao.getTurnaround() >= processoEmExecucao.tempoTotalDeExecucao()) {
                        processoEmExecucao.alterarEstado(EEstadoProcesso.FINALIZADO);
                        System.out.println(String.format("[FINALIZADO] Tempo %d: Processo %d finalizou",
                                tempo, processoEmExecucao.getPid()));
                        break;
                    }

                    // verifica se o processo deve ir para I/O
                    if (instantesIO != null && processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {
                        System.out.println(String.format("[I/O] Tempo %d: Processo %d iniciou I/O",
                                tempo, processoEmExecucao.getPid()));
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);
                        colocarProcessoEmEspera(processoEmExecucao, filaMenorPrioridade);
                        break;
                    }

                    processoEmExecucao.executarProcesso();
                    tempo++;
                    esperar();
                    verificarNovosProcessos();

                    // verifica se chegou processo de alta prioridade após executar
                    if (filaMaiorPrioridade.temProcessosProntos()) {
                        System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo de alta prioridade chegou, interrompendo FCFS",
                                tempo));
                        mandarParaFinalDaFilaDePronto(processoEmExecucao, filaMenorPrioridade);
                        break;
                    }

                    System.out.println(String.format("[DEBUG] Tempo %d: Processo %d | turnaround: %d | total: %d",
                            tempo, processoEmExecucao.getPid(),
                            processoEmExecucao.getTurnaround(),
                            processoEmExecucao.tempoTotalDeExecucao()));
                }
            }
            //#endregion
        }

        return tempo;
    }

    private static void verificarNovosProcessos() {
        if (!processos.isEmpty()) {
            Processo novoProcesso = processos.getFirst();
            if (novoProcesso.getChegada() == tempo) {
                System.out.println(String.format("[CHEGADA] Tempo %d: Novo processo chegou! ID: %d, Prioridade: %d",
                        tempo, novoProcesso.getPid(), novoProcesso.getPrioridade()));
                definirProcessoComoPronto(novoProcesso);
                processos.remove(novoProcesso);
            }
        }
    }

    private static boolean temNovosProcessos() {
        return !processos.isEmpty();
    }

    private static void colocarProcessoEmEspera(Processo p, FilaMLQ fila) {
        System.out.println(String.format("[I/O] Processo %d colocado em espera", p.getPid()));
        fila.adicionarAhFilaDeEmEspera(p);
    }

    private static void mandarParaFinalDaFilaDePronto(Processo p, FilaMLQ fila) {
        System.out.println(String.format("[FILA] Processo %d retornou ao final da fila de prontos", p.getPid()));
        fila.mandarParaFinalDaFilaDePronto(p);
    }

    private static void esperar() {
        filaMaiorPrioridade.esperar();
        filaMenorPrioridade.esperar();
    }

    // Bug corrigido: lógica estava invertida com os ! desnecessários
    private static boolean temProcessosEmEspera() {
        return filaMaiorPrioridade.temProcessosEmEspera() || filaMenorPrioridade.temProcessosEmEspera();
    }

    private static boolean temProcessosProntos() {
        return filaMaiorPrioridade.temProcessosProntos() || filaMenorPrioridade.temProcessosProntos();
    }

    private static void definirProcessoComoPronto(Processo p) {
        FilaMLQ filaDeProcessos;
        int prioridade = p.getPrioridade();

        switch (prioridade) {
            case 1:
                filaDeProcessos = filaMaiorPrioridade;
                System.out.println(String.format("[FILA] Processo %d adicionado à fila de MAIOR prioridade", p.getPid()));
                break;
            case 2:
                filaDeProcessos = filaMenorPrioridade;
                System.out.println(String.format("[FILA] Processo %d adicionado à fila de MENOR prioridade", p.getPid()));
                break;
            default:
                throw new IllegalArgumentException(String.format("[ERRO] Prioridade inválida para processo %d: %d", p.getPid(), prioridade));
        }

        filaDeProcessos.adicionarAhFilaDePronto(p);
    }
}