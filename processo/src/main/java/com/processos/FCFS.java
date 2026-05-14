package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * ============================================================
 * FCFS — First-Come, First-Served (Primeiro a Chegar, Primeiro a Ser Atendido)
 * ============================================================
 *
 * CONCEITO DO ALGORITMO:
 * O FCFS é o algoritmo de escalonamento mais simples que existe.
 * A ideia central é: o primeiro processo que chega na fila de prontos
 * é o primeiro a receber a CPU, sem nenhuma interrupção até terminar
 * (ou ir para I/O).
 *
 * É um algoritmo NÃO-PREEMPTIVO: uma vez que um processo começa a
 * executar, ele não é retirado da CPU por outro processo — apenas
 * a própria solicitação de I/O pode tirá-lo temporariamente.
 *
 * VANTAGEM: simples de implementar e sem overhead de troca de contexto.
 * DESVANTAGEM: processos longos bloqueiam processos curtos que chegam
 * depois (conhecido como "efeito comboio").
 *
 * ESTRUTURAS DE DADOS UTILIZADAS:
 * - processos:          lista de todos os processos lidos do arquivo,
 *                       ordenados por tempo de chegada. Funciona como
 *                       uma fila de chegada externa ao escalonador.
 * - processosProntos:   lista dos processos que já chegaram e estão
 *                       aguardando a CPU. A ordem de inserção define
 *                       a ordem de execução (FIFO).
 * - processosEmEspera:  lista dos processos que solicitaram I/O e
 *                       estão aguardando o dispositivo responder.
 *                       Cada processo espera exatamente TEMPO_DE_IO = 5
 *                       unidades de tempo antes de voltar à fila de prontos.
 * - processosFinalizados: lista de processos que já terminaram. Usada
 *                         exclusivamente para calcular as métricas ao final.
 * - tempo:              relógio global da simulação. Avança 1 unidade
 *                       por ciclo de CPU executado.
 */
public class FCFS {

    // Lista com todos os processos ainda não chegaram ao escalonador.
    // O arquivo processos.txt é lido em ordem, então esta lista já vem
    // ordenada por tempo de chegada.
    private static List<Processo> processos = new ArrayList<>();

    // Fila de prontos: processos que já chegaram e aguardam a CPU.
    // No FCFS, o primeiro da lista é sempre o próximo a executar.
    private static List<Processo> processosProntos = new ArrayList<>();

    // Fila de espera de I/O: processos bloqueados aguardando dispositivo.
    // Cada processo permanece aqui por 5 unidades de tempo (TEMPO_DE_IO),
    // definido na classe Processo como constante.
    private static List<Processo> processosEmEspera = new ArrayList<>();

    // Lista de processos que finalizaram completamente.
    // Passada para a classe Metricas ao final para calcular os resultados.
    private static List<Processo> processosFinalizados = new ArrayList<>();

    // Relógio global da simulação: representa o tempo atual do sistema.
    private static int tempo = 0;


    // =========================================================================
    // API PÚBLICA
    // =========================================================================

    /**
     * Ponto de entrada único da simulação FCFS.
     *
     * Sequência de operações:
     * 1. resetar()         → limpa todas as listas e zera o relógio,
     *                        garantindo que rodadas anteriores não interfiram.
     * 2. lerProcessos()    → lê o arquivo processos.txt via LeitorDeProcessos
     *                        e coloca o primeiro processo na fila de prontos.
     * 3. executarProcessos() → roda o loop principal da simulação.
     * 4. new Metricas(...)  → cria o objeto de métricas com a lista de
     *                          processos finalizados e o tempo total.
     * 5. m.imprimir()       → exibe Turnaround Médio, Espera Média e Throughput.
     * 6. return m           → devolve o objeto para o Main comparar os algoritmos.
     *
     * @return objeto Metricas com os resultados desta simulação.
     */
    public static Metricas iniciarSimulacao() {
        resetar();
        lerProcessos();
        tempo = executarProcessos();

        // A classe Metricas recebe: nome do algoritmo, lista de processos
        // finalizados (que contêm o instante de fim gravado durante a execução)
        // e o tempo total. Com esses dados ela calcula turnaround, espera e throughput.
        Metricas m = new Metricas("FCFS", processosFinalizados, tempo);
        m.imprimir();
        return m;
    }


    // =========================================================================
    // INICIALIZAÇÃO
    // =========================================================================

    /**
     * Limpa o estado interno para permitir múltiplas execuções na mesma JVM.
     *
     * Como todos os campos são static, eles persistem entre chamadas.
     * Sem este método, uma segunda chamada a iniciarSimulacao() acumularia
     * os dados da rodada anterior, gerando métricas incorretas.
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
     * LeitorDeProcessos.criarProcessos() lê o arquivo processos.txt e
     * retorna um array de objetos Processo, um por linha do arquivo.
     * Cada Processo armazena: PID, tempo de chegada, burst total de CPU,
     * prioridade e (opcionalmente) os instantes de I/O.
     *
     * Decisão de projeto: apenas o primeiro processo é colocado na fila
     * de prontos aqui. Todos os outros chegam durante a simulação, verificados
     * a cada ciclo por verificarChegadas(). Isso garante que o tempo de
     * chegada de cada processo seja respeitado.
     */
    private static void lerProcessos() {
    processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
    verificarChegadas(); // adiciona TODOS os que chegaram em t=0 de uma vez
}


    // =========================================================================
    // LOOP PRINCIPAL DA SIMULAÇÃO
    // =========================================================================

    /**
     * Coração do algoritmo FCFS.
     *
     * O loop externo continua enquanto houver algum processo para processar,
     * seja na fila de prontos ou aguardando I/O.
     *
     * Dentro dele, dois cenários principais:
     *
     * CENÁRIO 1 — CPU ociosa:
     *   Todos os processos estão em I/O e nenhum está pronto para executar.
     *   O simulador avança o tempo unidade por unidade, decrementando o
     *   contador de espera de cada processo em processosEmEspera, até que
     *   algum processo conclua o I/O e retorne à fila de prontos.
     *
     * CENÁRIO 2 — Há processo pronto:
     *   Seleciona o primeiro da fila (FIFO), executa ciclo a ciclo até que
     *   termine ou solicite I/O, verificando a cada ciclo se novos processos
     *   chegaram e se o processo atingiu um instante de I/O.
     *
     * @return tempo total de execução da simulação.
     */
    private static int executarProcessos() {

        // Loop externo: continua enquanto há trabalho pendente (prontos ou em I/O).
        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {

            // ── CENÁRIO 1: CPU ociosa ─────────────────────────────────────────
            // Se não há processo pronto mas há processos em I/O, o sistema
            // precisa esperar. A cada iteração:
            //   - esperar() decrementa o contador de I/O de cada processo em espera;
            //   - se algum processo zera o contador, ele volta para processosProntos;
            //   - verificarChegadas() verifica se novos processos chegaram;
            //   - tempo++ avança o relógio.
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                esperar();
                tempo++;
                verificarChegadas();
            }

            // ── CENÁRIO 2: Executa o primeiro processo da fila ────────────────
            // No FCFS a escolha é sempre o primeiro da fila — sem critério de
            // prioridade, sem comparação de tempo restante. Quem chegou primeiro
            // executa primeiro.
            Processo exec = executaPrimeiroDaLista();

            // Captura os instantes de I/O e o tempo total de execução do processo.
            // "tempoTotalDeExecucao" começa igual ao burstTotal, mas aumenta em
            // TEMPO_DE_IO (5 unidades) a cada vez que o processo faz I/O, pois
            // o tempo de espera não conta como CPU mas o processo ainda precisa
            // voltar e continuar executando.
            int[] instantesIO  = exec.getInstantesIO();
            int tempoTotalExec = exec.tempoTotalDeExecucao();

            // Loop interno: executa o processo ciclo a ciclo até ele terminar
            // ou ser bloqueado por I/O (break).
            while (exec.getTurnaround() < tempoTotalExec) {

                // executarProcesso() incrementa o contador interno "turnaround"
                // do processo, representando mais 1 unidade de CPU consumida.
                exec.executarProcesso();
                tempo++;

                // A cada ciclo, decrementa o contador de espera dos processos em I/O
                // e verifica se algum deles ficou pronto para retornar à fila.
                esperar();

                // Verifica se algum processo do arquivo chegou neste instante.
                // Isso é necessário mesmo no FCFS: embora não haja preempção,
                // processos podem chegar enquanto outro executa e devem entrar
                // na fila de prontos para executar depois.
                verificarChegadas();

                // ── Verificação de I/O ────────────────────────────────────────
                // Se o processo possui instantes de I/O definidos, verifica se
                // o turnaround atual (tempo acumulado de CPU) atingiu o próximo
                // instante de I/O.
                if (instantesIO != null) {
                    int proxIO = exec.proximoTempoDeIO(); // índice no array instantesIO[]

                    if (exec.getTurnaround() == instantesIO[proxIO]) {
                        // O processo atingiu um instante de I/O:
                        // 1. Avança o índice para o próximo I/O no array.
                        exec.definirProximoIO(proxIO + 1);
                        // exec.aumentarTempoTotalDeExecucao(); ← REMOVER esta linha
                        colocarProcessoEmEspera(exec);
                        exec = null;
                        break;
                    }
                }
            }

            // Se o processo ainda está no estado EXECUTANDO após o loop interno,
            // significa que ele terminou normalmente (não saiu por I/O).
            // Registramos o instante de fim para cálculo das métricas.
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
     * Verifica se algum processo do arquivo deve entrar na fila de prontos.
     *
     * O arquivo de processos está ordenado por tempo de chegada. Por isso,
     * basta verificar sempre o primeiro elemento da lista: se ele chegou
     * (chegada <= tempo atual), é movido para prontos e removido da lista.
     *
     * Observação: verificamos apenas o primeiro porque a lista está ordenada.
     * Se o primeiro ainda não chegou, nenhum outro chegou também.
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
     * Finaliza um processo: marca como FINALIZADO, registra o instante de fim
     * e o adiciona à lista de finalizados para cálculo de métricas.
     *
     * O "instanteDeFim" é essencial para calcular:
     *   - Turnaround real = instanteDeFim - chegada
     *   - Tempo de espera = turnaroundReal - burstTotal (CPU puro)
     *
     * @param p       processo que terminou.
     * @param tempoFim instante do relógio em que o processo concluiu.
     */
    private static void finalizarProcesso(Processo p, int tempoFim) {
        p.finalizarProcesso();        // altera estado para FINALIZADO
        p.setInstanteDeFim(tempoFim); // grava o instante para as métricas
        processosFinalizados.add(p);  // registra na lista de finalizados
    }

    /**
     * Retorna true se há algum processo aguardando CPU na fila de prontos.
     */
    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    /**
     * Move um processo para a fila de espera de I/O.
     *
     * Internamente, Processo.colocarEmEspera() altera o estado para EM_ESPERA
     * e inicializa o contador tempoDeEspera = TEMPO_DE_IO (5 unidades).
     * A cada chamada de esperar(), esse contador é decrementado.
     * Quando chega a zero, o processo volta ao estado PRONTO.
     *
     * @param p processo que solicitou I/O.
     */
    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        // Garante que o processo não fique em prontos enquanto está em I/O.
        processosProntos.remove(p);
    }

    /**
     * Move um processo para a fila de prontos e atualiza seu estado.
     *
     * Usado tanto na chegada de novos processos quanto no retorno de I/O.
     * Garante que o processo seja removido de processosEmEspera se estiver lá,
     * evitando duplicatas entre as listas.
     *
     * @param p processo a ser marcado como PRONTO.
     */
    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosEmEspera.remove(p); // remove de espera se veio de I/O
    }

    /**
     * Retira o primeiro processo da fila de prontos e o coloca em execução.
     *
     * No FCFS, "primeiro" significa o que chegou há mais tempo na fila
     * (índice 0 da lista). removeFirst() remove e retorna em uma operação,
     * garantindo consistência.
     *
     * @return o processo que agora está executando.
     */
    private static Processo executaPrimeiroDaLista() {
        Processo p = processosProntos.removeFirst();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        return p;
    }

    /**
     * Avança o contador de I/O de todos os processos em espera.
     *
     * A cada chamada, Processo.esperar() decrementa tempoDeEspera.
     * Quando tempoDeEspera chega a zero, esperar() altera o estado
     * do processo para PRONTO automaticamente.
     *
     * Aqui verificamos esse estado: se o processo ficou PRONTO,
     * o movemos imediatamente para processosProntos.
     *
     * Nota: usamos índice em vez de for-each para evitar
     * ConcurrentModificationException, já que definirProcessoComoPronto()
     * modifica processosEmEspera durante a iteração.
     */
    private static void esperar() {
        for (int i = 0; i < processosEmEspera.size(); i++) {
            Processo p = processosEmEspera.get(i);
            p.esperar(); // decrementa o contador de I/O interno do processo
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                // O processo concluiu o I/O e está pronto para executar novamente.
                definirProcessoComoPronto(p);
            }
        }
    }
}