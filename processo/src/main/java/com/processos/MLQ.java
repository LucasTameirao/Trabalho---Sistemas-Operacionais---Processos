package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * Escalonador Multilevel Queue (MLQ) com duas filas estáticas.
 *
 * Estrutura de filas:
 *   Fila 1 — Alta Prioridade  → Round-Robin com quantum fixo de 2.
 *   Fila 2 — Baixa Prioridade → FCFS (First-Come, First-Served).
 *
 * Regra fundamental: processos da Fila 2 só executam quando a Fila 1
 * está completamente vazia. Se um processo de alta prioridade chegar
 * enquanto um processo de baixa prioridade executa, o de baixa é
 * preemptado e devolvido ao final de sua fila.
 *
 * Cada fila é representada por um objeto FilaMLQ, que encapsula:
 *   - Lista de processos PRONTOS (aguardando CPU).
 *   - Lista de processos EM ESPERA (bloqueados em I/O).
 *   Isso permite que cada fila gerencie seu próprio ciclo de I/O
 *   de forma independente.
 *
 * A prioridade do processo (campo Processo.prioridade) determina
 * em qual das duas filas ele será inserido: 1 → alta, 2 → baixa.
 *
 * Esta classe é totalmente estática (sem instâncias), centralizando
 * todo o estado da simulação em atributos de classe.
 */
public class MLQ {

    // Lista com os processos ainda não chegados ao sistema, ordenada
    // por tempo de chegada. Funciona como uma "linha do tempo" de chegadas:
    // processos são removidos daqui e inseridos em suas filas conforme
    // o relógio global atinge seus tempos de chegada.
    private static List<Processo> processos = new ArrayList<>();

    // Referência ao processo atualmente em execução na CPU.
    // Mantida como atributo de classe para ser acessível nos métodos
    // auxiliares de log e controle sem precisar ser passada como parâmetro.
    private static Processo processoEmExecucao = null;

    // Relógio global da simulação. Avança 1 unidade por tick de CPU
    // executado ou por tick de espera forçada (CPU ociosa aguardando I/O).
    private static int tempo = 0;

    // Fila de Alta Prioridade — escalonada via Round-Robin (quantum = 2).
    // FilaMLQ encapsula a lista de prontos e de em-espera, além de oferecer
    // operações atômicas como proximoProcessoPronto() e adicionarAhFilaDeEmEspera().
    // Usamos FilaMLQ (e não List direta) para reaproveitar a lógica de
    // gerenciamento de I/O já implementada nessa classe.
    private static FilaMLQ filaMaiorPrioridade = new FilaMLQ();

    // Fila de Baixa Prioridade — escalonada via FCFS.
    // FCFS é naturalmente implementado por uma fila FIFO sem preempção:
    // o processo que chegou primeiro executa até completar ou ir para I/O.
    private static FilaMLQ filaMenorPrioridade = new FilaMLQ();

    /**
     * Ponto de entrada público da simulação.
     * Carrega os processos, executa o loop principal e retorna o tempo total.
     */
    public static int iniciarSimulacao() {
        System.out.println("========== INICIANDO SIMULAÇÃO MLQ ==========");
        lerProcessos();
        System.out.println("Processos carregados com sucesso. Iniciando execução...");
        tempo = executarProcessos();
        System.out.println("========== SIMULAÇÃO FINALIZADA ==========");
        System.out.println(String.format("Tempo total de execução: %d unidades", tempo));
        return tempo;
    }

    /**
     * Inicializa a simulação carregando os processos do arquivo de entrada.
     *
     * LeitorDeProcessos.criarProcessos() (utilitário externo) lê o arquivo
     * de configuração e retorna um array de objetos Processo com PID,
     * chegada, burst, prioridade e instantes de I/O já preenchidos.
     *
     * Apenas o primeiro processo é inserido na fila de prontos agora —
     * os demais serão inseridos por verificarNovosProcessos() conforme
     * o relógio alcança seus tempos de chegada.
     *
     * Pressupõe-se que os processos estão ordenados por tempo de chegada
     * (garantido pelo LeitorDeProcessos).
     */
    private static void lerProcessos() {
        System.out.println("\n[LEITURA] Iniciando leitura de processos...");
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        System.out.println(String.format("[LEITURA] Total de processos carregados: %d", processos.size()));

        // O primeiro processo entra imediatamente em sua fila de prontos.
        // definirProcessoComoPronto() usa Processo.getPrioridade() para
        // decidir entre filaMaiorPrioridade e filaMenorPrioridade.
        Processo novoProcesso = processos.getFirst();
        System.out.println(String.format("[LEITURA] Primeiro processo (ID: %d, Prioridade: %d) definido como pronto",
                novoProcesso.getPid(), novoProcesso.getPrioridade()));
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    /**
     * Loop principal da simulação MLQ.
     *
     * Estrutura geral a cada iteração do while externo:
     *   1. Se CPU está ociosa (ninguém pronto mas há processos em I/O),
     *      avança o relógio e aguarda I/O concluir.
     *   2. Drena completamente a Fila 1 (Alta Prioridade) via Round-Robin.
     *   3. Só então processa a Fila 2 (Baixa Prioridade) via FCFS,
     *      interrompendo imediatamente se a Fila 1 receber um processo.
     *
     * O loop externo repete enquanto houver processos prontos em qualquer
     * fila ou processos bloqueados em I/O (que voltarão a ficar prontos).
     *
     * @return tempo total decorrido quando todos os processos finalizarem.
     */
    private static int executarProcessos() {
        System.out.println("\n[EXECUÇÃO] Iniciando loop de execução de processos\n");

        // Variáveis declaradas fora dos loops internos para evitar
        // realocação repetida a cada iteração — boa prática em loops quentes.
        int proximoIO;
        int[] instantesIO;

        // Continua enquanto houver processos prontos (em qualquer fila)
        // ou processos bloqueados em I/O que ainda vão retornar.
        while (temProcessosProntos() || temProcessosEmEspera()) {

            // --- Tratamento de CPU ociosa (idle) ---
            // Ocorre quando todos os processos prontos foram despachados e
            // estão aguardando I/O. Avançamos o relógio tick a tick até que
            // ao menos um processo retorne da fila de espera.
            while (temProcessosEmEspera() && !temProcessosProntos()) {
                System.out.println(String.format("[EXECUÇÃO] Tempo %d: Aguardando processos I/O completarem...", tempo));
                esperar();             // decrementa contadores de I/O em ambas as filas
                tempo++;
                verificarNovosProcessos(); // verifica chegadas neste tick ocioso
            }

            // =========================================================
            // #region Round Robin — Fila de Alta Prioridade (Fila 1)
            // =========================================================
            // Drena toda a Fila 1 antes de qualquer coisa.
            // O while externo garante que, se durante a execução de um
            // processo de Fila 1 um novo processo de Fila 1 chegar via I/O
            // ou chegada externa, ele também será processado antes da Fila 2.
            while (filaMaiorPrioridade.temProcessosProntos()) {

                // Quantum fixo = 2 ticks de CPU para todos os processos de alta prioridade.
                // Escolha de projeto: quantum pequeno para garantir alta responsividade
                // e alternância rápida entre processos críticos.
                int quantum = 2;
                int tempoNoProcessador = 0; // ticks usados nesta fatia de CPU

                // Retira o processo do início da fila e marca como EXECUTANDO.
                // FilaMLQ.proximoProcessoPronto() é atômico: remove da lista
                // de prontos e muda o estado em uma única operação.
                processoEmExecucao = filaMaiorPrioridade.proximoProcessoPronto();

                System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d (Prioridade 1 - RoundRobin) escalonado com quantum=%d",
                        tempo, processoEmExecucao.getPid(), quantum));

                // Captura os instantes de I/O e o ponteiro para o próximo evento.
                // instantesIO: array com os momentos (em turnaround) de cada I/O.
                // proximoIO:   índice do próximo instante não consumido.
                proximoIO = processoEmExecucao.proximoTempoDeIO();
                instantesIO = processoEmExecucao.getInstantesIO();

                // --- Loop tick a tick do processo de alta prioridade ---
                // Continua enquanto o processo estiver no estado EXECUTANDO.
                // O estado muda para PRONTO (quantum), EM_ESPERA (I/O)
                // ou FINALIZADO — todos os casos encerram este loop com break.
                while (processoEmExecucao.estadoProcesso() == EEstadoProcesso.EXECUTANDO) {

                    // Barreira de segurança contra loop infinito em caso de bug.
                    // 10.000 ticks é um limite conservador para simulações típicas.
                    if (tempo >= 10000) {
                        throw new IllegalStateException("Tempo de execução excedeu o limite");
                    }

                    // --- Verificação de conclusão ---
                    // Checamos ANTES de executar para não ultrapassar o burst total.
                    // getTurnaround() = ticks de CPU já consumidos pelo processo.
                    // tempoTotalDeExecucao() = burst total configurado no arquivo.
                    if (processoEmExecucao.getTurnaround() >= processoEmExecucao.tempoTotalDeExecucao()) {
                        processoEmExecucao.alterarEstado(EEstadoProcesso.FINALIZADO);
                        System.out.println(String.format("[FINALIZADO] Tempo %d: Processo %d finalizou",
                                tempo, processoEmExecucao.getPid()));
                        break;
                    }

                    // --- Verificação de evento de I/O ---
                    // Se o turnaround atual coincide com o próximo instante de I/O,
                    // o processo solicita I/O e é bloqueado antes de executar mais um tick.
                    // Comparamos com instantesIO[proximoIO]: o turnaround é o "relógio
                    // interno" do processo em CPU, e o instante de I/O é definido
                    // em unidades de CPU consumidas — por isso a comparação é direta.
                    if (instantesIO != null && processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {
                        System.out.println(String.format("[I/O] Tempo %d: Processo %d iniciou I/O",
                                tempo, processoEmExecucao.getPid()));

                        // Avança o ponteiro para o próximo instante de I/O.
                        // definirProximoIO() também anula instantesIO se não houver mais eventos,
                        // evitando acessos fora dos limites do array nas próximas iterações.
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);

                        // Move o processo para a fila de espera DA SUA PRÓPRIA FILA (Alta).
                        // Quando o I/O concluir (após TEMPO_DE_IO ticks), ele retornará
                        // à fila de prontos de alta prioridade — não cai de nível.
                        colocarProcessoEmEspera(processoEmExecucao, filaMaiorPrioridade);
                        break;
                    }

                    // --- Verificação de esgotamento de quantum ---
                    // Checamos ANTES de executar o tick: se já usou `quantum` ticks,
                    // é preemptado agora sem executar mais nenhum tick extra.
                    // O processo volta ao FINAL da fila de prontos de alta prioridade,
                    // implementando a rotação circular do Round-Robin.
                    if (tempoNoProcessador == quantum) {
                        System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d completou quantum, retornando à fila",
                                tempo, processoEmExecucao.getPid()));
                        mandarParaFinalDaFilaDePronto(processoEmExecucao, filaMaiorPrioridade);
                        break;
                    }

                    // --- Execução de 1 tick de CPU ---
                    // executarProcesso() incrementa o turnaround interno do processo.
                    processoEmExecucao.executarProcesso();
                    tempoNoProcessador++;
                    tempo++;

                    // Após cada tick: avança I/O de processos bloqueados e
                    // verifica se novos processos chegaram no instante atual.
                    esperar();
                    verificarNovosProcessos();

                    System.out.println(String.format("[DEBUG] Tempo %d: Processo %d | turnaround: %d | total: %d",
                            tempo, processoEmExecucao.getPid(),
                            processoEmExecucao.getTurnaround(),
                            processoEmExecucao.tempoTotalDeExecucao()));
                }
            }
            // #endregion Round Robin

            // =========================================================
            // #region FCFS — Fila de Baixa Prioridade (Fila 2)
            // =========================================================
            // Só executamos a Fila 2 se a Fila 1 estiver completamente vazia.
            // A condição dupla do while garante que:
            //   - Há processo pronto na Fila 2 para executar.
            //   - A Fila 1 continua vazia (checado a cada tick).
            // Se durante a execução de um processo FCFS um processo de alta
            // prioridade chegar, o while interno é interrompido e o processo
            // de baixa prioridade é devolvido à sua fila.
            while (filaMenorPrioridade.temProcessosProntos() && !filaMaiorPrioridade.temProcessosProntos()) {

                // FCFS: retira o primeiro da fila (quem chegou antes executa antes).
                // FilaMLQ.proximoProcessoPronto() remove do início e muda para EXECUTANDO.
                processoEmExecucao = filaMenorPrioridade.proximoProcessoPronto();
                System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo %d (Prioridade 2 - FCFS) escalonado",
                        tempo, processoEmExecucao.getPid()));

                proximoIO = processoEmExecucao.proximoTempoDeIO();
                instantesIO = processoEmExecucao.getInstantesIO();

                // --- Loop tick a tick do processo de baixa prioridade ---
                // Condição dupla: processo ainda executando E Fila 1 vazia.
                // A verificação de filaMaiorPrioridade aqui é a implementação
                // da preempção por prioridade: a Fila 2 cede CPU imediatamente.
                while (processoEmExecucao.estadoProcesso() == EEstadoProcesso.EXECUTANDO
                        && !filaMaiorPrioridade.temProcessosProntos()) {

                    // Barreira de segurança — mesmo critério da Fila 1.
                    if (tempo >= 10000) {
                        throw new IllegalStateException(String.format("Tempo limite excedido | tempo: %d", tempo));
                    }

                    // --- Verificação de conclusão (antes de executar) ---
                    if (processoEmExecucao.getTurnaround() >= processoEmExecucao.tempoTotalDeExecucao()) {
                        processoEmExecucao.alterarEstado(EEstadoProcesso.FINALIZADO);
                        System.out.println(String.format("[FINALIZADO] Tempo %d: Processo %d finalizou",
                                tempo, processoEmExecucao.getPid()));
                        break;
                    }

                    // --- Verificação de evento de I/O ---
                    // Mesma lógica da Fila 1: compara turnaround com o instante de I/O.
                    // O processo vai para a fila de espera da sua fila (Baixa Prioridade):
                    // ao retornar, permanece na Fila 2 — não há migração entre filas no MLQ estático.
                    if (instantesIO != null && processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {
                        System.out.println(String.format("[I/O] Tempo %d: Processo %d iniciou I/O",
                                tempo, processoEmExecucao.getPid()));
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);
                        colocarProcessoEmEspera(processoEmExecucao, filaMenorPrioridade);
                        break;
                    }

                    // --- Execução de 1 tick de CPU ---
                    // FCFS não tem quantum: o processo executa sem limite de tempo
                    // (até concluir, ir para I/O ou ser preemptado por alta prioridade).
                    processoEmExecucao.executarProcesso();
                    tempo++;
                    esperar();
                    verificarNovosProcessos();

                    // --- Verificação de preempção por alta prioridade ---
                    // Checada APÓS o tick: se verificarNovosProcessos() acabou de
                    // inserir um processo na Fila 1, interrompemos o FCFS agora.
                    // O processo de baixa prioridade retorna ao final de sua fila
                    // com o estado PRONTO — seu progresso (turnaround) é preservado.
                    if (filaMaiorPrioridade.temProcessosProntos()) {
                        System.out.println(String.format("[ESCALONAMENTO] Tempo %d: Processo de alta prioridade chegou, interrompendo FCFS",
                                tempo));
                        mandarParaFinalDaFilaDePronto(processoEmExecucao, filaMenorPrioridade);
                        break;
                    }

                    System.out.println(String.format("[DEBUG] Tempo %d: Processo %d | turnaround: %d | total: %d",
                            tempo, processoEmExecucao.getPid(),
                            processoEmExecucao.getTurnaround(),
                            processoEmExecucao.tempoTotalDeExecucao()));
                }
            }
            // #endregion FCFS
        }

        return tempo;
    }

    /**
     * Verifica se algum processo da lista de chegadas futuras chegou
     * no tick atual e o encaminha para a fila de prontos correta.
     *
     * Usa um loop while para tratar múltiplos processos com o mesmo
     * tempo de chegada — sem isso, apenas o primeiro seria detectado
     * por tick, atrasando erroneamente os demais.
     *
     * Pressupõe que processos[] está ordenado por chegada.
     */
    private static void verificarNovosProcessos() {
        while (!processos.isEmpty() && processos.get(0).getChegada() == tempo) {
            Processo novoProcesso = processos.get(0);
            System.out.println(String.format("[CHEGADA] Tempo %d: Novo processo chegou! ID: %d, Prioridade: %d",
                    tempo, novoProcesso.getPid(), novoProcesso.getPrioridade()));
            definirProcessoComoPronto(novoProcesso);
            processos.remove(0);
        }
    }

    /**
     * Move um processo para a fila de espera de I/O da sua fila de origem.
     *
     * FilaMLQ.adicionarAhFilaDeEmEspera() muda o estado para EM_ESPERA,
     * inicializa tempoDeEspera = TEMPO_DE_IO (5 ticks) via Processo.alterarEstado(),
     * e remove o processo da lista de prontos da fila — garantindo que ele
     * não apareça em dois lugares ao mesmo tempo.
     *
     * @param p    processo a bloquear
     * @param fila fila à qual o processo pertence (alta ou baixa prioridade)
     */
    private static void colocarProcessoEmEspera(Processo p, FilaMLQ fila) {
        System.out.println(String.format("[I/O] Processo %d colocado em espera", p.getPid()));
        fila.adicionarAhFilaDeEmEspera(p);
    }

    /**
     * Devolve um processo ao final da fila de prontos da sua fila de origem.
     *
     * Usado em dois cenários:
     *   - Round-Robin: processo esgotou o quantum e retorna ao final da Fila 1.
     *   - FCFS preemptado: processo de baixa prioridade interrompido retorna à Fila 2.
     *
     * FilaMLQ.mandarParaFinalDaFilaDePronto() muda o estado para PRONTO
     * e adiciona ao final da lista — preservando a ordem FIFO.
     *
     * @param p    processo a devolver
     * @param fila fila à qual o processo pertence
     */
    private static void mandarParaFinalDaFilaDePronto(Processo p, FilaMLQ fila) {
        System.out.println(String.format("[FILA] Processo %d retornou ao final da fila de prontos", p.getPid()));
        fila.mandarParaFinalDaFilaDePronto(p);
    }

    /**
     * Avança o tempo de I/O de todos os processos bloqueados em ambas as filas.
     *
     * FilaMLQ.esperar() itera sobre os processos em espera da fila,
     * decrementa seus contadores e os move para prontos quando concluem.
     * Chamamos para ambas as filas pois I/O pode ocorrer em qualquer uma.
     */
    private static void esperar() {
        filaMaiorPrioridade.esperar();
        filaMenorPrioridade.esperar();
    }

    /**
     * Retorna true se houver processos bloqueados em I/O em QUALQUER fila.
     * Necessário para que o loop principal não termine prematuramente
     * enquanto processos ainda estão aguardando I/O.
     */
    private static boolean temProcessosEmEspera() {
        return filaMaiorPrioridade.temProcessosEmEspera() || filaMenorPrioridade.temProcessosEmEspera();
    }

    /**
     * Retorna true se houver processos prontos em QUALQUER fila.
     * Usado como condição de continuação do loop principal e como
     * critério de preempção da Fila 2 (que exige Fila 1 vazia).
     */
    private static boolean temProcessosProntos() {
        return filaMaiorPrioridade.temProcessosProntos() || filaMenorPrioridade.temProcessosProntos();
    }

    /**
     * Insere um processo na fila de prontos correspondente à sua prioridade.
     *
     * A prioridade é um atributo fixo do processo (definido no arquivo de entrada)
     * e não muda durante a simulação — o MLQ aqui implementado é ESTÁTICO:
     * processos não migram entre filas.
     *
     *   Prioridade 1 → filaMaiorPrioridade (Round-Robin)
     *   Prioridade 2 → filaMenorPrioridade (FCFS)
     *   Outro valor  → exceção, pois a simulação não suporta outras filas.
     *
     * FilaMLQ.adicionarAhFilaDePronto() muda o estado para PRONTO e evita
     * duplicatas verificando se o processo já está na lista.
     *
     * @param p processo a inserir
     * @throws IllegalArgumentException se a prioridade não for 1 nem 2
     */
    private static void definirProcessoComoPronto(Processo p) {
        FilaMLQ filaDeProcessos;
        int prioridade = p.getPrioridade();

        switch (prioridade) {
            case 1:
                filaDeProcessos = filaMaiorPrioridade;
                System.out.println(String.format("[FILA] Processo %d adicionado à fila de MAIOR prioridade", p.getPid()));
                break;
            case 2:
                filaDeProcessos = filaMenorPrioridade;
                System.out.println(String.format("[FILA] Processo %d adicionado à fila de MENOR prioridade", p.getPid()));
                break;
            default:
                throw new IllegalArgumentException(String.format("[ERRO] Prioridade inválida para processo %d: %d", p.getPid(), prioridade));
        }

        filaDeProcessos.adicionarAhFilaDePronto(p);
    }
}