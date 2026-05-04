package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

public class FCFS {
    private static Processo[] processos;
    private static int tempo = 0;

    private static List<Processo> processosProntos = new ArrayList<>(); 
    private static List<Processo> processosEmEspera = new ArrayList<>(); 

    private static void lerProcessos(){
        final int PRIMEIRO_PROCESSO = 0;
        processos = LeitorDeProcessos.criarProcessos();
        processosProntos.add(processos[PRIMEIRO_PROCESSO]);
        tempo = 0;
    }

    public static int executarProcessos(){
        if(processos == null){
            lerProcessos();
        }
        Processo processoEmExecucao = null;
        int burstTotal = 0;
        int[] instantesIO = null;
        int[] temposDeTurnaround = null;
        int tempoAtual = 0;

        while(temProcessosProntos()){
            processoEmExecucao = executaPrimeiroDaLista();

            burstTotal = processoEmExecucao.getBurstTotal();
            instantesIO = processoEmExecucao.getInstantesIO();

            if (instantesIO != null) {

                // definindo o tamanho do vetor (literalmente o desenho do diagrama de gantt)
                temposDeTurnaround = instantesIO[instantesIO.length - 1] == burstTotal ? new int[(instantesIO.length * 2) + 1] : new int[(instantesIO.length * 2) + 2];

                // definindo o primeiro valor do vetor que é a chegada do processo
                temposDeTurnaround[0] = processoEmExecucao.getChegada();

                int k = 1;
                int j = 0;
                int i = 0;
                while(tempoAtual < burstTotal){
                    if(processos[i].getChegada() == tempo){ 
                        definirProcessoComoPronto(processos[i++]);
                    }
                    if (tempoAtual == instantesIO[j]) {
                        temposDeTurnaround[k] = tempoAtual;
                        colocarProcessoEmEspera(processoEmExecucao, temposDeTurnaround);
                        executarProcessos();
                    }
                    tempoAtual++;
                    tempo++;
                    esperar();
                }

                // for(int i = 0; i < instantesIO.length; i++){
                //     if (tempoAtual >= 5) {
                //         definirProcessoComoPronto(processosEmEspera.getFirst());
                //     }
                //     if(i == 0){
                //         temposDeTurnaround[j] = temposDeTurnaround[j - 1] + instantesIO[i];
                //     }else{
                //         temposDeTurnaround[j] = temposDeTurnaround[j - 1] + (instantesIO[i] - instantesIO[i - 1]);
                //     }
                //     temposDeTurnaround[j + 1] = processo.getTEMPO_DE_IO() + temposDeTurnaround[j];
                //     tempoAtual += temposDeTurnaround[j + 1];
                    
                //     j += 2;
                // }

                temposDeTurnaround[temposDeTurnaround.length - 1] = temposDeTurnaround[temposDeTurnaround.length - 2] + (processoEmExecucao.getBurstTotal() - instantesIO[instantesIO.length - 1]);

                System.out.println(processoEmExecucao);
                System.out.println("===================\n");
                for(Integer t : temposDeTurnaround){
                    System.out.println(t);
                }
                System.out.println("===================\n");

                tempo += temposDeTurnaround[temposDeTurnaround.length - 1];
            }
            else{
                int i = 0;

                //verifica se durante a execução do processo surgiram mais processos
                while (tempo < processoEmExecucao.getBurstTotal()) {
                    if (tempo == processos[i].getChegada()) {
                        definirProcessoComoPronto(processos[i++]);
                    }
                    tempo++;
                }
            }
        }

        return tempo;
    }

    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    private static void colocarProcessoEmEspera(Processo p, int[] temposDeTurnaround){
        p.setTemposDeTurnaround(temposDeTurnaround);
        processosEmEspera.add(p);
        p.alterarEstado(EEstadoProcesso.EM_ESPERA);
        processosProntos.remove(p);
    }

    private static void definirProcessoComoPronto(Processo p){
        processosProntos.add(p);
        if(!processosEmEspera.contains(p)){
            processosEmEspera.remove(p);
        }
    }

    private static boolean processoEstahPronto(Processo p){
        return p.estadoProcesso() == EEstadoProcesso.PRONTO;
    }

    private static void trocaDeContexto(){

    }

    private static Processo executaPrimeiroDaLista(){
        Processo p = processosProntos.getFirst();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    private static void esperar(){
        for(Processo p : processosEmEspera){
            p.esperar();
            if (processoEstahPronto(p)) {
                definirProcessoComoPronto(p);
            }
        }
    }
}
