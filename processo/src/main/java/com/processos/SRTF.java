package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

<<<<<<< HEAD
=======

>>>>>>> 3f01bea (Terminando documentação dos algoritmos)
public class SRTF {
    private static List<Processo> processos = new ArrayList<>();
    private static int tempo = 0;

    private static List<Processo> processosProntos = new ArrayList<>();
    private static List<Processo> processosEmEspera = new ArrayList<>();

<<<<<<< HEAD
    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    public static int iniciarSimulacao() {
=======
    public static Metricas iniciarSimulacao() {
        resetar();
>>>>>>> 3f01bea (Terminando documentação dos algoritmos)
        lerProcessos();
        tempo = executarProcessos();
        return tempo;
    }

<<<<<<< HEAD
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
=======

    /**
     * Limpa todas as listas e reinicia o relógio.
     * Necessário porque os campos são static: persistem entre chamadas.
     */
    private static void resetar() {
        processos.clear();
        processosProntos.clear();
        processosEmEspera.clear();
        processosFinalizados.clear();
        tempo = 0;
    }

    /**
     * Lê os processos do arquivo e coloca o primeiro na fila de prontos.
     *
     * LeitorDeProcessos.criarProcessos() parseia o arquivo processos.txt
     * e retorna um array de Processo na ordem em que aparecem no arquivo
     * (que deve ser ordenada por tempo de chegada).
     */
    private static void lerProcessos() {
    processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
    verificarChegadas(); // substitui o getFirst() manual
}


    // =========================================================================
    // LOOP PRINCIPAL DA SIMULAÇÃO
    // =========================================================================

    private static int executarProcessos() {

        // Loop externo: enquanto houver processo pronto ou em I/O.
        while (temProcessosProntos() || temProcessosEmEspera()) {

            // ── CENÁRIO 1: CPU ociosa ─────────────────────────────────────────
            // Aguarda até que algum processo em I/O conclua e volte a ficar pronto.
            while (temProcessosEmEspera() && !temProcessosProntos()) {
                esperar();
                tempo++;
                verificarChegadas();
            }

            // ── CENÁRIO 2: Seleciona o processo de menor tempo restante ───────
            // executaMenorTempoRestante() percorre processosProntos, encontra
            // o processo com menor tempoRestante(), o remove da lista e
            // altera seu estado para EXECUTANDO.
            Processo exec = executaMenorTempoRestante();

            // Loop interno: executa o processo escolhido ciclo a ciclo.
            // O loop termina quando o processo não tem mais tempo restante
            // (tempoRestante() retorna burstTotal + penalidades de I/O - turnaround).
            while (exec.tempoRestante() > 0) {

                // Executa 1 ciclo de CPU: incrementa o turnaround interno do processo.
                exec.executarProcesso();
                tempo++;

                // Decrementa contadores de I/O de processos em espera e
                // recoloca na fila de prontos os que concluíram o I/O.
                esperar();

                // Verifica se algum processo do arquivo chegou agora.
                verificarChegadas();

                // ── PREEMPÇÃO ─────────────────────────────────────────────────
                // Esta é a principal diferença do SRTF em relação ao FCFS.
                // Após cada ciclo, verificamos se há na fila de prontos algum
                // processo com tempo restante MENOR que o processo atual.
                // Se sim, interrompemos o processo atual:
                //   1. devolverParaProntos() → estado volta a PRONTO e reinsere na lista.
                //   2. executaMenorTempoRestante() → seleciona o novo menor tempo.
                // O processo interrompido não perde progresso: seu turnaround
                // continua de onde parou quando for re-selecionado.
                if (devePreemptar(exec)) {
                    devolverParaProntos(exec);
                    exec = executaMenorTempoRestante();
                }

                // ── Verificação de I/O ────────────────────────────────────────
                // Verifica se o processo atingiu um instante de I/O.
                // Mesmo após uma possível preempção, "exec" pode ter mudado,
                // então verificamos o processo que está efetivamente executando.
                if (exec.getInstantesIO() != null) {
                    int proxIO = exec.proximoTempoDeIO(); // índice no array

                    if (exec.getTempoDeProcessador() == exec.getInstantesIO()[proxIO]) {
                        exec.definirProximoIO(proxIO + 1);
                        // exec.aumentarTempoTotalDeExecucao(); ← REMOVER esta linha
                        colocarProcessoEmEspera(exec);
                        exec = null;
                        break;
>>>>>>> 3f01bea (Terminando documentação dos algoritmos)
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

    private static boolean temProcessosEmEspera(){
        return !processosEmEspera.isEmpty();
    }
}