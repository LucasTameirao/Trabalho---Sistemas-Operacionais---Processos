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
        
        if (temProcessosEmEspera()) {
            for (int i = 0; i < processosEmEspera.size(); i++) {
                processosEmEspera.get(i).esperar();
                
                if(processosEmEspera.get(i).estadoProcesso() == EEstadoProcesso.PRONTO){
                    adicionarAhFilaDePronto(processosEmEspera.get(i));
                }
            }
        }
    }

    public List<Processo> mandarParaFinalDaFilaDePronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        return processosProntos;
    }

    public Processo proximoProcessoPronto() {
        Processo novoProcesso = processosProntos.isEmpty() ? null : processosProntos.getFirst();
        novoProcesso.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(novoProcesso);
        return novoProcesso; 
    }

    public void adicionarAhFilaDeEmEspera(Processo p) {
        processosEmEspera.add(p);
        p.alterarEstado(EEstadoProcesso.EM_ESPERA);
        if (processosProntos.contains(p)) {
            processosProntos.remove(p);
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        s.append("Processos PRONTOS: \n");
        for(Processo p : processosProntos){
            s.append(p + "\n");
        }

        s.append("Porcessos EM ESPERA: \n");
        for(Processo p : processosEmEspera){
            s.append(p + "\n");
        }

        return s.toString();
    }
}
