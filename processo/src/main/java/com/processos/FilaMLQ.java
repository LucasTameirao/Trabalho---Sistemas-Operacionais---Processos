package com.processos;

import java.util.ArrayList;
import java.util.List;

public class FilaMLQ {
    private List<Processo> processosProntos = new ArrayList<>();
    private List<Processo> processosEmEspera = new ArrayList<>();

    public FilaMLQ() {}

    public List<Processo> adicionarAhFilaDePronto(Processo p) {
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if (!processosProntos.contains(p)) {
            processosProntos.add(p);
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
        for (int i = processosEmEspera.size() - 1; i >= 0; i--) {
            Processo p = processosEmEspera.get(i);
            p.esperar();
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                processosEmEspera.remove(i); // remove ANTES de adicionar aos prontos
                adicionarAhFilaDePronto(p);
            }
        }
    }

    public List<Processo> mandarParaFinalDaFilaDePronto(Processo p) {
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if (!processosProntos.contains(p)) {
            processosProntos.add(p);
        }
        return processosProntos;
    }

    public Processo proximoProcessoPronto() {
        Processo p = processosProntos.getFirst();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    public void adicionarAhFilaDeEmEspera(Processo p) {
        p.alterarEstado(EEstadoProcesso.EM_ESPERA);
        if (!processosEmEspera.contains(p)) {
            processosEmEspera.add(p);
        }
        processosProntos.remove(p);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("Processos PRONTOS: \n");
        for (Processo p : processosProntos) {
            s.append(p + "\n");
        }
        s.append("Processos EM ESPERA: \n");
        for (Processo p : processosEmEspera) {
            s.append(p + "\n");
        }
        return s.toString();
    }
}