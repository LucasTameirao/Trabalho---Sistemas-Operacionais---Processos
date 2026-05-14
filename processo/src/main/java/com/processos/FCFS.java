package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * ============================================================
 *  FCFS — First-Come, First-Served
 * ============================================================
 *
 * Política de escalonamento NÃO-PREEMPTIVA: o processo que chegar
 * primeiro ocupa a CPU e permanece nela até finalizar ou até precisar
 * realizar I/O. Nenhum processo em execução é interrompido por outro
 * que chegue depois.
 *
 * --- Classes auxiliares utilizadas ---
 *
 * Processo
 *   Encapsula todos os atributos e o comportamento de um processo:
 *   pid, chegada, burstTotal, prioridade, instantesIO, turnaround, etc.
 *   Métodos relevantes:
 *     executarProcesso()             → consome 1 unidade de CPU (turnaround++)
 *     esperar()                      → decrementa o contador de I/O; quando
 *                                      chega a 0 o estado volta para PRONTO
 *     colocarEmEspera()              → estado → EM_ESPERA, seta countdown = TEMPO_DE_IO
 *     alterarEstado(e)               → muda o estado manualmente
 *     estadoProcesso()               → retorna o estado atual (EEstadoProcesso)
 *     getTurnaround()                → total de unidades de CPU já consumidas
 *     tempoTotalDeExecucao()         → burst original + penalidades de I/O acumuladas
 *     getInstantesIO()               → array com os turnarounds em que ocorre I/O
 *     proximoTempoDeIO()             → índice do próximo instante de I/O no array
 *     definirProximoIO(i)            → avança o ponteiro; anula array se esgotado
 *     aumentarTempoTotalDeExecucao() → soma TEMPO_DE_IO ao total (compensa a pausa)
 *     getChegada()                   → instante em que o processo entra no sistema
 *
 * LeitorDeProcessos
 *   Lê a configuração dos processos e retorna um Processo[].
 *
 * EEstadoProcesso
 *   Enum com os estados possíveis: PRONTO, EXECUTANDO, EM_ESPERA, FINALIZADO.
 */
public class FCFS {

    /** Processos que ainda não chegaram ao sistema (ordenados por chegada). */
    private static List<Processo> processos = new ArrayList<>();

    /**
     * Relógio global da simulação.
     * Avança +1 a cada unidade de CPU consumida ou de espera de I/O.
     */
    private static int tempo = 0;

    /**
     * Fila de prontos: processos que já chegaram e aguardam a CPU.
     * Como é FCFS, a ordem de inserção define a ordem de execução.
     */
    private static List<Processo> processosProntos = new ArrayList<>();

    /** Processos bloqueados em I/O; cada um carrega seu próprio contador regressivo. */
    private static List<Processo> processosEmEspera = new ArrayList<>();

    // =========================================================================
    //  INICIALIZAÇÃO
    // =========================================================================

    /**
     * Carrega todos os processos e coloca o primeiro na fila de prontos.
     *
     * Decisão de design: apenas o primeiro processo é adicionado aqui porque
     * ele tipicamente chega no instante 0. Os demais têm tempo de chegada
     * futuro e serão inseridos na fila de prontos dentro do loop principal,
     * quando o relógio atingir o instante correto de cada um.
     */
    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));

        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso); // estado → PRONTO
        processos.remove(novoProcesso);           // sai da lista "a chegar"
    }

    /** Ponto de entrada público. Retorna o instante de término da simulação. */
    public static int iniciarSimulacao() {
        lerProcessos();
        tempo = executarProcessos();
        return tempo;
    }

    // =========================================================================
    //  LOOP PRINCIPAL
    // =========================================================================

    /**
     * Núcleo da simulação FCFS.
     *
     * Fluxo geral:
     *
     *  ENQUANTO houver prontos OU processos em I/O:
     *
     *    [Fase de espera — CPU ociosa]
     *    Se não há prontos mas há processos em I/O:
     *      → avançar o relógio e processar os contadores de I/O até
     *        que algum processo fique pronto.
     *
     *    [Fase de execução]
     *    Pegar o primeiro da fila de prontos (FCFS) e executá-lo:
     *      → SE o processo tem I/O:
     *           rodar até atingir o instante de I/O → bloquear → sair do loop interno
     *      → SE o processo NÃO tem I/O:
     *           rodar até esgotar o burst
     *
     *    A cada unidade de tempo, em ambos os caminhos:
     *      1. executarProcesso() consome 1 tick de CPU
     *      2. tempo++ avança o relógio
     *      3. esperar() decrementa contadores de I/O (pode liberar processos)
     *      4. verificarNovosProcessos() insere na fila quem chegou neste instante
     */
    private static int executarProcessos() {

        Processo processoEmExecucao;
        int[] instantesIO;
        int tempoTotalDeExecucao;

        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {

            // -----------------------------------------------------------------
            // Fase de espera: CPU ociosa, apenas processos em I/O existem
            // -----------------------------------------------------------------
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                /*
                 * CORREÇÃO — ordem de operações:
                 * O código original chamava esperar() ANTES de tempo++, o que
                 * fazia o countdown de I/O ser decrementado antes do relógio
                 * avançar, consumindo 1 tick a mais do que o esperado.
                 * Agora: tempo++ primeiro → esperar() → verificarNovosProcessos(),
                 * alinhado com o comportamento do loop de execução abaixo.
                 */
                tempo++;
                esperar();
                verificarNovosProcessos();
            }

            // Pega o primeiro da fila de prontos (FCFS) e o coloca em execução
            processoEmExecucao   = executaPrimeiroDaLista(); // estado → EXECUTANDO
            instantesIO          = processoEmExecucao.getInstantesIO();
            tempoTotalDeExecucao = processoEmExecucao.tempoTotalDeExecucao();

            // -----------------------------------------------------------------
            // Execução de processo COM I/O
            // -----------------------------------------------------------------
            if (instantesIO != null) {

                /*
                 * proximoIO é o índice no array instantesIO[] que indica o
                 * próximo turnaround em que o processo deve ir a I/O.
                 * Ex.: instantesIO = {3, 8} → I/O quando turnaround == 3,
                 *      depois quando turnaround == 8 (já com o acréscimo de TEMPO_DE_IO).
                 */
                int proximoIO = processoEmExecucao.proximoTempoDeIO();

                while (processoEmExecucao.getTurnaround() < tempoTotalDeExecucao) {

                    processoEmExecucao.executarProcesso(); // turnaround++
                    tempo++;
                    esperar();
                    verificarNovosProcessos();

                    // Verifica se atingiu o instante de I/O deste processo
                    if (processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {

                        /*
                         * Avança o ponteiro. Se não houver mais instantes de I/O,
                         * definirProximoIO() anula o array internamente, e chamadas
                         * futuras a getInstantesIO() retornarão null.
                         */
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);

                        /*
                         * Compensa o burst total: o processo ficará TEMPO_DE_IO
                         * unidades bloqueado, então somamos esse valor ao total
                         * esperado para que o loop saiba que ainda há trabalho
                         * após o retorno do I/O.
                         */
                        processoEmExecucao.aumentarTempoTotalDeExecucao();
                        colocarProcessoEmEspera(processoEmExecucao); // estado → EM_ESPERA
                        break; // processo volta pela fila de prontos após o I/O
                    }
                }

            // -----------------------------------------------------------------
            // Execução de processo SEM I/O
            // -----------------------------------------------------------------
            } else {

                while (processoEmExecucao.getTurnaround() < tempoTotalDeExecucao) {
                    processoEmExecucao.executarProcesso();
                    tempo++;
                    esperar();
                    verificarNovosProcessos();
                }
            }
        }

        return tempo;
    }

    // =========================================================================
    //  MÉTODOS AUXILIARES
    // =========================================================================

    /**
     * Verifica se algum processo da lista "a chegar" tem chegada == tempo atual
     * e, em caso positivo, move-o para a fila de prontos.
     *
     * CORREÇÃO — múltiplos processos no mesmo instante:
     * O código original verificava apenas o primeiro da lista, ignorando
     * processos que chegam simultaneamente. O loop "while" garante que TODOS
     * os processos com chegada == tempo sejam adicionados.
     */
    private static void verificarNovosProcessos() {
        while (!processos.isEmpty() && processos.getFirst().getChegada() == tempo) {
            Processo novoProcesso = processos.getFirst();
            definirProcessoComoPronto(novoProcesso);
            processos.remove(novoProcesso);
        }
    }

    /** Retorna true se há pelo menos um processo aguardando a CPU. */
    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    /**
     * Move o processo para a fila de espera de I/O.
     * colocarEmEspera() internamente: estado → EM_ESPERA e seta o countdown.
     * Garante que o processo não permaneça na fila de prontos.
     */
    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        processosProntos.remove(p); // no-op se não estiver lá
    }

    /**
     * Move o processo para a fila de prontos (estado → PRONTO).
     * Garante que o processo não permaneça na fila de espera.
     */
    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosEmEspera.remove(p); // no-op se não estiver lá
    }

    /**
     * Remove o primeiro processo da fila de prontos, altera seu estado para
     * EXECUTANDO e o retorna.
     * FCFS: quem chegou primeiro (primeiro inserido) sai primeiro.
     */
    private static Processo executaPrimeiroDaLista() {
        Processo p = processosProntos.getFirst();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    /**
     * Itera sobre todos os processos em espera de I/O e avança seus
     * contadores regressivos em 1 unidade (p.esperar()).
     * Quando o contador chega a 0, o estado interno do processo muda
     * para PRONTO e aqui o movemos para a fila de prontos.
     *
     * CORREÇÃO — iteração reversa:
     * O código original iterava do início para o fim com índice i++.
     * Ao chamar definirProcessoComoPronto() (que remove da lista de espera)
     * durante a iteração, o índice pulava o elemento seguinte.
     * A iteração de trás para frente resolve esse problema: remover o
     * elemento na posição i não afeta os índices 0..i-1 já visitados.
     */
    private static void esperar() {
        for (int i = processosEmEspera.size() - 1; i >= 0; i--) {
            Processo p = processosEmEspera.get(i);
            p.esperar(); // decrementa; muda estado para PRONTO se countdown == 0
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                processosEmEspera.remove(i); // remove pelo índice ANTES de adicionar aos prontos
                definirProcessoComoPronto(p);
            }
        }
    }
}