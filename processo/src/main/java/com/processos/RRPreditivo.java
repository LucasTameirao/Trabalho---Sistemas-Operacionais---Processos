package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

public class RRPreditivo {

    // Peso da média exponencial: 0.5 significa que passado e presente
    // têm o mesmo peso na previsão do próximo surto.
    private static final double ALPHA = 0.5;

    // Previsão usada para processos novos, que ainda não têm histórico de CPU.
    private static final double PRIMEIRA_PREVISAO = 10.0;

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

    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        verificarChegadas();
    }


    // =========================================================================
    // LOOP PRINCIPAL DA SIMULAÇÃO
    // =========================================================================

    private static int executarProcessos() {

        // enquanto houver processo pronto ou em I/O.
        while (temProcessosProntos() || temProcessosEmEspera()) {

            
            while (temProcessosEmEspera() && !temProcessosProntos()) {
                esperar();
                tempo++;
                verificarChegadas();
            }

            
            int quantum = (int) Math.max(1, Math.round(menorTau()));

            // Retira o processo da FRENTE da fila (FIFO circular).
            Processo exec = executaPrimeiroDaLista();

            // Contador de ciclos executados neste quantum.
            int ciclosNoQuantum = 0;

            // ── Loop interno: executa até o quantum antigir o limite, fizer I/O ou terminar o processo
            while (exec.tempoRestante() > 0 && ciclosNoQuantum < quantum) {

                exec.executarProcesso();

                // Registra 1 ciclo no surto atual do processo.
                // burstAtual é o t_n da fórmula: duração real do surto corrente.
                exec.incrementarBurstAtual();

                ciclosNoQuantum++;
                tempo++;
                esperar();
                verificarChegadas();

                if (exec.getInstantesIO() != null) {
                    int proxIO = exec.proximoTempoDeIO();
                    if (exec.getTempoDeProcessador() == exec.getInstantesIO()[proxIO]) {

                        
                        exec.definirProximoIO(proxIO + 1);

                        exec.atualizarPrevisaoAnterior(ALPHA);

                        colocarProcessoEmEspera(exec);

                        exec = null;
                        break;
                    }
                }
            }

            if (exec != null) {
                    
                // CASO 1: Processo terminou (tempoRestante <= 0)
                if (exec.tempoRestante() <= 0) {
                    exec.atualizarPrevisaoAnterior(ALPHA);
                    finalizarProcesso(exec, tempo);
                }
                // CASO 2: Processo esgotou o quantum mas ainda tem tempo
                else {
                    exec.atualizarPrevisaoAnterior(ALPHA);
                    exec.alterarEstado(EEstadoProcesso.PRONTO);
                    processosProntos.add(exec);
                }
            }
        }

        return tempo;
    }


    // =========================================================================
    // MÉTODOS AUXILIARES (HELPERS)
    // =========================================================================

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
     * @return o menor τ entre os processos em processosProntos.
     */
    private static double menorTau() {
        double menor = processosProntos.getFirst().getPrevisao();
        for (Processo p : processosProntos) {
            if (p.getPrevisao() < menor) {
                menor = p.getPrevisao();
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
                novo.inicializarTau(PRIMEIRA_PREVISAO); // processo novo sem histórico → τ₀ = 10
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