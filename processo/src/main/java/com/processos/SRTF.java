package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

public class SRTF {

    // Processos aguardando chegada (ainda não estão no escalonador).
    private static List<Processo> processos          = new ArrayList<>();

    // Fila de prontos: qualquer processo aqui pode ser escolhido,
    // mas a escolha é sempre o de MENOR tempo restante.
    private static List<Processo> processosProntos   = new ArrayList<>();

    // Processos bloqueados por I/O. Cada um aguarda 5 unidades de tempo
    // antes de retornar à fila de prontos.
    private static List<Processo> processosEmEspera  = new ArrayList<>();

    // Processos encerrados. Guardados com instante de fim para cálculo de métricas.
    private static List<Processo> processosFinalizados = new ArrayList<>();

    // Relógio da simulação: avança 1 a cada ciclo de CPU executado.
    private static int tempo = 0;


    public static Metricas iniciarSimulacao() {
        resetar();
        lerProcessos();
        tempo = executarProcessos();
        Metricas m = new Metricas("SRTF", processosFinalizados, tempo);
        m.imprimir();
        return m;
    }


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
                    }
                }
            }

            // Após sair do loop interno: se o processo ainda está EXECUTANDO,
            // significa que tempoRestante() chegou a zero → processo concluído.
            // (Se saiu por I/O ou preempção, o estado já foi alterado antes.)
            if (exec != null && exec.estadoProcesso() == EEstadoProcesso.EXECUTANDO) {
                finalizarProcesso(exec, tempo);
            }
        }

        return tempo;
    }


    // =========================================================================
    // MÉTODOS AUXILIARES (HELPERS)
    // =========================================================================

    /**
     * Verifica se algum processo do arquivo chegou no tempo atual.
     * Como a lista está ordenada por chegada, basta checar o primeiro.
     */
    private static void verificarChegadas() {
    while (!processos.isEmpty()) {          // ← while, não if
        Processo novo = processos.getFirst();
        if (novo.getChegada() <= tempo) {
            definirProcessoComoPronto(novo);
            processos.remove(novo);
        } else {
            break;
        }
    }
}

    /**
     * Registra a finalização de um processo com o instante de fim.
     *
     * O instante de fim é usado pela classe Metricas para calcular:
     *   turnaroundReal = instanteDeFim - chegada
     *   tempoEsperaReal = turnaroundReal - burstTotal
     */
    private static void finalizarProcesso(Processo p, int tempoFim) {
        p.finalizarProcesso();        // estado → FINALIZADO
        p.setInstanteDeFim(tempoFim); // grava para as métricas
        processosFinalizados.add(p);
    }

    /**
     * Percorre processosProntos e retorna o processo com menor tempoRestante().
     *
     * tempoRestante() = tempoTotalDeExecucao - turnaround
     * Onde turnaround é o tempo de CPU já consumido pelo processo.
     *
     * Este método NÃO remove o processo da lista — apenas o encontra.
     * A remoção é feita por executaMenorTempoRestante().
     */
    private static Processo menorTempoRestante() {
        Processo menor = processosProntos.getFirst();
        for (Processo p : processosProntos) {
            if (p.tempoRestante() < menor.tempoRestante()) {
                menor = p;
            }
        }
        return menor;
    }

    /**
     * Seleciona o processo de menor tempo restante, remove da fila de prontos
     * e altera seu estado para EXECUTANDO.
     *
     * Chama menorTempoRestante() para a busca e então faz a remoção,
     * separando a responsabilidade de "encontrar" e "executar".
     */
    private static Processo executaMenorTempoRestante() {
        Processo p = menorTempoRestante();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    /**
     * Decide se o processo atual deve ser preemptado.
     *
     * A preempção ocorre quando existe na fila de prontos algum processo
     * com tempoRestante() estritamente menor que o processo em execução.
     *
     * Retorna false imediatamente se a fila de prontos estiver vazia,
     * pois não há candidato para preemptar.
     *
     * @param atual processo que está na CPU no momento.
     * @return true se deve preemptar, false caso contrário.
     */
    private static boolean devePreemptar(Processo atual) {
        if (!temProcessosProntos()) return false;
        return menorTempoRestante().tempoRestante() < atual.tempoRestante();
    }

    /**
     * Devolve o processo à fila de prontos após uma preempção.
     *
     * O processo não perde o progresso: seu turnaround interno continua
     * acumulado. Quando for selecionado novamente, executará a partir
     * do ponto onde parou.
     *
     * @param p processo que foi interrompido.
     */
    private static void devolverParaProntos(Processo p) {
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosProntos.add(p);
    }

    /**
     * Retorna true se há algum processo aguardando CPU.
     */
    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    /**
     * Move o processo para a fila de espera de I/O.
     *
     * Processo.colocarEmEspera() altera o estado para EM_ESPERA e
     * inicializa o contador tempoDeEspera = TEMPO_DE_IO (5).
     * A cada chamada de esperar(), esse contador diminui em 1.
     */
    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        processosProntos.remove(p);
    }

    /**
     * Adiciona o processo à fila de prontos e atualiza seu estado.
     *
     * Garante também que o processo seja removido de processosEmEspera,
     * cobrindo tanto a chegada inicial quanto o retorno de I/O.
     */
    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosEmEspera.remove(p);
    }

    /**
     * Avança o tempo de espera de I/O de todos os processos bloqueados.
     *
     * A cada chamada, Processo.esperar() decrementa o contador interno.
     * Quando chega a zero, o processo volta ao estado PRONTO automaticamente.
     * Detectamos isso aqui e o movemos para processosProntos.
     */
    private static void esperar() {
        for (int i = 0; i < processosEmEspera.size(); i++) {
            Processo p = processosEmEspera.get(i);
            p.esperar();
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                definirProcessoComoPronto(p);
            }
        }
    }

    private static boolean temProcessosEmEspera(){
        return !processosEmEspera.isEmpty();
    }
}