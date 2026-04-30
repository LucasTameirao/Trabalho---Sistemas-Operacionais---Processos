package com.processos.util;

public class Processo {
    int pid;
    int chegada;
    int burstTotal;
    int prioridade;
    int[] instantesIO;
    final int TEMPO_DE_IO = 5;

    public Processo(int pid, int chegada, int burstTotal, int prioridade, int[] instantesIO){
        this.pid = pid;
        this.chegada = chegada;
        this.burstTotal = burstTotal;
        this.instantesIO = instantesIO;
    }
}
