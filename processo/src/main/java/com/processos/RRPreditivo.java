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
 * calculada pela fórmula de Média Exponencial:
 *
 *     τ_{n+1} = α * t_n + (1 − α) * τ_n
 *
 * Onde:
 *   - t_n  = duração real do último surto de CPU do processo
 *   - τ_n  = previsão anterior
 *   - α    = 0.5 (peso equilibrado entre passado e presente)
 *   - τ₀   = 10.0 (previsão inicial para processos sem histórico)
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

    // Constante de peso da média exponencial (α = 0.5, conforme enunciado).
    // Valor 0.5 dá peso igual ao histórico passado e ao surto mais recente.
    private static final double ALPHA = 0.5;

    // Previsão inicial (τ₀ = 10ms, conforme enunciado).
    // Todo processo novo começa com esta previsão, pois ainda não tem histórico.
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
     * 2. lerProcessos()       → lê o arquivo e inicializa τ do primeiro processo.
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
     * Lê os processos do arquivo e prepara o primeiro para execução.
     *
     * Diferença em relação ao FCFS/SRTF:
     * antes de colocar o primeiro processo na fila de prontos, inicializamos
     * seu τ com TAU_0 (10.0). Isso é obrigatório porque menorTau() será
     * chamado logo em seguida e cada processo precisa ter um τ válido.
     */
    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        Processo primeiro = processos.getFirst();

        // Inicializa a previsão inicial do processo com τ₀ = 10.
        // Sem isso, getTau() retornaria 0.0 (valor padrão de double em Java),
        // e o quantum seria calculado incorretamente na primeira iteração.
        primeiro.inicializarTau(TAU_0);

        definirProcessoComoPronto(primeiro);
        processos.remove(primeiro);
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
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                esperar();
                tempo++;
                verificarChegadas();
            }

            // ── Calcula o quantum desta rodada ────────────────────────────────
            // O quantum é o menor τ entre os processos na fila de prontos,
            // arredondado para o inteiro mais próximo (Math.round),
            // com mínimo de 1 para garantir que pelo menos 1 ciclo seja executado.
            // Math.max(1, ...) evita quantum = 0 caso τ seja muito pequeno.
            int quantum = (int) Math.max(1, Math.round(menorTau()));

            // Retira o processo da FRENTE da fila (ordem de chegada na fila).
            // No Round-Robin, a ordem de acesso é FIFO dentro de cada "volta".
            Processo exec = executaPrimeiroDaLista();

            System.out.printf("[RR] t=%d | PID=%d | quantum=%d | τ=%.2f | restante=%d%n",
                    tempo, exec.getPid(), quantum, exec.getTau(), exec.tempoRestante());

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
                if (exec.getInstantesIO() != null) {
                    int proxIO = exec.proximoTempoDeIO();

                    if (exec.getTurnaround() == exec.getInstantesIO()[proxIO]) {
                        exec.definirProximoIO(proxIO + 1);
                        exec.aumentarTempoTotalDeExecucao();

                        // Atualiza τ ANTES de ir para I/O.
                        // O surto corrente (burstAtual) terminou aqui por I/O.
                        // τ_{n+1} = 0.5 * burstAtual + 0.5 * τ_atual.
                        // atualizarTau() também reseta burstAtual para o próximo surto.
                        exec.atualizarTau(ALPHA);

                        colocarProcessoEmEspera(exec);

                        // Sinaliza que o processo saiu por I/O (null = não precisa
                        // de tratamento pós-loop para este processo).
                        exec = null;
                        break;
                    }
                }
            }

            // Se exec é null, o processo foi para I/O — vai para a próxima iteração.
            if (exec == null) continue;

            // ── Pós-loop: processo terminou ou esgotou o quantum ──────────────

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
                processosProntos.add(exec); // fim da fila → próxima "volta"

                System.out.printf("[RR] PID=%d esgotou quantum, volta à fila. Novo τ=%.2f%n",
                        exec.getPid(), exec.getTau());
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
     * Verifica se algum processo do arquivo chegou no tempo atual
     * e, em caso positivo, inicializa seu τ e o move para prontos.
     *
     * Inicializar τ = TAU_0 aqui é essencial: processos que chegam durante
     * a simulação ainda não têm histórico de CPU, então começam com a
     * previsão padrão de 10 unidades de tempo.
     */
    private static void verificarChegadas() {
    while (!processos.isEmpty()) {
        Processo novo = processos.getFirst();
        if (novo.getChegada() <= tempo) {
            definirProcessoComoPronto(novo);
            processos.remove(novo);
        } else {
            break; // lista está ordenada por chegada; nenhum outro chegou ainda
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