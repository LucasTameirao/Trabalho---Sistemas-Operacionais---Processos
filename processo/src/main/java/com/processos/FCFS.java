package com.processos;

import com.processos.util.LeitorDeProcessos;

public class FCFS {
    private static Pilha<Processo> pilhaProcessos = new Pilha<>();
    private static int tempo = 0;

    private static void lerProcessos(){
        Processo[] processos = LeitorDeProcessos.criarProcessos();
        for(Processo p : processos){
            pilhaProcessos.empilhar(p);
        }
    }

    public static int executarProcessos(){
        lerProcessos();
        
        while (!pilhaProcessos.estahVazia()) {
            Processo processo = pilhaProcessos.desempilhar();
            tempo += processo.executarProcesso();
        }

        return tempo;
    }
}
