package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * ============================================================
 * ROUND-ROBIN COM QUANTUM POR PREDIÇÃO (Média Exponencial)
 * ============================================================
 *
 * CONCEITO DO ALGORITMO:
 * O Round-Robin clássico distribui a CPU em fatias de tempo iguais
 * (quantum fixo) entre os processos da fila de prontos, de forma circular.
 *
 * Esta variante adapta o quantum dinamicamente: a cada troca de contexto,
 * o quantum é recalculado como o MENOR τ (tau) entre todos os processos
 * na fila de prontos. τ é a previsão do próximo surto de CPU do processo,
  * Fórmula de atualização:
 *
 *     proxima_previsao = (0.5 * ultimo_burst_real) + (0.5 * previsao_atual)
 *
 * Onde:
 *   - ultimo_burst_real  = tempo de CPU que o processo usou no último surto
 *   - previsao_atual     = previsão que foi feita para esse surto
 *   - proxima_previsao   = nova previsão para o próximo surto
 *   - previsao_inicial   = 10ms (usado quando o processo ainda não tem histórico)
 *
 * POR QUE O MENOR τ COMO QUANTUM?
 * O objetivo é garantir que processos curtos (interativos, com τ pequeno)
 * terminem seu surto sem interrupções desnecessárias, aumentando a
 * responsividade do sistema. Se o quantum for grande demais, processos
 * curtos ainda seriam cortados no meio; se for o menor τ, o processo
 * mais rápido da fila consegue terminar seu surto de uma vez.
 *
 * ESTRUTURAS DE DADOS:
 * Mesmas quatro listas dos outros algoritmos, com o mesmo significado.
 * A diferença está em como os processos saem de processosProntos:
 * sempre o PRIMEIRO (FIFO circular), mas o quantum muda a cada seleção.
 *
 * - processos:             processos aguardando chegada (do arquivo).
 * - processosProntos:      fila circular; saem pelo início, entram pelo fim.
 * - processosEmEspera:     bloqueados por I/O (5 unidades de espera).
 * - processosFinalizados:  encerrados; usados para calcular métricas.
 * - tempo:                 relógio global da simulação.
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


    // =========================================================================
    // API PÚBLICA
    // =========================================================================

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
     *
     * CORREÇÃO: em vez de colocar apenas o primeiro processo na fila,
     * delegamos para verificarChegadas(), que usa um loop while e adiciona
     * TODOS os processos cujo tempo de chegada <= tempo atual (t=0).
     * Isso corrige o bug em que processos com chegada=0 entravam na fila
     * um por ciclo em vez de todos simultaneamente.
     *
     * verificarChegadas() também inicializa τ = TAU_0 para cada processo
     * adicionado, garantindo que menorTau() funcione corretamente desde
     * a primeira iteração.
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
     * DIFERENÇAS em relação ao FCFS e SRTF:
     *
     * 1. QUANTUM DINÂMICO: calculado antes de cada execução como o menor τ
     *    da fila de prontos (arredondado, mínimo 1).
     *
     * 2. FILA CIRCULAR: o processo sai do início da fila, executa até o
     *    quantum, e se não terminar, volta ao FIM da fila (não ao início).
     *    Isso garante que todos os processos recebam CPU de forma justa.
     *
     * 3. ATUALIZAÇÃO DE τ: ao final de cada surto (por fim, I/O ou quantum),
     *    atualizamos τ com a fórmula de média exponencial.
     *
     * 4. CONTROLE DE SURTO: além do turnaround global, rastreamos
     *    burstAtual — o número de ciclos de CPU executados no surto corrente.
     *    Esse valor é t_n na fórmula de atualização do τ.
     *
     * CENÁRIO 1 — CPU ociosa (igual aos outros algoritmos):
     *   Todos os processos estão em I/O. O simulador aguarda.
     *
     * CENÁRIO 2 — Há processo pronto:
     *   a) Calcula o quantum = menor τ arredondado (mínimo 1).
     *   b) Retira o primeiro processo da fila de prontos.
     *   c) Executa até o quantum, ou até I/O, ou até terminar.
     *   d) Atualiza τ e decide: finalizar, colocar em I/O, ou voltar à fila.
     *
     * @return tempo total de execução da simulação.
     */
    private static int executarProcessos() {

        // Loop externo: enquanto houver processo pronto ou em I/O.
        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {

            // ── CENÁRIO 1: CPU ociosa ─────────────────────────────────────────
            // Todos os processos estão em I/O. Avança o tempo unidade por unidade
            // até que algum conclua o I/O e retorne para a fila de prontos.
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                esperar();
                tempo++;
                verificarChegadas();
            }

            // ── Calcula o quantum desta rodada ────────────────────────────────
            // O quantum é o menor τ entre os processos na fila de prontos,
            // arredondado para o inteiro mais próximo (Math.round),
            // com mínimo de 1 para garantir que pelo menos 1 ciclo seja executado.
            int quantum = (int) Math.max(1, Math.round(menorTau()));

            // Retira o processo da FRENTE da fila (FIFO circular).
            Processo exec = executaPrimeiroDaLista();

            // Contador de ciclos executados neste quantum.
            // Quando ciclosNoQuantum == quantum, o processo esgotou sua fatia de tempo.
            int ciclosNoQuantum = 0;

            // ── Loop interno: executa até quantum, I/O ou término ─────────────
            while (exec.tempoRestante() > 0 && ciclosNoQuantum < quantum) {

                // Executa 1 ciclo de CPU: turnaround++ internamente no processo.
                exec.executarProcesso();

                // Registra 1 ciclo no surto atual do processo.
                // burstAtual é o t_n da fórmula: duração real do surto corrente.
                exec.incrementarBurstAtual();

                ciclosNoQuantum++;
                tempo++;
                esperar();
                verificarChegadas();

                // ── Verificação de I/O ────────────────────────────────────────
                // Processo.getInstantesIO() retorna null se não há mais I/Os.
                // Processo.proximoTempoDeIO() retorna o índice atual no array
                // de instantes, indicando qual é o próximo a ser verificado.
                // Processo.getTurnaround() retorna quantos ciclos de CPU o
                // processo já consumiu ao total.
                if (exec.getInstantesIO() != null) {
                    int proxIO = exec.proximoTempoDeIO();
                    if (exec.getTurnaround() == exec.getInstantesIO()[proxIO]) {

                        // Avança o índice para o próximo I/O no array.
                        exec.definirProximoIO(proxIO + 1);

                        // CORREÇÃO: atualiza τ ANTES de bloquear o processo.
                        // O surto corrente (burstAtual) encerrou aqui por I/O,
                        // então registramos sua duração real na previsão.
                        // atualizarTau() também reseta burstAtual para o próximo surto.
                        // A chamada a aumentarTempoTotalDeExecucao() foi REMOVIDA:
                        // o mecanismo de EM_ESPERA já segura o processo por 5 unidades
                        // automaticamente — somar +5 ao burst causava dupla contagem.
                        exec.atualizarTau(ALPHA);

                        colocarProcessoEmEspera(exec);

                        // Sinaliza que o processo saiu por I/O para o bloco pós-loop
                        // não tentar finalizá-lo ou recolocá-lo na fila.
                        exec = null;
                        break;
                    }
                }
            }

            // ── Pós-loop ──────────────────────────────────────────────────────
            // exec == null significa que o processo foi para I/O — já tratado acima.
            if (exec == null) continue;

            if (exec.tempoRestante() <= 0) {
                // O processo terminou dentro do quantum.
                // Atualiza τ com o surto final e registra a finalização.
                exec.atualizarTau(ALPHA);
                finalizarProcesso(exec, tempo);

            } else {
                // O processo esgotou o quantum mas ainda tem tempo restante.
                // Atualiza τ com o surto executado neste quantum e devolve
                // o processo ao FIM da fila de prontos (comportamento circular).
                exec.atualizarTau(ALPHA);
                exec.alterarEstado(EEstadoProcesso.PRONTO);
                processosProntos.add(exec);
            }
        }

        return tempo;
    }


    // =========================================================================
    // MÉTODOS AUXILIARES (HELPERS)
    // =========================================================================

    /**
     * Percorre a fila de prontos e retorna o menor valor de τ encontrado.
     *
     * Este valor define o quantum para a próxima execução.
     * A ideia é que o quantum respeite o processo mais rápido da fila,
     * evitando que ele seja interrompido no meio do seu surto previsto.
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
     * CORREÇÃO: substituído if por while para processar todos os processos
     * com chegada <= tempo atual em uma única chamada. Sem isso, quando
     * vários processos chegavam no mesmo instante (ex: chegada=0), apenas
     * um entrava por ciclo, distorcendo o escalonamento.
     *
     * INICIALIZAÇÃO DE τ: obrigatória aqui porque processos que chegam
     * durante a simulação ainda não têm histórico de CPU. Sem inicializar,
     * getTau() retorna 0.0 (valor padrão de double em Java), e menorTau()
     * calcularia um quantum de 0, travando a simulação.
     */
    private static void verificarChegadas() {
        while (!processos.isEmpty()) {
            Processo novo = processos.getFirst();
            if (novo.getChegada() <= tempo) {
                novo.inicializarTau(TAU_0); // processo novo sem histórico → τ₀ = 10
                definirProcessoComoPronto(novo);
                processos.remove(novo);
            } else {
                break; // lista ordenada por chegada: se este não chegou, nenhum chegou
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
     * Nota: usamos índice inteiro em vez de for-each para evitar
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