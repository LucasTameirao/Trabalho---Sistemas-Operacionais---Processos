package com.processos;

import java.util.ArrayList;
import java.util.List;

import com.processos.util.LeitorDeProcessos;

public class FCFS {
    private static List<Processo> processos = new ArrayList<>();
    private static int tempo = 0;

    private static List<Processo> processosProntos = new ArrayList<>(); 
    private static List<Processo> processosEmEspera = new ArrayList<>(); 

    private static void lerProcessos(){
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    public static int iniciarSimulacao(){
        lerProcessos();
        tempo = executarProcessos();
<<<<<<< HEAD
=======

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
        while (temProcessosProntos() || temProcessosEmEspera()) {

            // ── CENÁRIO 1: CPU ociosa ─────────────────────────────────────────
            // Se não há processo pronto mas há processos em I/O, o sistema
            // precisa esperar. A cada iteração:
            //   - esperar() decrementa o contador de I/O de cada processo em espera;
            //   - se algum processo zera o contador, ele volta para processosProntos;
            //   - verificarChegadas() verifica se novos processos chegaram;
            //   - tempo++ avança o relógio.
            while (temProcessosEmEspera() && !temProcessosProntos()) {
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
            while (exec.getTempoDeProcessador() < tempoTotalExec) {

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

                    if (exec.getTempoDeProcessador() == instantesIO[proxIO]) {
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

>>>>>>> 3f01bea (Terminando documentação dos algoritmos)
        return tempo;
    }

    private static int executarProcessos(){

        Processo processoEmExecucao;
        int[] instantesIO;
        int tempoTotalDeExecucao;
        
        while(temProcessosProntos() || !processosEmEspera.isEmpty()){
            while(!processosEmEspera.isEmpty() && !temProcessosProntos()){
                esperar();
            }
            System.out.println("tem processos prontos... ");
            for(Processo p : processosProntos){
                System.out.println(p);
            }
            processoEmExecucao = executaPrimeiroDaLista();
            System.out.println(processoEmExecucao);
            instantesIO = processoEmExecucao.getInstantesIO();
            tempoTotalDeExecucao = processoEmExecucao.tempoTotalDeExecucao();

            if (instantesIO != null) {
                System.out.println("entrou");
                int proximoIO = processoEmExecucao.proximoTempoDeIO();
                System.out.println(processoEmExecucao.getTurnaround());
                
                while(processoEmExecucao.getTurnaround() < tempoTotalDeExecucao){
                    

                    processoEmExecucao.executarProcesso();
                    tempo++;
                    esperar();
                    System.out.println("executou e esperou");
                    Processo novoProcesso = processos.isEmpty() ? null : processos.getFirst();
                    if(novoProcesso != null){
                        System.out.println(novoProcesso);
                    }
                    

                    //verifica se chegaram processos novos
                    if(temNovosProcessos()){
                        if(novoProcesso.getChegada() == tempo){ 
                            definirProcessoComoPronto(novoProcesso);
                            System.out.println("definiu como pronto, processo " + novoProcesso.getPid());
                            processos.remove(novoProcesso);
                            System.out.println("Processo " + novoProcesso.getPid() + " removido da lista");
                        }
                    }

                    // verifica se o processo fez I/O para coloca-lo em espera
                    if (processoEmExecucao.getTurnaround() == instantesIO[proximoIO]) {
                        System.out.println("Identificou o I/O no momento " + instantesIO[proximoIO]);
                        proximoIO++;
                        processoEmExecucao.definirProximoIO(proximoIO);
                        processoEmExecucao.aumentarTempoTotalDeExecucao();
                        colocarProcessoEmEspera(processoEmExecucao);
                        System.out.println("colocou processo em espera");
                        break;
                    }

                    System.out.println("tempo de execução " + tempo);
                }
            }
            else{
                Processo novoProcesso;

                //verifica se durante a execução do processo surgiram mais processos
                while (processoEmExecucao.getTurnaround() < tempoTotalDeExecucao) {
                    processoEmExecucao.executarProcesso();
                    tempo++;
                    esperar();

                    if(temNovosProcessos()){
                        novoProcesso = processos.getFirst();
                        if (novoProcesso.getChegada() == tempo) {
                            processos.remove(novoProcesso);
                            definirProcessoComoPronto(novoProcesso);
                        }
                    }
                }
            }
        }

        System.out.println("Processos em espera");
        for(Processo p : processosEmEspera){
            System.out.println(p);
        }
        System.out.println("Processos prontos");
        for(Processo p : processosProntos){
            System.out.println(p);
        }
        return tempo;
    }

    private static boolean temNovosProcessos() {
        return !processos.isEmpty();
    }

    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    private static void colocarProcessoEmEspera(Processo p){
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        if(processosProntos.contains(p)){
            processosProntos.remove(p);
        }
    }

    private static void definirProcessoComoPronto(Processo p){
        
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if(processosEmEspera.contains(p)){
            processosEmEspera.remove(p);
        }
    }

    private static boolean processoEstahPronto(Processo p){
        return p.estadoProcesso() == EEstadoProcesso.PRONTO;
    }

    private static Processo executaPrimeiroDaLista(){
        Processo p = null;
        if (temProcessosProntos()) {
            p = processosProntos.getFirst();
            p.alterarEstado(EEstadoProcesso.EXECUTANDO);
            processosProntos.remove(p);
        }

        return p;
        
    }

    private static void esperar(){
        for(int i = 0; i < processosEmEspera.size(); i++){
            Processo p = processosEmEspera.get(i);
            System.out.println(p.esperar());
            System.out.println("processo " + p.getPid() + " esperou");
            System.out.println(p.estadoProcesso());
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                System.out.println("FICOU PRONTO E FOI PRA LISTA DE PRONTOS");
                definirProcessoComoPronto(p);
            }
        }
    }
<<<<<<< HEAD
}
=======

    private static boolean temProcessosEmEspera(){
        return !processosEmEspera.isEmpty();
    }
}
>>>>>>> 3f01bea (Terminando documentação dos algoritmos)
