package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * ============================================================
 * ROUND-ROBIN COM QUANTUM POR PREDIÇÃO (Média Exponencial)
 * ============================================================
 *
 * Algoritmo que adapta dinamicamente o quantum (fatia de tempo)
 * usando média exponencial para prever a duração dos surtos de CPU.
 *
 * Fórmula: τ(n+1) = α·t(n) + (1-α)·τ(n)
 * Com α = 0.5 e τ₀ = 10
 */
public class RRPreditivo {

    // Peso da média exponencial: 0.5 significa que passado e presente
    // têm o mesmo peso na previsão do próximo surto.
    private static final double ALPHA = 0.5;

    // Previsão usada para processos novos, que ainda não têm histórico de CPU.
    private static final double TAU_0 = 10.0;

    // Processos aguardando chegada (ainda não estão no escalonador).
    private static List<Processo> processos          = new ArrayList<>();

    // Fila circular de prontos. No Round-Robin, o processo entra pelo fim
    // e sai pelo início. Quando esgota o quantum sem terminar, volta ao fim.
    private static List<Processo> processosProntos   = new ArrayList<>();

    // Processos bloqueados por I/O; permanecem aqui por 5 unidades de tempo.
    private static List<Processo> processosEmEspera  = new ArrayList<>();

    // Processos encerrados; passados para Metricas ao final da simulação.
    private static List<Processo> processosFinalizados = new ArrayList<>();

    // Relógio global: incrementado a cada ciclo de CPU executado.
    private static int tempo = 0;

    /**
     * Ponto de entrada único da simulação Round-Robin Preditivo.
     *
     * 1. resetar()            → limpa estado entre execuções.
     * 2. lerProcessos()       → lê o arquivo e inicializa τ de todos os
     *                           processos que chegam em t=0.
     * 3. executarProcessos()  → loop principal com lógica RR + média exponencial.
     * 4. Metricas             → calcula e exibe turnaround, espera e throughput.
     *
     * @return objeto Metricas com os resultados desta simulação.
     */
    public static Metricas iniciarSimulacao() {
        resetar();
        lerProcessos();
        tempo = executarProcessos();
        Metricas m = new Metricas("RR-Preditivo", processosFinalizados, tempo);
        m.imprimir();
        return m;
    }


    // =========================================================================
    // INICIALIZAÇÃO
    // =========================================================================

    /**
     * Limpa todas as listas e reinicia o relógio.
     * Necessário porque os campos são static e persistem entre chamadas.
     */
    private static void resetar() {
        processos.clear();
        processosProntos.clear();
        processosEmEspera.clear();
        processosFinalizados.clear();
        tempo = 0;
    }

    /**
     * Lê todos os processos do arquivo e inicializa a simulação.
     */
    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        verificarChegadas();
    }


    // =========================================================================
    // LOOP PRINCIPAL DA SIMULAÇÃO
    // =========================================================================

    /**
     * Coração do algoritmo Round-Robin Preditivo.
     *
     * ESTRUTURA PRINCIPAL:
     *
     * 1. Loop externo continua enquanto há processos prontos ou em I/O
     * 2. Loop de CPU ociosa: aguarda enquanto há I/O mas nenhum pronto
     * 3. GUARDA if: só processa se há processos prontos
     * 4. Calcula quantum dinâmico = menor τ da fila
     * 5. Retira processo, executa até quantum/I/O/fim
     * 6. Atualiza τ e reinsere na fila ou finaliza
     *
     * A refatoração elimina o 'continue' usando guard clause (if guardando
     * todo o bloco de processamento). Isso melhora legibilidade e mantém
     * fluxo linear sem saltos imperceptíveis.
     *
     * @return tempo total de execução da simulação.
     */
    private static int executarProcessos() {

        // Loop externo: continua enquanto há processos pendentes (prontos ou em I/O)
        while (temProcessosProntos() || temProcessosEmEspera()) {

            // ── BLOCO 1: CPU Ociosa ───────────────────────────────────────
            // Situação: não há processos prontos, mas há em I/O.
            // Aguardamos que algum I/O termine e retorne à fila de prontos.
            while (temProcessosEmEspera() && !temProcessosProntos()) {
                esperar();
                tempo++;
                verificarChegadas();
            }

            // ── BLOCO 2: Processamento (Guard Clause) ──────────────────────
            // Só processamos se há processos prontos. Isto evita:
            // - Chamar menorTau() com fila vazia
            // - Chamar executaPrimeiroDaLista() com fila vazia
            // - Usar 'continue' (fluxo não-linear)
            if (temProcessosProntos()) {

                // Calcula o quantum dinâmico para esta iteração
                // quantum = menor τ (tempo previsto) da fila
                int quantum = (int) Math.max(1, Math.round(menorTau()));

                // Retira o primeiro processo da FRENTE da fila (FIFO circular)
                Processo exec = executaPrimeiroDaLista();

                // Contador de ciclos executados neste quantum
                int ciclosNoQuantum = 0;

                // ── Loop interno: executa ciclos até quantum, I/O ou fim ────
                while (exec.tempoRestante() > 0 && ciclosNoQuantum < quantum) {

                    // Executa 1 ciclo de CPU
                    exec.executarProcesso();

                    // Registra o ciclo no surto atual (t_n da fórmula de atualização)
                    exec.incrementarBurstAtual();

                    ciclosNoQuantum++;
                    tempo++;

                    // Avança o relógio de I/O de processos em espera
                    esperar();

                    // Verifica se novos processos chegaram neste instante
                    verificarChegadas();

                    // ── Verificação de I/O ────────────────────────────────
                    // Se o processo solicita I/O neste ciclo...
                    if (exec.getInstantesIO() != null) {
                        int proxIO = exec.proximoTempoDeIO();
                        if (exec.getTempoDeProcessador() == exec.getInstantesIO()[proxIO]) {

                            // Avança para o próximo instante de I/O
                            exec.definirProximoIO(proxIO + 1);

                            // Atualiza τ com a fórmula de média exponencial
                            // ANTES de colocar em espera (surto termina por I/O)
                            exec.atualizarTau(ALPHA);

                            // Coloca o processo na fila de espera de I/O
                            colocarProcessoEmEspera(exec);

                            // Sinaliza que o processo foi tratado
                            exec = null;

                            // Sai do loop interno; voltará ao loop externo
                            // quando algum I/O terminar
                            break;
                        }
                    }
                }

                // ── Pós-loop: trata os dois casos possíveis ────────────────
                // Executamos este bloco APENAS se exec != null
                // (ou seja, o processo NÃO foi para I/O)
                if (exec != null) {

                    // CASO 1: Processo terminou (consumiu todo seu burst)
                    if (exec.tempoRestante() <= 0) {
                        // Atualiza τ com o surto final
                        exec.atualizarTau(ALPHA);
                        // Registra a finalização com o instante atual
                        finalizarProcesso(exec, tempo);
                    }
                    // CASO 2: Processo esgotou o quantum mas ainda tem tempo
                    else {
                        // Atualiza τ com o surto executado neste quantum
                        exec.atualizarTau(ALPHA);
                        // Marca como PRONTO
                        exec.alterarEstado(EEstadoProcesso.PRONTO);
                        // Devolve ao FIM da fila para próximo rodízio
                        processosProntos.add(exec);
                    }
                }

            }  // ← Fecha o if (temProcessosProntos())
        }

        return tempo;
    }


    // =========================================================================
    // MÉTODOS AUXILIARES (HELPERS)
    // =========================================================================

    /**
     * Verifica se há processo em espera de I/O.
     */
    private static boolean temProcessosEmEspera() {
        return !processosEmEspera.isEmpty();
    }

    /**
     * Percorre a fila de prontos e retorna o menor valor de τ encontrado.
     *
     * Este valor define o quantum para a próxima execução.
     * A ideia é que o quantum respeite o processo mais rápido da fila,
     * evitando que ele seja interrompido no meio do seu surto previsto.
     *
     * NOTA: Só é chamada dentro de if (temProcessosProntos()), portanto
     * a fila nunca está vazia quando este método executa.
     *
     * @return o menor τ entre os processos em processosProntos.
     */
    private static double menorTau() {
        double menor = processosProntos.getFirst().getTau();
        for (Processo p : processosProntos) {
            if (p.getTau() < menor) {
                menor = p.getTau();
            }
        }
        return menor;
    }

    /**
     * Verifica se algum processo do arquivo chegou no tempo atual e o move
     * para a fila de prontos, inicializando seu τ antes de inserir.
     *
     * Usa while em vez de if para processar TODOS os processos com
     * chegada <= tempo atual em uma única chamada. Isto corrige o bug
     * onde processos com chegada=0 entravam um por ciclo.
     *
     * Inicializa τ = TAU_0 para processos novos, pois sem isso getTau()
     * retornaria 0.0 e menorTau() calcularia quantum=0, travando a simulação.
     */
    private static void verificarChegadas() {
        while (!processos.isEmpty()) {
            Processo novo = processos.getFirst();
            if (novo.getChegada() <= tempo) {
                novo.inicializarTau(TAU_0);
                definirProcessoComoPronto(novo);
                processos.remove(novo);
            } else {
                break;
            }
        }
    }

    /**
     * Registra a finalização do processo com seu instante de fim.
     * Usado pela classe Metricas para calcular turnaround e espera.
     */
    private static void finalizarProcesso(Processo p, int tempoFim) {
        p.finalizarProcesso();
        p.setInstanteDeFim(tempoFim);
        processosFinalizados.add(p);
    }

    /**
     * Retorna true se há processo aguardando CPU.
     */
    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    /**
     * Retira o primeiro processo da fila de prontos e o coloca em execução.
     *
     * No Round-Robin, a ordem é FIFO: o primeiro que entrou na fila
     * é o primeiro a executar. Quando um processo esgota o quantum,
     * ele volta ao FIM da fila (via processosProntos.add()), garantindo
     * que todos os processos se revezem de forma justa.
     *
     * NOTA: Só é chamado dentro de if (temProcessosProntos()), portanto
     * a fila nunca está vazia quando este método executa.
     */
    private static Processo executaPrimeiroDaLista() {
        Processo p = processosProntos.removeFirst();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        return p;
    }

    /**
     * Move o processo para a fila de espera de I/O.
     *
     * Processo.colocarEmEspera() altera o estado para EM_ESPERA e
     * inicializa tempoDeEspera = 5. A cada chamada de esperar(),
     * esse contador decresce 1. Ao zerar, o processo volta para PRONTO.
     */
    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        processosProntos.remove(p);
    }

    /**
     * Adiciona o processo à fila de prontos e atualiza seu estado.
     * Remove de processosEmEspera caso o processo esteja retornando de I/O.
     */
    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosEmEspera.remove(p);
    }

    /**
     * Avança o contador de I/O de todos os processos em espera.
     *
     * Processo.esperar() decrementa tempoDeEspera e, ao chegar a zero,
     * altera o estado do processo para PRONTO. Detectamos isso e
     * movemos o processo para processosProntos.
     *
     * Usa índice inteiro em vez de for-each para evitar
     * ConcurrentModificationException, já que a lista pode ser alterada
     * por definirProcessoComoPronto() durante a iteração.
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