package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * ============================================================
 *  SRTF — Shortest Remaining Time First
 * ============================================================
 *
 * Variante PREEMPTIVA do SJF (Shortest Job First).
 * A cada instante, o escalonador seleciona o processo com o
 * MENOR TEMPO RESTANTE de execução. Se um novo processo chega
 * com tempo restante menor do que o processo em execução, ocorre
 * preempção: o processo atual volta para a fila de prontos e o
 * novo assume a CPU imediatamente.
 *
 * --- Classes auxiliares utilizadas ---
 *
 * Processo
 *   Encapsula todos os atributos e o comportamento de um processo.
 *   Métodos relevantes:
 *     executarProcesso()             → turnaround++ (1 tick de CPU consumido)
 *     esperar()                      → decrementa contador de I/O; estado → PRONTO quando chega a 0
 *     colocarEmEspera()              → estado → EM_ESPERA, seta countdown = TEMPO_DE_IO
 *     alterarEstado(e)               → muda o estado manualmente
 *     estadoProcesso()               → estado atual (EEstadoProcesso)
 *     getTurnaround()                → total de ticks de CPU já consumidos
 *     tempoTotalDeExecucao()         → burst original + penalidades de I/O acumuladas
 *     tempoRestante()                → tempoTotalDeExecucao() - getTurnaround()
 *     getInstantesIO()               → array com os turnarounds em que ocorre I/O
 *     proximoTempoDeIO()             → índice do próximo instante de I/O no array
 *     definirProximoIO(i)            → avança o ponteiro; anula array se esgotado
 *     aumentarTempoTotalDeExecucao() → soma TEMPO_DE_IO ao total (compensa a pausa de I/O)
 *     getChegada()                   → instante de chegada ao sistema
 *
 * LeitorDeProcessos
 *   Lê a configuração dos processos e retorna um Processo[].
 *
 * EEstadoProcesso
 *   Enum: PRONTO, EXECUTANDO, EM_ESPERA, FINALIZADO.
 */
public class SRTF {

    /** Processos que ainda não chegaram ao sistema (ordenados por chegada). */
    private static List<Processo> processos = new ArrayList<>();

    /** Relógio global da simulação. */
    private static int tempo = 0;

    /**
     * Fila de prontos: processos que já chegaram e aguardam a CPU.
     * Não mantemos ordem especial aqui — a seleção do menor tempo restante
     * é feita dinamicamente por menorTempoRestante() a cada decisão.
     */
    private static List<Processo> processosProntos = new ArrayList<>();

    /** Processos bloqueados em I/O. */
    private static List<Processo> processosEmEspera = new ArrayList<>();

    // =========================================================================
    //  INICIALIZAÇÃO
    // =========================================================================

    /**
     * Carrega todos os processos e coloca o primeiro na fila de prontos.
     * Os demais serão inseridos conforme o relógio avançar.
     */
    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));

        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
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
     * Núcleo da simulação SRTF.
     *
     * Fluxo geral:
     *
     *  ENQUANTO houver prontos OU processos em I/O:
     *
     *    [Fase de espera — CPU ociosa]
     *    Se não há prontos mas há I/O em andamento:
     *      → avançar o relógio e processar contadores de I/O até haver um pronto.
     *
     *    [Fase de execução]
     *    Selecionar o processo com menor tempoRestante() da fila de prontos.
     *    A cada tick:
     *      1. executarProcesso() — consome 1 tick de CPU
     *      2. tempo++
     *      3. esperar()           — avança contadores de I/O
     *      4. verificarNovosProcessos() — adiciona quem chegou neste instante
     *      5. Se novo processo tem menor tempo restante → PREEMPÇÃO
     *      6. Verificar se atingiu instante de I/O → bloquear
     *      7. Se tempoRestante() == 0 → processo finalizado
     */
    private static int executarProcessos() {

        Processo processoEmExecucao;

        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {

            // -----------------------------------------------------------------
            // Fase de espera: CPU ociosa, só há processos em I/O
            // -----------------------------------------------------------------
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                /*
                 * CORREÇÃO — consistência de operações:
                 * Alinhamos a ordem com o restante da simulação:
                 * tempo++ → esperar() → verificarNovosProcessos().
                 * O código original usava <= no getChegada() aqui, mas == no
                 * loop de execução. Unificamos para == (verificarNovosProcessos)
                 * para garantir que cada processo entre exatamente no instante certo.
                 */
                tempo++;
                esperar();
                verificarNovosProcessos();
            }

            // Seleciona o processo com menor tempo restante e o coloca em execução
            processoEmExecucao = executaMenorTempoRestante(); // estado → EXECUTANDO

            System.out.println("Executando processo " + processoEmExecucao.getPid()
                    + " | tempo restante: " + processoEmExecucao.tempoRestante());

            // -----------------------------------------------------------------
            // Loop de execução: roda até terminar, ir a I/O ou ser preemptado
            // -----------------------------------------------------------------
            while (processoEmExecucao.tempoRestante() > 0) {

                processoEmExecucao.executarProcesso(); // turnaround++
                tempo++;
                esperar();
                verificarNovosProcessos();

                System.out.println("tempo: " + tempo
                        + " | PID em execução: " + processoEmExecucao.getPid()
                        + " | restante: " + processoEmExecucao.tempoRestante());

                // -------------------------------------------------------------
                // Verifica preempção: chegou processo com menor tempo restante?
                // -------------------------------------------------------------
                if (devePreemptar(processoEmExecucao)) {
                    /*
                     * O método devePreemptar() compara o menor tempo restante
                     * da fila de prontos com o tempo restante do processo atual.
                     * Se algum processo da fila for mais curto, ocorre preempção:
                     * o processo atual volta para a fila de prontos e o mais
                     * curto assume a CPU.
                     */
                    System.out.println("Preempção! Processo " + processoEmExecucao.getPid()
                            + " volta pra fila. Novo menor: " + menorTempoRestante().getPid());
                    devolverParaProntos(processoEmExecucao);
                    processoEmExecucao = executaMenorTempoRestante();
                }

                // -------------------------------------------------------------
                // Verifica se o processo atingiu um instante de I/O
                // -------------------------------------------------------------
                if (processoEmExecucao.getInstantesIO() != null) {
                    int proximoIO = processoEmExecucao.proximoTempoDeIO();
                    if (processoEmExecucao.getTurnaround() == processoEmExecucao.getInstantesIO()[proximoIO]) {
                        System.out.println("Processo " + processoEmExecucao.getPid()
                                + " foi pra I/O no tempo " + tempo);

                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);

                        /*
                         * Compensa o burst total pelo tempo que ficará em I/O,
                         * para que tempoRestante() continue correto após o retorno.
                         */
                        processoEmExecucao.aumentarTempoTotalDeExecucao();
                        colocarProcessoEmEspera(processoEmExecucao); // estado → EM_ESPERA
                        break; // sai do loop interno; volta pelo loop externo após o I/O
                    }
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
     * e, em caso positivo, move TODOS os que chegaram neste instante para prontos.
     */
    private static void verificarNovosProcessos() {
        while (!processos.isEmpty() && processos.getFirst().getChegada() == tempo) {
            Processo novoProcesso = processos.getFirst();
            definirProcessoComoPronto(novoProcesso);
            processos.remove(novoProcesso);
            System.out.println("Processo " + novoProcesso.getPid() + " chegou no tempo " + tempo);
        }
    }

    /**
     * Varre a fila de prontos e retorna o processo com o menor tempoRestante().
     * Em caso de empate, retorna o primeiro encontrado (comportamento FCFS como desempate).
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
     * Seleciona o processo com menor tempo restante da fila de prontos,
     * remove-o da fila e altera seu estado para EXECUTANDO.
     */
    private static Processo executaMenorTempoRestante() {
        Processo p = menorTempoRestante();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    /**
     * Decide se deve ocorrer preempção.
     * Retorna true se houver algum processo na fila de prontos com
     * tempo restante ESTRITAMENTE menor do que o processo em execução.
     * Se a fila estiver vazia, não há candidato e retorna false.
     */
    private static boolean devePreemptar(Processo processoAtual) {
        if (!temProcessosProntos()) {
            return false;
        }
        Processo candidato = menorTempoRestante();
        return candidato.tempoRestante() < processoAtual.tempoRestante();
    }

    /**
     * Devolve um processo à fila de prontos sem perda de progresso.
     * O turnaround acumulado é preservado na instância do Processo.
     */
    private static void devolverParaProntos(Processo p) {
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosProntos.add(p);
    }

    /** Retorna true se há pelo menos um processo aguardando a CPU. */
    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    /**
     * Move o processo para a fila de espera de I/O.
     * colocarEmEspera() internamente: estado → EM_ESPERA e seta o countdown.
     */
    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        processosProntos.remove(p);
    }

    /**
     * Move o processo para a fila de prontos (estado → PRONTO).
     * Garante que o processo não permaneça na fila de espera.
     */
    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosEmEspera.remove(p);
    }

    /**
     * Itera sobre os processos em espera de I/O e avança seus contadores.
     * Quando o contador chega a 0, o processo volta para a fila de prontos.
     */
    private static void esperar() {
        for (int i = processosEmEspera.size() - 1; i >= 0; i--) {
            Processo p = processosEmEspera.get(i);
            p.esperar();
            System.out.println("Processo " + p.getPid() + " esperando I/O...");
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                System.out.println("Processo " + p.getPid() + " voltou do I/O");
                processosEmEspera.remove(i);     // remove pelo índice ANTES de adicionar aos prontos
                definirProcessoComoPronto(p);
                /*
                 * SRTF: ao retornar do I/O, o processo vai para a fila de prontos
                 * com seu tempo restante atualizado. Na próxima iteração do loop
                 * externo, devePreemptar() verificará se ele é o mais curto e
                 * poderá preemptá-lo se necessário.
                 */
            }
        }
    }
}