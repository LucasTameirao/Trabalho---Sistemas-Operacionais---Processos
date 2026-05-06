package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

public class FCFS {
    private static List<Processo> processos = new ArrayList<>();
    private static int tempo = 0;

    private static List<Processo> processosProntos = new ArrayList<>(); 
    private static List<Processo> processosEmEspera = new ArrayList<>(); 

    private static void lerProcessos(){
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    public static int iniciarSimulacao(){
        lerProcessos();
        tempo = executarProcessos();
        return tempo;
    }

    private static int executarProcessos(){

        Processo processoEmExecucao;
        int[] instantesIO;
        int tempoTotalDeExecucao;
        
        while(temProcessosProntos() || !processosEmEspera.isEmpty()){
            while(!processosEmEspera.isEmpty() && !temProcessosProntos()){
                esperar();
                tempo++;
            }
            System.out.println("tem processos prontos... ");
            for(Processo p : processosProntos){
                System.out.println(p);
            }
            processoEmExecucao = executaPrimeiroDaLista();
            System.out.println(processoEmExecucao);
            instantesIO = processoEmExecucao.getInstantesIO();
            tempoTotalDeExecucao = processoEmExecucao.tempoTotalDeExecucao();

            if (instantesIO != null) {
                System.out.println("entrou");
                int proximoIO = processoEmExecucao.proximoTempoDeIO();
                System.out.println(processoEmExecucao.getTurnaround());
                
                while(processoEmExecucao.getTurnaround() < tempoTotalDeExecucao){
                    

                    processoEmExecucao.executarProcesso();
                    tempo++;
                    esperar();
                    System.out.println("executou e esperou");
                    Processo novoProcesso = processos.isEmpty() ? null : processos.getFirst();
                    if(novoProcesso != null){
                        System.out.println(novoProcesso);
                    }
                    

                    //verifica se chegaram processos novos
                    if(temNovosProcessos()){
                        if(novoProcesso.getChegada() == tempo){ 
                            definirProcessoComoPronto(novoProcesso);
                            System.out.println("definiu como pronto, processo " + novoProcesso.getPid());
                            processos.remove(novoProcesso);
                            System.out.println("Processo " + novoProcesso.getPid() + " removido da lista");
                        }
                    }

                    // verifica se o processo fez I/O para coloca-lo em espera
                    if (processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {
                        System.out.println("Identificou o I/O no momento " + instantesIO[proximoIO]);
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);
                        processoEmExecucao.aumentarTempoTotalDeExecucao();
                        colocarProcessoEmEspera(processoEmExecucao);
                        System.out.println("colocou processo em espera");
                        break;
                    }

                    System.out.println("tempo de execução " + tempo);
                }
            }
            else{
                Processo novoProcesso;

                //verifica se durante a execução do processo surgiram mais processos
                while (processoEmExecucao.getTurnaround() < tempoTotalDeExecucao) {
                    processoEmExecucao.executarProcesso();
                    tempo++;
                    esperar();

                    if(temNovosProcessos()){
                        novoProcesso = processos.getFirst();
                        if (novoProcesso.getChegada() == tempo) {
                            processos.remove(novoProcesso);
                            definirProcessoComoPronto(novoProcesso);
                        }
                    }
                }
            }
        }

        System.out.println("Processos em espera");
        for(Processo p : processosEmEspera){
            System.out.println(p);
        }
        System.out.println("Processos prontos");
        for(Processo p : processosProntos){
            System.out.println(p);
        }
        return tempo;
    }

    private static boolean temNovosProcessos() {
        return !processos.isEmpty();
    }

    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    private static void colocarProcessoEmEspera(Processo p){
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        if(processosProntos.contains(p)){
            processosProntos.remove(p);
        }
    }

    private static void definirProcessoComoPronto(Processo p){
        
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if(processosEmEspera.contains(p)){
            processosEmEspera.remove(p);
        }
    }

    private static boolean processoEstahPronto(Processo p){
        return p.estadoProcesso() == EEstadoProcesso.PRONTO;
    }

    private static Processo executaPrimeiroDaLista(){
        Processo p = null;
        if (temProcessosProntos()) {
            p = processosProntos.getFirst();
            p.alterarEstado(EEstadoProcesso.EXECUTANDO);
            processosProntos.remove(p);
        }

        return p;
        
    }

    private static void esperar(){
        for(int i = 0; i < processosEmEspera.size(); i++){
            Processo p = processosEmEspera.get(i);
            System.out.println(p.esperar());
            System.out.println("processo " + p.getPid() + " esperou");
            System.out.println(p.estadoProcesso());
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                System.out.println("FICOU PRONTO E FOI PRA LISTA DE PRONTOS");
                definirProcessoComoPronto(p);
            }
        }
    }
}
