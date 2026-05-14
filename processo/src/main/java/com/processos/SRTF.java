package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * ============================================================
 * SRTF — Shortest Remaining Time First
 *         (Menor Tempo Restante Primeiro)
 * ============================================================
 *
 * CONCEITO DO ALGORITMO:
 * O SRTF é a versão preemptiva do SJF (Shortest Job First).
 * A regra é simples: a CPU sempre vai para o processo que tem o
 * MENOR TEMPO RESTANTE de execução naquele instante.
 *
 * É PREEMPTIVO: se um novo processo chega com tempo restante menor
 * do que o processo atualmente em execução, o processo atual é
 * interrompido, devolvido à fila de prontos, e o recém-chegado
 * assume a CPU. Isso é chamado de "preempção".
 *
 * VANTAGEM: minimiza o tempo médio de espera — ótimo teoricamente.
 * DESVANTAGEM: pode causar "starvation" (inanição) em processos longos
 * se processos curtos continuam chegando indefinidamente.
 *
 * ESTRUTURAS DE DADOS:
 * As mesmas quatro listas do FCFS, com a diferença crítica de que
 * processosProntos não é consultada por ordem de inserção (FIFO),
 * mas sim pelo processo com menor tempoRestante() entre todos.
 *
 * - processos:             fila de chegada externa (do arquivo).
 * - processosProntos:      candidatos à CPU; o escolhido é sempre o de
 *                          menor tempoRestante(), não necessariamente o primeiro.
 * - processosEmEspera:     processos bloqueados aguardando I/O (5 unidades).
 * - processosFinalizados:  processos encerrados, usados para calcular métricas.
 * - tempo:                 relógio global da simulação.
 */
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


    // =========================================================================
    // API PÚBLICA
    // =========================================================================

    /**
     * Ponto de entrada único da simulação SRTF.
     *
     * Mesmo padrão do FCFS:
     * 1. resetar()            → garante estado limpo entre execuções.
     * 2. lerProcessos()       → lê o arquivo e inicializa a fila.
     * 3. executarProcessos()  → loop principal com lógica SRTF.
     * 4. Metricas             → calcula e exibe os resultados.
     *
     * @return objeto Metricas com turnaround médio, espera média e throughput.
     */
    public static Metricas iniciarSimulacao() {
        resetar();
        lerProcessos();
        tempo = executarProcessos();
        Metricas m = new Metricas("SRTF", processosFinalizados, tempo);
        m.imprimir();
        return m;
    }


    // =========================================================================
    // INICIALIZAÇÃO
    // =========================================================================

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

    /**
     * Coração do algoritmo SRTF.
     *
     * Diferença fundamental em relação ao FCFS:
     * a cada ciclo de CPU, verificamos se um processo novo chegou e se ele
     * tem tempo restante menor que o processo atual. Se sim, ocorre PREEMPÇÃO.
     *
     * CENÁRIO 1 — CPU ociosa (mesmo que FCFS):
     *   Todos os processos estão em I/O. O sistema aguarda, decrementando
     *   contadores de espera, até que algum processo conclua o I/O.
     *
     * CENÁRIO 2 — Há processo pronto:
     *   Seleciona o processo com MENOR tempoRestante() da fila de prontos.
     *   Executa ciclo a ciclo. A cada ciclo:
     *     a) Verifica se um processo novo chegou (verificarChegadas).
     *     b) Verifica se o recém-chegado tem tempo menor → PREEMPÇÃO.
     *     c) Verifica se o processo atual atingiu um instante de I/O → I/O.
     *
     * @return tempo total de execução da simulação.
     */
    private static int executarProcessos() {

        // Loop externo: enquanto houver processo pronto ou em I/O.
        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {

            // ── CENÁRIO 1: CPU ociosa ─────────────────────────────────────────
            // Aguarda até que algum processo em I/O conclua e volte a ficar pronto.
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
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

                    if (exec.getTurnaround() == exec.getInstantesIO()[proxIO]) {
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
}