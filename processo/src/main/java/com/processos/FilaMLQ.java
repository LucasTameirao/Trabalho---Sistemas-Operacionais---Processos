package com.processos;

import java.util.ArrayList;
import java.util.List;

public class FilaMLQ {
    private List<Processo> processosProntos = new ArrayList<>(); 
    private List<Processo> processosEmEspera = new ArrayList<>(); 

    public FilaMLQ(){

    }

    public List<Processo> adicionarAhFilaDePronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if(processosEmEspera.contains(p)){
            processosEmEspera.remove(p);
        }
        return processosProntos;
    }


    public boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    public boolean temProcessosEmEspera() {
        return !processosEmEspera.isEmpty();
    }

    public void esperar() {
        for (Processo p : processosEmEspera) {
            p.esperar();
            if(p.estadoProcesso() == EEstadoProcesso.PRONTO){
                adicionarAhFilaDePronto(p);
            }
        }
    }

    public List<Processo> mandarParaFinalDaFilaDePronto(Processo p) {
        processosProntos.add(p);
        return processosProntos;
    }

    public Processo proximoProcessoPronto() {
        Processo novoProcesso = processosProntos.isEmpty() ? null : processosProntos.getFirst();
        processosProntos.remove(novoProcesso);
        return novoProcesso; 
    }

    public void adicionarAhFilaDeEmEspera(Processo p) {
        processosEmEspera.add(p);
        if (processosProntos.contains(p)) {
            processosProntos.remove(p);
        }
        p.alterarEstado(EEstadoProcesso.PRONTO);
    }
}
