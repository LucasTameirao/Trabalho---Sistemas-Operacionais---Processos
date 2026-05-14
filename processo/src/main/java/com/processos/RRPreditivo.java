package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

/**
 * Escalonador Round-Robin com Quantum Preditivo.
 *
 * Diferente do Round-Robin clássico (quantum fixo), aqui o quantum é
 * recalculado a cada troca de contexto com base na menor média exponencial
 * (τ — "tau") entre os processos prontos. Isso significa que o escalonador
 * tenta se adaptar ao comportamento real dos processos: se um processo
 * historicamente usa pouco tempo de CPU, seu τ será pequeno e influenciará
 * um quantum menor para todos na fila.
 *
 * Parâmetros fixos conforme enunciado:
 *   α (ALPHA)     = 0.5  → peso igual entre burst real e predição anterior
 *   τ₀ (TAU_INICIAL) = 10ms → predição inicial para todos os processos
 *
 * Fórmula de atualização do τ (média exponencial):
 *   τ_novo = α × burst_real + (1 − α) × τ_anterior
 *
 * Esta classe é totalmente estática (sem instâncias), centralizando todo
 * o estado da simulação em atributos de classe.
 */
public class RRPreditivo {

    // Lista com todos os processos ainda não chegados ao sistema.
    // Usamos ArrayList pois precisamos de acesso por índice e remoção
    // eficiente durante a simulação. Os processos são consumidos
    // conforme seu tempo de chegada é atingido.
    private static List<Processo> processos = new ArrayList<>();

    // Relógio global da simulação. Avança 1 unidade por ciclo de execução
    // ou de espera (I/O). Representa o tempo real decorrido no sistema.
    private static int tempo = 0;

    // Fila de prontos: processos que já chegaram e estão aguardando CPU.
    // Mantida como ArrayList para facilitar iteração e remoção por posição.
    // A ordem é FIFO — quem chegou primeiro à fila executa primeiro,
    // respeitando a natureza circular do Round-Robin.
    private static List<Processo> processosProntos = new ArrayList<>();

    // Fila de espera: processos bloqueados aguardando fim de I/O.
    // O tempo de I/O é fixo em 5 unidades (definido em Processo.TEMPO_DE_IO).
    // Cada tick do simulador decrementa o contador interno de espera
    // de cada processo aqui presente.
    private static List<Processo> processosEmEspera = new ArrayList<>();

    // α = 0.5 → fator de suavização da média exponencial.
    // Com α = 0.5, burst real e histórico têm peso idêntico.
    // Valores maiores de α tornam o τ mais reativo a mudanças recentes.
    private static final double ALPHA = 0.5;

    // Predição inicial de burst para todos os processos.
    // Como ainda não temos histórico, assumimos 10ms para todos.
    private static final double TAU_INICIAL = 10.0;

    // Vetor de τ indexado pelo PID do processo.
    // taus[pid] contém a predição de burst atual para o processo de ID pid.
    // Alocado com tamanho processos.size() + 1 para usar PID diretamente
    // como índice (evitando mapeamento extra).
    private static double[] taus;

    /**
     * Inicializa os processos carregando-os do arquivo de entrada
     * e prepara o vetor de τ com o valor inicial para todos.
     *
     * LeitorDeProcessos.criarProcessos() (utilitário externo) lê o arquivo
     * de configuração e retorna um array de objetos Processo já montados
     * com PID, chegada, burst, prioridade e instantes de I/O.
     *
     * Só o primeiro processo é colocado na fila de prontos aqui —
     * os demais são inseridos via verificarNovosProcessos() conforme
     * o relógio avança e atinge seus tempos de chegada.
     */
    private static void lerProcessos() {
        // Converte o array retornado pelo leitor em uma List e acumula
        // em processos, que servirá como "banco" de chegadas futuras.
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));

        // Cria o vetor de τ com uma posição extra para suportar PID 1-based.
        // Todos começam com TAU_INICIAL = 10ms, pois não há histórico ainda.
        taus = new double[processos.size() + 1];
        for (int i = 0; i < taus.length; i++) {
            taus[i] = TAU_INICIAL;
        }

        // O primeiro processo da lista é adicionado imediatamente à fila de
        // prontos. Pressupõe-se que os processos estão ordenados por chegada
        // e que o primeiro chega no instante inicial (tempo 0).
        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    /**
     * Ponto de entrada público da simulação.
     * Carrega os processos, executa o loop principal e retorna o tempo total.
     */
    public static int iniciarSimulacao() {
        lerProcessos();
        tempo = executarProcessos();
        return tempo;
    }

    /**
     * Loop principal da simulação.
     *
     * Continua enquanto houver processos prontos OU em espera de I/O.
     * Quando não há nenhum processo pronto (CPU ociosa), avança o relógio
     * e aguarda I/O concluir — evitando busy-wait infinito.
     *
     * A cada fatia de CPU concedida a um processo:
     *   1. Calcula o quantum atual com base nos τ da fila de prontos.
     *   2. Executa o processo tick a tick.
     *   3. A cada tick: verifica I/O, esgotamento de quantum e conclusão.
     *   4. Ao sair do quantum, atualiza o τ do processo e o devolve à fila.
     *
     * @return tempo total decorrido ao fim de todos os processos.
     */
    private static int executarProcessos() {

        // Continua enquanto existirem processos prontos ou bloqueados em I/O.
        // Processos ainda não chegados serão detectados por verificarNovosProcessos().
        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {

            // --- Tratamento de CPU ociosa (idle) ---
            // Se não há ninguém pronto mas há processos em I/O, avançamos o
            // relógio até que algum I/O conclua e o processo volte para prontos.
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                esperar();     // decrementa contadores de I/O de todos em espera
                tempo++;       // avança o relógio global
                verificarNovosProcessos(); // verifica se algum processo chegou neste tick
            }

            // --- Cálculo do quantum preditivo ---
            // O quantum é determinado pelo menor τ entre os processos prontos.
            // Isso reflete a predição do menor burst próximo na fila,
            // evitando que processos lentos "contaminem" processos rápidos.
            int quantum = calcularQuantum();
            System.out.println("Quantum calculado: " + quantum);

            // Retira o primeiro processo da fila de prontos e marca como EXECUTANDO.
            // executaPrimeiroDaLista() usa processosProntos.getFirst() — respeita FIFO.
            Processo processoEmExecucao = executaPrimeiroDaLista();
            int tempoNoProcessador = 0; // contador de ticks gastos nesta fatia de CPU

            // Obtém os instantes de I/O do processo e o índice do próximo evento.
            // instantesIO: array com os momentos (em turnaround) em que o processo
            //              solicita I/O. Null se o processo não faz I/O.
            // proximoIO:   índice para o próximo instante não consumido no array.
            int[] instantesIO = processoEmExecucao.getInstantesIO();
            int proximoIO = processoEmExecucao.proximoTempoDeIO();

            // --- Loop de execução tick a tick do processo atual ---
            // Continua enquanto o processo não tiver completado seu burst total.
            // getTurnaround() = quantos ticks de CPU o processo já usou.
            // tempoTotalDeExecucao() = total de ticks de CPU que precisa usar.
            while (processoEmExecucao.getTurnaround() < processoEmExecucao.tempoTotalDeExecucao()) {

                // Executa 1 tick de CPU: incrementa o turnaround interno do processo.
                // executarProcesso() retorna o novo valor de turnaround — não usado aqui.
                processoEmExecucao.executarProcesso();
                tempoNoProcessador++; // contabiliza uso do quantum atual
                tempo++;              // avança o relógio global

                // A cada tick, verifica se processos em I/O concluíram
                // e os move de volta para a fila de prontos.
                esperar();
                // Verifica se algum processo externo chegou neste tick.
                verificarNovosProcessos();

                // --- Verificação de evento de I/O ---
                // Se o turnaround atual coincide com um instante de I/O cadastrado,
                // o processo para de executar e vai para a fila de espera.
                // Comparamos instantesIO[proximoIO] com getTurnaround() porque
                // o instante de I/O é definido em unidades de CPU já consumidas.
                if (instantesIO != null && processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {
                    System.out.println("Processo " + processoEmExecucao.getPid() + " foi para I/O no tempo " + tempo);

                    // Avança o ponteiro para o próximo instante de I/O.
                    // definirProximoIO() também anula instantesIO se não houver mais eventos.
                    proximoIO++;
                    processoEmExecucao.definirProximoIO(proximoIO);

                    // Coloca o processo na fila de espera; seu estado muda para EM_ESPERA
                    // e tempoDeEspera é inicializado com TEMPO_DE_IO (5 ticks).
                    colocarProcessoEmEspera(processoEmExecucao);
                    break; // sai do loop de execução; o processo voltará após o I/O
                }

                // --- Verificação de esgotamento de quantum ---
                // Se o processo usou todos os ticks do quantum, é preemptado
                // e devolvido ao FINAL da fila de prontos (Round-Robin).
                if (tempoNoProcessador == quantum) {
                    System.out.println("Processo " + processoEmExecucao.getPid() + " esgotou o quantum no tempo " + tempo);

                    // Atualiza o τ com o burst real desta fatia antes de devolver.
                    // Isso refina a predição para as próximas vezes que o processo
                    // for escalado: τ_novo = α × real + (1−α) × τ_anterior.
                    atualizarTau(processoEmExecucao.getPid(), tempoNoProcessador);
                    devolverParaProntos(processoEmExecucao);
                    break; // sai do loop; o processo voltará na próxima rodada
                }
            }

            // --- Verificação de conclusão ---
            // Se o processo esgotou seu burst total (saiu do while por condição
            // e não por break de I/O ou quantum), está finalizado.
            // Atualizamos o τ com o burst desta última fatia para manter
            // o histórico consistente, mesmo que não seja mais usado.
            if (processoEmExecucao.getTurnaround() >= processoEmExecucao.tempoTotalDeExecucao()) {
                atualizarTau(processoEmExecucao.getPid(), tempoNoProcessador);
                System.out.println("Processo " + processoEmExecucao.getPid() + " finalizado no tempo " + tempo);
            }
        }

        return tempo;
    }

    /**
     * Calcula o quantum para a próxima fatia de CPU.
     *
     * Percorre todos os processos na fila de prontos e seleciona o menor τ.
     * Esse valor é arredondado para inteiro (mínimo 1, para evitar quantum zero).
     *
     * Se a fila de prontos estiver vazia (situação inesperada neste ponto),
     * retorna TAU_INICIAL como fallback seguro.
     *
     * Decisão de projeto: usar o MENOR τ (e não a média) incentiva que
     * processos historicamente curtos tenham sua predição respeitada,
     * aproximando o comportamento do SJF para processos rápidos.
     */
    private static int calcularQuantum() {
        if (processosProntos.isEmpty()) {
            return (int) Math.round(TAU_INICIAL);
        }

        double menorTau = Double.MAX_VALUE;
        for (Processo p : processosProntos) {
            double tau = taus[p.getPid()]; // acessa τ pelo PID do processo
            if (tau < menorTau) {
                menorTau = tau;
            }
        }

        // Math.max(1, ...) garante que o quantum nunca seja zero,
        // o que causaria loop infinito (o processo nunca executaria).
        return Math.max(1, (int) Math.round(menorTau));
    }

    /**
     * Atualiza o τ de um processo após sua execução.
     *
     * Aplica a fórmula da média exponencial:
     *   τ_novo = α × burstaReal + (1 − α) × τ_anterior
     *
     * Com α = 0.5: τ_novo = 0.5 × real + 0.5 × anterior
     * → média aritmética simples entre o burst medido e a predição anterior.
     *
     * @param pid       identificador do processo (índice no vetor taus[])
     * @param burstaReal tempo real de CPU usado nesta fatia (pode ser
     *                   menor que o quantum se houve I/O ou conclusão)
     */
    private static void atualizarTau(int pid, int burstaReal) {
        taus[pid] = ALPHA * burstaReal + (1 - ALPHA) * taus[pid];
        System.out.println("τ do processo " + pid + " atualizado para " + taus[pid]);
    }

    /**
     * Verifica se algum processo da lista de chegadas futuras chegou
     * no tick atual e o move para a fila de prontos.
     *
     * Usa um loop while para tratar múltiplos processos com o mesmo
     * tempo de chegada — sem isso, apenas o primeiro seria detectado
     * por tick, atrasando erroneamente os demais.
     *
     * Pressupõe que a lista processos[] está ordenada por chegada
     * (garantido pelo LeitorDeProcessos).
     */
    private static void verificarNovosProcessos() {
        while (!processos.isEmpty() && processos.get(0).getChegada() == tempo) {
            Processo novoProcesso = processos.get(0);
            definirProcessoComoPronto(novoProcesso);
            processos.remove(0);
            System.out.println("Processo " + novoProcesso.getPid() + " chegou no tempo " + tempo);
        }
    }

    /**
     * Retorna true se houver ao menos um processo na fila de prontos.
     */
    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    /**
     * Move um processo para a fila de espera de I/O.
     *
     * Processo.colocarEmEspera() muda o estado para EM_ESPERA e inicializa
     * tempoDeEspera = TEMPO_DE_IO (5 ticks). A partir daqui, esperar()
     * decrementa esse contador a cada tick até o processo voltar a PRONTO.
     */
    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        if (processosProntos.contains(p)) {
            processosProntos.remove(p);
        }
    }

    /**
     * Devolve um processo ao final da fila de prontos após esgotar o quantum.
     *
     * Mudar o estado para PRONTO e adicionar ao final da lista é o que
     * implementa a semântica circular do Round-Robin: o processo vai para
     * o final da fila e aguarda todos os outros antes de executar novamente.
     */
    private static void devolverParaProntos(Processo p) {
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosProntos.add(p); // adiciona ao FINAL — garante a rotação circular
    }

    /**
     * Move um processo para a fila de prontos.
     *
     * Usado tanto na chegada inicial quanto no retorno do I/O.
     * Remove da fila de espera se lá estiver (evita duplicatas).
     */
    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if (processosEmEspera.contains(p)) {
            processosEmEspera.remove(p);
        }
    }

    /**
     * Retira o primeiro processo da fila de prontos e o marca como EXECUTANDO.
     *
     * getFirst() respeita a ordem FIFO da fila — quem chegou primeiro executa
     * primeiro dentro da rodada, garantindo equidade entre os processos.
     */
    private static Processo executaPrimeiroDaLista() {
        Processo p = processosProntos.getFirst();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    /**
     * Avança o tempo de I/O de todos os processos bloqueados.
     *
     * A cada chamada, Processo.esperar() decrementa tempoDeEspera do processo.
     * Quando chega a zero, o próprio Processo muda seu estado para PRONTO.
     * Detectamos isso aqui e movemos o processo de volta à fila de prontos.
     *
     * Iteração reversa: ao remover um elemento da lista durante a iteração,
     * os índices à direita deslocam para a esquerda. Percorrer de trás para
     * frente garante que a remoção não pule elementos — a mesma lógica
     * usada em FilaMLQ.esperar().
     *
     * Removemos o processo de processosEmEspera ANTES de chamar
     * definirProcessoComoPronto() para evitar que este método tente
     * removê-lo novamente (ele verifica contains() internamente).
     */
    private static void esperar() {
        for (int i = processosEmEspera.size() - 1; i >= 0; i--) {
            Processo p = processosEmEspera.get(i);
            p.esperar(); // decrementa tempoDeEspera; muda estado se atingir 0
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                processosEmEspera.remove(i); // remove antes para evitar dupla remoção
                definirProcessoComoPronto(p);
            }
        }
    }
}