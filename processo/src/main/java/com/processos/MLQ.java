package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;


public class MLQ {

    private static final int QUANTUM_FILA1 = 2;

    // Lista global de processos que ainda não chegaram ao escalonador.
    // Lida do arquivo processos.txt por LeitorDeProcessos.criarProcessos().
    // Permanece ordenada por tempo de chegada.
    private static List<Processo> processos = new ArrayList<>();

    // Processos que finalizaram completamente.
    // Passados para a classe Metricas ao final da simulação para
    // calcular turnaround médio, tempo de espera médio e throughput.
    private static List<Processo> processosFinalizados = new ArrayList<>();

    // Relógio global: incrementado a cada ciclo de CPU executado.
    private static int tempo = 0;

    // Fila de alta prioridade: gerencia processos com prioridade == 1.
    // Internamente usa Round-Robin com quantum = QUANTUM_FILA1 (= 2).
    // FilaMLQ encapsula duas listas: processosProntos e processosEmEspera,
    // expondo métodos como proximoProcessoPronto(), esperar(), etc.
    private static FilaMLQ filaMaiorPrioridade = new FilaMLQ();

    // Fila de baixa prioridade: gerencia processos com prioridade == 2.
    // Internamente usa FCFS (First-Come, First-Served): sem preempção
    // entre processos da mesma fila, ordem de chegada é respeitada.
    private static FilaMLQ filaMenorPrioridade = new FilaMLQ();



    public static Metricas iniciarSimulacao() {
        resetar();
        lerProcessos();
        tempo = executarProcessos();
        Metricas m = new Metricas("MLQ", processosFinalizados, tempo);
        m.imprimir();
        return m;
    }


    private static void resetar() {
        processos.clear();
        processosFinalizados.clear();
        filaMaiorPrioridade = new FilaMLQ(); // recria o objeto para garantir estado limpo
        filaMenorPrioridade = new FilaMLQ(); // idem
        tempo = 0;
    }


    private static void lerProcessos() {
    processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
    verificarChegadas(); // adiciona TODOS os que chegaram em t=0 de uma vez
    }


    // =========================================================================
    // LOOP PRINCIPAL DA SIMULAÇÃO
    // =========================================================================


    private static int executarProcessos() {

        // Loop externo: continua enquanto há trabalho pendente.
        // temProcessosProntos() verifica AMBAS as filas (Fila 1 e Fila 2).
        // temProcessosEmEspera() verifica I/O pendente em AMBAS as filas.
        while (temProcessosProntos() || temProcessosEmEspera()) {

            while (!temProcessosProntos() && temProcessosEmEspera()) {
                esperar();
                tempo++;
                verificarChegadas();
            }


            // ══════════════════════════════════════════════════════════════════
            // BLOCO B — FILA 1: Round-Robin com quantum = QUANTUM_FILA1 (2)
            // ══════════════════════════════════════════════════════════════════

            while (filaMaiorPrioridade.temProcessosProntos()) {

                // Retira o primeiro processo da Fila 1 e o marca como EXECUTANDO.
                // FilaMLQ.proximoProcessoPronto() remove o processo da lista
                // interna de prontos e chama p.alterarEstado(EXECUTANDO).
                // No Round-Robin, "primeiro" significa o mais antigo na fila
                // (FIFO), garantindo que todos se revezem de forma justa.
                Processo exec = filaMaiorPrioridade.proximoProcessoPronto();

                // Contador de ciclos executados neste quantum.
                // Quando ciclos == QUANTUM_FILA1, o processo esgotou sua fatia.
                int ciclos = 0;

                // Executa o processo ciclo a ciclo enquanto:
                //   (a) ainda está em estado EXECUTANDO (não foi para I/O)
                //   (b) não esgotou o quantum (ciclos < QUANTUM_FILA1)
                while (exec.estadoProcesso() == EEstadoProcesso.EXECUTANDO
                        && ciclos < QUANTUM_FILA1) {

                    // ── Verificação de I/O ANTES de executar ─────────────────

                    if (exec.getInstantesIO() != null) {
                        int proxIO = exec.proximoTempoDeIO();
                        if (exec.getTempoDeProcessador() == exec.getInstantesIO()[proxIO]) {

                                exec.definirProximoIO(proxIO + 1);
                                // exec.aumentarTempoTotalDeExecucao(); ← REMOVER esta linha
                                colocarProcessoEmEspera(exec, filaMaiorPrioridade);
                                exec = null;
                                break;
                            }
                    }

                    // Executa 1 ciclo de CPU:
                    // Processo.executarProcesso() incrementa o turnaround interno
                    // do processo em 1. O turnaround aqui representa a quantidade
                    // de ciclos de CPU consumidos, não o turnaround real (tempo
                    // total desde a chegada), que é calculado nas Metricas.
                    exec.executarProcesso();
                    ciclos++;
                    tempo++;

                    // Avança o I/O de todos os processos em espera em AMBAS as filas.
                    // FilaMLQ.esperar() itera sobre os processos em espera da fila
                    // e decrementa o contador de cada um. Quando algum chega a zero,
                    // FilaMLQ o move automaticamente de volta para processosProntos.
                    esperar();

                    // Verifica se novos processos chegaram neste instante.
                    // Processos de alta prioridade (prioridade==1) que chegam aqui
                    // entram em filaMaiorPrioridade e serão atendidos na PRÓXIMA
                    // iteração do while externo (filaMaiorPrioridade.temProcessosProntos()).
                    verificarChegadas();

                    // Verifica se o processo terminou dentro do quantum.
                    // tempoRestante() = tempoTotalDeExecucao - turnaround.
                    // Se chegou a zero (ou menos), o processo concluiu todo o burst.
                    if (exec.tempoRestante() <= 0) {
                        finalizarProcesso(exec, tempo);
                        exec = null; // marca como tratado
                        break;
                    }
                }

                if (exec != null && exec.estadoProcesso() == EEstadoProcesso.EXECUTANDO) {
                    mandarParaFinalDaFilaDePronto(exec, filaMaiorPrioridade);
                }
            }


            // ══════════════════════════════════════════════════════════════════
            // BLOCO C — FILA 2: FCFS
            // ══════════════════════════════════════════════════════════════════

            while (filaMenorPrioridade.temProcessosProntos()
                    && !filaMaiorPrioridade.temProcessosProntos()) {

                // Retira o primeiro processo da Fila 2 (FIFO) e marca como EXECUTANDO.
                Processo exec = filaMenorPrioridade.proximoProcessoPronto();

                // ── Loop interno da Fila 2 ────────────────────────────────────
                // Executa o processo enquanto:
                //   (a) ainda está em estado EXECUTANDO
                //   (b) a Fila 1 continua vazia (sem preempção por alta prioridade)
                while (exec.estadoProcesso() == EEstadoProcesso.EXECUTANDO
                        && !filaMaiorPrioridade.temProcessosProntos()) {

                    // ── Verificação de I/O ANTES de executar ─────────────────

                    if (exec.getInstantesIO() != null) {
                        int proxIO = exec.proximoTempoDeIO();
                        if (exec.getTempoDeProcessador() == exec.getInstantesIO()[proxIO]) {

                                exec.definirProximoIO(proxIO + 1);
                                colocarProcessoEmEspera(exec, filaMenorPrioridade);
                                exec = null;
                                break;
                        }
                    }

                    // Executa 1 ciclo de CPU.
                    exec.executarProcesso();
                    tempo++;

                    // Avança I/O de AMBAS as filas.
                    // Isso é crucial: um processo da Fila 1 pode concluir seu
                    // I/O enquanto a Fila 2 está executando, e precisa retornar
                    // a filaMaiorPrioridade para acionar a preempção.
                    esperar();

                    // Verifica chegadas: um novo processo de alta prioridade pode
                    // chegar neste instante e entrar em filaMaiorPrioridade.
                    verificarChegadas();

                    // ── Verificação de preempção pela Fila 1 ─────────────────
                    // Após cada ciclo, checamos se a Fila 1 ganhou algum processo
                    // (seja por nova chegada ou por retorno de I/O via esperar()).
                    // Se sim, interrompemos o processo da Fila 2:
                    //   1. mandarParaFinalDaFilaDePronto() → reinsere no FIM da
                    //      fila de prontos da Fila 2, preservando o progresso.
                    //   2. exec = null → sinaliza que o processo foi tratado.
                    //   3. break → sai do loop interno; o while externo (Bloco B)
                    //      na próxima iteração assumirá o processo da Fila 1.
                    if (filaMaiorPrioridade.temProcessosProntos()) {
                        mandarParaFinalDaFilaDePronto(exec, filaMenorPrioridade);
                        exec = null;
                        break;
                    }

                    // Verifica se o processo terminou.
                    if (exec.tempoRestante() <= 0) {
                        finalizarProcesso(exec, tempo);
                        exec = null;
                        break;
                    }
                }

                if (exec != null && exec.estadoProcesso() == EEstadoProcesso.EXECUTANDO) {
                    mandarParaFinalDaFilaDePronto(exec, filaMenorPrioridade);
                }
            }
        }

        return tempo;
    }


    // =========================================================================
    // MÉTODOS AUXILIARES (HELPERS)
    // =========================================================================

    /**
     * Verifica se algum processo do arquivo deve entrar em uma das filas.
     *
     * DECISÃO DE PROJETO: o arquivo está ordenado por tempo de chegada,
     * então basta verificar sempre o PRIMEIRO elemento da lista. Se ele
     * ainda não chegou (chegada > tempo), nenhum outro chegou também,
     * e podemos parar a verificação imediatamente.
     *
     * O roteamento para Fila 1 ou Fila 2 é feito por definirProcessoComoPronto(),
     * que inspeciona a prioridade do processo recém-chegado.
     */
    private static void verificarChegadas() {
    while (!processos.isEmpty()) {          // ← while em vez de if
        Processo novo = processos.getFirst();
        if (novo.getChegada() <= tempo) {
            definirProcessoComoPronto(novo);
            processos.remove(novo);
            // continua o loop: verifica o próximo da lista
        } else {
            break; // lista ordenada por chegada: se este não chegou, nenhum chegou
        }
    }
}

    /**
     * Finaliza um processo: registra o instante de fim e o adiciona à lista
     * de finalizados para posterior cálculo de métricas.
     *
     * Processo.finalizarProcesso() altera o estado para FINALIZADO.
     * Processo.setInstanteDeFim(tempoFim) grava o instante atual do relógio.
     * Esse valor é usado pela classe Metricas para calcular:
     *   turnaroundReal  = instanteDeFim - chegada
     *   tempoEsperaReal = turnaroundReal - burstTotal
     *
     * @param p       processo que terminou.
     * @param tempoFim instante do relógio em que o processo concluiu.
     */
    private static void finalizarProcesso(Processo p, int tempoFim) {
        p.finalizarProcesso();
        p.setInstanteDeFim(tempoFim);
        processosFinalizados.add(p);
    }

    /**
     * Verifica se há algum processo pronto em QUALQUER das duas filas.
     *
     * Usado na condição do loop externo de executarProcessos() para saber
     * se a simulação ainda tem trabalho a fazer na CPU.
     *
     * FilaMLQ.temProcessosProntos() verifica internamente se a lista de
     * prontos da fila não está vazia.
     */
    private static boolean temProcessosProntos() {
        return filaMaiorPrioridade.temProcessosProntos()
                || filaMenorPrioridade.temProcessosProntos();
    }

    /**
     * Verifica se há processo em espera de I/O em QUALQUER das duas filas.
     *
     * Usado em conjunto com temProcessosProntos() na condição do loop externo
     * para distinguir entre "simulação encerrada" (ambos vazios) e "CPU ociosa"
     * (sem prontos, mas com processos em I/O).
     *
     * FilaMLQ.temProcessosEmEspera() verifica internamente a lista de em espera.
     */
    private static boolean temProcessosEmEspera() {
        return filaMaiorPrioridade.temProcessosEmEspera()
                || filaMenorPrioridade.temProcessosEmEspera();
    }

    /**
     * Envia um processo para a fila de espera de I/O da fila indicada.
     *
     * DECISÃO DE PROJETO: o parâmetro "fila" (FilaMLQ) é passado explicitamente
     * para que o processo retorne à MESMA fila após o I/O. Um processo de alta
     * prioridade que solicita I/O não "cai" para a Fila 2 quando termina o I/O:
     * ele sempre retorna à sua fila de origem (Fila 1 ou Fila 2).
     *
     * FilaMLQ.adicionarAhFilaDeEmEspera() remove o processo de processosProntos
     * (se ainda estiver lá), chama p.alterarEstado(EM_ESPERA) que inicializa
     * o contador tempoDeEspera = TEMPO_DE_IO (5), e adiciona à lista de em espera.
     *
     * @param p    processo que solicitou I/O.
     * @param fila fila de origem do processo (Fila 1 ou Fila 2).
     */
    private static void colocarProcessoEmEspera(Processo p, FilaMLQ fila) {
        fila.adicionarAhFilaDeEmEspera(p);
    }

    /**
     * Reinsere um processo no FIM da fila de prontos indicada.
     *
     * Usado em dois contextos:
     *   1. Round-Robin (Fila 1): processo esgotou o quantum sem terminar.
     *      Volta ao fim da Fila 1 para a próxima "volta" do rodízio.
     *
     *   2. Preempção pela Fila 1 (Fila 2): processo da Fila 2 foi interrompido
     *      porque um processo de alta prioridade chegou. Volta ao fim da Fila 2
     *      para retomar depois que a Fila 1 esvaziar novamente.
     *
     * O processo não perde progresso: turnaround continua acumulado,
     * e tempoRestante() refletirá corretamente o quanto ainda falta.
     *
     * FilaMLQ.mandarParaFinalDaFilaDePronto() adiciona ao fim de processosProntos
     * e chama p.alterarEstado(PRONTO).
     *
     * @param p    processo a ser reinserido.
     * @param fila fila de destino (mesma de origem, para preservar a hierarquia).
     */
    private static void mandarParaFinalDaFilaDePronto(Processo p, FilaMLQ fila) {
        fila.mandarParaFinalDaFilaDePronto(p);
    }

    /**
     * Avança o contador de I/O de TODOS os processos em espera, em ambas as filas.
     *
     * FilaMLQ.esperar() itera internamente sobre sua lista de em espera,
     * chamando Processo.esperar() em cada um. Processo.esperar() decrementa
     * tempoDeEspera e, ao chegar a zero, altera o estado para PRONTO.
     * FilaMLQ detecta essa mudança e move o processo para processosProntos.
     *
     * DECISÃO DE PROJETO: chamamos esperar() para AMBAS as filas a cada ciclo
     * de CPU, independentemente de qual fila está executando no momento.
     * Isso é necessário porque um processo de alta prioridade pode concluir
     * seu I/O enquanto a CPU está executando um processo da Fila 2, e ele
     * precisa retornar imediatamente para filaMaiorPrioridade para que a
     * preempção seja detectada no ciclo seguinte.
     */
    private static void esperar() {
        filaMaiorPrioridade.esperar();
        filaMenorPrioridade.esperar();
    }

    /**
     * Move um processo para a fila de prontos correta, roteando pela prioridade.
     *
     * REGRA DE ROTEAMENTO:
     *   prioridade == 1 → filaMaiorPrioridade (Round-Robin)
     *   prioridade == 2 → filaMenorPrioridade (FCFS)
     *   outro valor     → exceção (valor inválido no arquivo de entrada)
     *
     * FilaMLQ.adicionarAhFilaDePronto() adiciona o processo à lista de prontos
     * da fila, chama p.alterarEstado(PRONTO) e o remove de processosEmEspera
     * caso ele esteja retornando de I/O.
     *
     * DECISÃO DE PROJETO: centralizar o roteamento aqui garante que a regra
     * "prioridade define a fila" seja aplicada de forma uniforme tanto para
     * processos que chegam do arquivo quanto para processos que retornam de I/O
     * (neste caso, FilaMLQ já os devolve à fila correta internamente via esperar()).
     *
     * @param p processo a ser roteado para a fila correspondente à sua prioridade.
     * @throws IllegalArgumentException se a prioridade do processo não for 1 ou 2.
     */
    private static void definirProcessoComoPronto(Processo p) {
        switch (p.getPrioridade()) {
            case 1 -> filaMaiorPrioridade.adicionarAhFilaDePronto(p);
            case 2 -> filaMenorPrioridade.adicionarAhFilaDePronto(p);
            default -> throw new IllegalArgumentException(
                    "Prioridade inválida para PID " + p.getPid() + ": " + p.getPrioridade());
        }
    }
}