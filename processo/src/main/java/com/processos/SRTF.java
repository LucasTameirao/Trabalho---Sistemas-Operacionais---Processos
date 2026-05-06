package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

public class SRTF {
    private static List<Processo> processos = new ArrayList<>();
    private static int tempo = 0;

    private static List<Processo> processosProntos = new ArrayList<>();
    private static List<Processo> processosEmEspera = new ArrayList<>();

    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    public static int iniciarSimulacao() {
        lerProcessos();
        tempo = executarProcessos();
        return tempo;
    }

    private static int executarProcessos() {

        Processo processoEmExecucao;

        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {

            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                esperar();
                tempo++;
                if (temNovosProcessos()) {
                    Processo novoProcesso = processos.getFirst();
                    if (novoProcesso.getChegada() <= tempo) {
                        definirProcessoComoPronto(novoProcesso);
                        processos.remove(novoProcesso);
                    }
                }
            }

            processoEmExecucao = executaMenorTempoRestante();

            System.out.println("Executando processo " + processoEmExecucao.getPid()
                    + " | tempo restante: " + processoEmExecucao.tempoRestante());

            while (processoEmExecucao.tempoRestante() > 0) {

                processoEmExecucao.executarProcesso();
                tempo++;
                esperar();

                System.out.println("tempo: " + tempo
                        + " | PID em execucao: " + processoEmExecucao.getPid()
                        + " | restante: " + processoEmExecucao.tempoRestante());

                if (temNovosProcessos()) {
                    Processo novoProcesso = processos.getFirst();
                    if (novoProcesso.getChegada() == tempo) {
                        definirProcessoComoPronto(novoProcesso);
                        processos.remove(novoProcesso);
                        System.out.println("Processo " + novoProcesso.getPid() + " chegou no tempo " + tempo);

                        if (devePreemptar(processoEmExecucao)) {
                            System.out.println("Preempcao! Processo " + processoEmExecucao.getPid()
                                    + " volta pra fila. Novo menor: " + menorTempoRestante().getPid());
                            devolverParaProntos(processoEmExecucao);
                            processoEmExecucao = executaMenorTempoRestante();
                        }
                    }
                }

                if (processoEmExecucao.getInstantesIO() != null) {
                    int proximoIO = processoEmExecucao.proximoTempoDeIO();
                    if (processoEmExecucao.getTurnaround() == processoEmExecucao.getInstantesIO()[proximoIO]) {
                        System.out.println("Processo " + processoEmExecucao.getPid()
                                + " foi pra I/O no tempo " + tempo);
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);
                        processoEmExecucao.aumentarTempoTotalDeExecucao();
                        colocarProcessoEmEspera(processoEmExecucao);
                        break;
                    }
                }
            }
        }

        return tempo;
    }

    private static Processo menorTempoRestante() {
        Processo menor = processosProntos.getFirst();
        for (Processo p : processosProntos) {
            if (p.tempoRestante() < menor.tempoRestante()) {
                menor = p;
            }
        }
        return menor;
    }

    private static Processo executaMenorTempoRestante() {
        Processo p = menorTempoRestante();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    private static boolean devePreemptar(Processo processoAtual) {
        if (!temProcessosProntos()) {
            return false;
        }
        Processo candidato = menorTempoRestante();
        return candidato.tempoRestante() < processoAtual.tempoRestante();
    }

    private static void devolverParaProntos(Processo p) {
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosProntos.add(p);
    }

    private static boolean temNovosProcessos() {
        return !processos.isEmpty();
    }

    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        if (processosProntos.contains(p)) {
            processosProntos.remove(p);
        }
    }

    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if (processosEmEspera.contains(p)) {
            processosEmEspera.remove(p);
        }
    }

    private static void esperar() {
        for (int i = 0; i < processosEmEspera.size(); i++) {
            Processo p = processosEmEspera.get(i);
            p.esperar();
            System.out.println("Processo " + p.getPid() + " esperando I/O...");
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                System.out.println("Processo " + p.getPid() + " voltou do I/O");
                definirProcessoComoPronto(p);
            }
        }
    }
}