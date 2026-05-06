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

    public static int iniciarSimulacao(){
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
        for(Processo p : processos){
            System.out.println("Processo... " + p);
        }
    }

    private static int executarProcessos() {
        System.out.println("\n[EXECUÇÃO] Iniciando loop de execução de processos\n");

        int proximoIO;
        int[] instantesIO;
        Processo novoProcesso = null;
        
        while (temProcessosProntos() || temProcessosEmEspera()) {
            while (temProcessosEmEspera() && !temProcessosProntos()) {
                System.out.println(String.format("[EXECUÇÃO] Tempo %d: Aguardando processos I/O completarem...", tempo));
                esperar();
            }

            // executando round robin (primeira fila)
            while (filaMaiorPrioridade.temProcessosProntos()) {
                int quantum = 1;
                int tempoDeProcessador = 0;
                processoEmExecucao = filaMaiorPrioridade.proximoProcessoPronto();
                System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d (Prioridade 1 - RoundRobin) escalonado com quantum=%d", 
                                         tempo, processoEmExecucao.getPid(), quantum));
                
                proximoIO = processoEmExecucao.proximoTempoDeIO();
                instantesIO = processoEmExecucao.getInstantesIO();
                novoProcesso = null;
                
                while (processoEmExecucao.getTurnaround() <= tempo && tempoDeProcessador < quantum) {

                    // verifica se já deu o tempo de processador
                    if (tempoDeProcessador == quantum) {
                        System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d completou quantum, retornando à fila", 
                                                 tempo, processoEmExecucao.getPid()));
                        mandarParaFinalDaFilaDePronto(processoEmExecucao);
                        break;
                    }

                    //verifica se chegaram processos novos
                    if (temNovosProcessos()) {
                        novoProcesso = processos.getFirst();
                        if (novoProcesso.getChegada() == tempo) {
                            System.out.println(String.format("[CHEGADA] Tempo %d: Novo processo chegou! ID: %d, Prioridade: %d", 
                                                     tempo, novoProcesso.getPid(), novoProcesso.getPrioridade()));
                            definirProcessoComoPronto(novoProcesso);
                            processos.remove(novoProcesso);
                        }
                    }
                    

                    //verifica se o processo fez I/O
                    if (instantesIO != null) {
                        if(processoEmExecucao.getTurnaround() == instantesIO[proximoIO]){
                            System.out.println(String.format("[I/O] Tempo %d: Processo %d iniciou I/O (instante I/O: %d)", 
                                                     tempo, processoEmExecucao.getPid(), instantesIO[proximoIO]));
                            proximoIO++;
                            
                            processoEmExecucao.definirProximoIO(proximoIO);
                            colocarProcessoEmEspera(processoEmExecucao, filaMaiorPrioridade);  
                            tempoDeProcessador++;
                            esperar();
                            break;                          
                        }
                    }
                    
                    tempoDeProcessador++;
                    esperar();
                }
            }

            // executando FCFS (segunda fila)
            
            while (filaMenorPrioridade.temProcessosProntos() && !filaMaiorPrioridade.temProcessosProntos()) {

                int tempoTotalDeExecucao = 0;
                processoEmExecucao = filaMenorPrioridade.proximoProcessoPronto();
                System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d (Prioridade 2 - FCFS) escalonado", 
                                         tempo, processoEmExecucao.getPid()));
                
                proximoIO = processoEmExecucao.proximoTempoDeIO();
                instantesIO = processoEmExecucao.getInstantesIO();
                novoProcesso = null;

                if (processoEmExecucao.getInstantesIO() != null) {
                    System.out.println(String.format("[EXECUÇÃO] Tempo %d: Processo %d tem instantes de I/O definidos", 
                                             tempo, processoEmExecucao.getPid()));
                }
                else{
                    tempoTotalDeExecucao = processoEmExecucao.getTurnaround();
                    System.out.println(String.format("[EXECUÇÃO] Tempo %d: Processo %d sem I/O, tempo total de execução: %d", 
                                             tempo, processoEmExecucao.getPid(), tempoTotalDeExecucao));
                    while (tempoTotalDeExecucao <= tempo) {
                        if (temNovosProcessos()) {

                            novoProcesso = processos.getFirst();

                            //verifica se fez chegaram processos novos
                            if (novoProcesso.getChegada() == tempo) {
                                System.out.println(String.format("[CHEGADA] Tempo %d: Novo processo chegou! ID: %d, Prioridade: %d", 
                                                         tempo, novoProcesso.getPid(), novoProcesso.getPrioridade()));
                                definirProcessoComoPronto(novoProcesso);
                                if (novoProcesso.getPrioridade() == 1) {
                                    System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo de alta prioridade chegou, interrompendo FCFS", tempo));
                                    esperar();
                                    tempoTotalDeExecucao++;
                                    break;
                                }
                            }

                            //verifica se fez I/O
                        }
                    }
                    
                }
            }
        }

        return tempo;
    }

    private static boolean temNovosProcessos() {
        return !processos.isEmpty();
    }

    private static void colocarProcessoEmEspera(Processo p, FilaMLQ fila) {
        System.out.println(String.format("[I/O] Processo %d colocado em espera", p.getPid()));
        p.aumentarTempoTotalDeExecucao();
        fila.adicionarAhFilaDeEmEspera(p);
    }

    private static void mandarParaFinalDaFilaDePronto(Processo p) {
        System.out.println(String.format("[FILA] Processo %d retornou ao final da fila de prontos", p.getPid()));
        filaMaiorPrioridade.mandarParaFinalDaFilaDePronto(p);
    }

    private static Processo executarProcesso() {
        Processo novoProcesso = null;
        if (filaMaiorPrioridade.temProcessosProntos()) {
            novoProcesso = filaMaiorPrioridade.proximoProcessoPronto();
        }
        else if (filaMenorPrioridade.temProcessosProntos()){
            novoProcesso = filaMenorPrioridade.proximoProcessoPronto();
        }
        return novoProcesso;
    }

    private static void esperar() {
        tempo++;
        processoEmExecucao.executarProcesso();
        // System.out.println(String.format("[DEBUG] Tempo %d: Incrementado", tempo));
        filaMaiorPrioridade.esperar();
        filaMenorPrioridade.esperar();
    }

    private static boolean temProcessosEmEspera() {
        return !filaMaiorPrioridade.temProcessosEmEspera() || !filaMenorPrioridade.temProcessosEmEspera();
    }

    private static boolean temProcessosProntos() {
        return !filaMaiorPrioridade.temProcessosProntos() || !filaMenorPrioridade.temProcessosProntos();
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
                filaDeProcessos = null;
                System.out.println(String.format("[ERRO] Prioridade inválida para processo %d: %d", p.getPid(), prioridade));
                break;
        }
        filaDeProcessos.adicionarAhFilaDePronto(p);
    }
}