package com.processos;

public class Processo {
    private int pid;
    private int chegada;
    private int burstTotal;
    private int prioridade;
    private int[] instantesIO;
    private final int TEMPO_DE_IO = 5;
    private EEstadoProcesso estado;
    private int tempoDeEspera = 0;
    private int turnaround = 0;
    private int proximoIO = 0;
    private int tempoTotalDeExecucao;

    // Métricas de resultado
    private int instanteDeFim = 0;
    private int tempoEsperaTotal = 0; // acumulado de ciclos em que o processo estava pronto mas não executando

    // Para Round-Robin preditivo: média exponencial por processo
    private double tau; // previsão atual (τ)
    private int burstAtual = 0; // tempo de CPU acumulado no surto corrente

    public Processo(int pid, int chegada, int burstTotal, int prioridade, int[] instantesIO) {
        this.pid = pid;
        this.chegada = chegada;
        this.burstTotal = burstTotal;
        this.instantesIO = instantesIO;
        this.prioridade = prioridade;
        this.tempoTotalDeExecucao = burstTotal;
    }

    public Processo(String[] dados) {
        pid = Integer.parseInt(dados[0]);
        chegada = Integer.parseInt(dados[1]);
        burstTotal = Integer.parseInt(dados[2]);
        prioridade = Integer.parseInt(dados[3]);
        instantesIO = converterInstantesParaInteiros(dados);
        tempoTotalDeExecucao = burstTotal;
    }

    // ─── Getters básicos ────────────────────────────────────────────────────────

    public int getPid()        { return pid; }
    public int getChegada()    { return chegada; }
    public int getBurstTotal() { return burstTotal; }
    public int getPrioridade() { return prioridade; }
    public int[] getInstantesIO() { return instantesIO; }
    public int getTEMPO_DE_IO()   { return TEMPO_DE_IO; }

    // ─── Métricas ────────────────────────────────────────────────────────────────

    public int getInstanteDeFim()         { return instanteDeFim; }
    public void setInstanteDeFim(int t)   { this.instanteDeFim = t; }

    /** Turnaround real = instante de fim − instante de chegada */
    public int getTurnaroundReal()        { return instanteDeFim - chegada; }

    /** Tempo de espera real = turnaround real − burst total de CPU */
    public int getTempoEsperaReal()       { return getTurnaroundReal() - burstTotal; }

    // ─── Média exponencial (Round-Robin preditivo) ───────────────────────────────

    /**
     * Inicializa τ com o valor padrão τ₀.
     * Deve ser chamado antes de inserir o processo no escalonador RR preditivo.
     */
    public void inicializarTau(double tau0) { this.tau = tau0; }

    public double getTau() { return tau; }

    /**
     * Atualiza a previsão após completar um surto de CPU.
     * τ_{n+1} = α * t_n + (1 − α) * τ_n   (α = 0.5)
     */
    public void atualizarTau(double alpha) {
        tau = alpha * burstAtual + (1 - alpha) * tau;
        burstAtual = 0; // reseta para o próximo surto
    }

    /** Registra um ciclo de CPU no surto corrente (usado pelo RR preditivo). */
    public void incrementarBurstAtual() { burstAtual++; }

    public int getBurstAtual() { return burstAtual; }

    // ─── Controle de execução ────────────────────────────────────────────────────

    public int executarProcesso() { return ++turnaround; }

    public EEstadoProcesso alterarEstado(EEstadoProcesso e) {
        estado = e;
        if (estado == EEstadoProcesso.EM_ESPERA) {
            tempoDeEspera = TEMPO_DE_IO;
        }
        return estado;
    }

    public EEstadoProcesso colocarEmEspera() {
        estado = EEstadoProcesso.EM_ESPERA;
        tempoDeEspera = TEMPO_DE_IO;
        return estado;
    }

    public EEstadoProcesso estadoProcesso() { return estado; }

    public EEstadoProcesso finalizarProcesso() {
        estado = EEstadoProcesso.FINALIZADO;
        return estado;
    }

    public int esperar() {
        if (estado == EEstadoProcesso.EM_ESPERA) {
            tempoDeEspera--;
            if (tempoDeEspera <= 0) {
                alterarEstado(EEstadoProcesso.PRONTO);
            }
        }
        return tempoDeEspera;
    }

    public int getTurnaround()        { return turnaround; }

    public int proximoTempoDeIO()     { return proximoIO; }

    public int definirProximoIO(int proximoIO) {
        this.proximoIO = proximoIO;
        if (proximoIO >= instantesIO.length) {
            instantesIO = null;
            this.proximoIO = -1;
        }
        return this.proximoIO;
    }

    public int aumentarTempoTotalDeExecucao() {
        tempoTotalDeExecucao += TEMPO_DE_IO;
        return tempoTotalDeExecucao;
    }

    public int tempoTotalDeExecucao() { return tempoTotalDeExecucao; }

    public int tempoRestante()        { return tempoTotalDeExecucao - turnaround; }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private int[] converterInstantesParaInteiros(String[] dados) {
        if (!possuiIO(dados)) return null;
        String[] partes = dados[4].split(",");
        int[] instantes = new int[partes.length];
        for (int i = 0; i < partes.length; i++) {
            instantes[i] = Integer.parseInt(partes[i].trim());
        }
        return instantes;
    }

    private boolean possuiIO(String[] dados) { return dados.length == 5; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("============================\n");
        sb.append("PID: ").append(pid).append("\n");
        sb.append("Chegada: ").append(chegada).append("\n");
        sb.append("Burst Total: ").append(burstTotal).append("\n");
        sb.append("Prioridade: ").append(prioridade).append("\n");
        if (instantesIO == null) {
            sb.append("Instantes de I/O: ---\n");
        } else {
            StringBuilder inst = new StringBuilder();
            for (int i = 0; i < instantesIO.length - 1; i++) inst.append(instantesIO[i]).append(", ");
            inst.append(instantesIO[instantesIO.length - 1]);
            sb.append("Instantes de I/O: ").append(inst).append("\n");
        }
        sb.append("============================\n");
        return sb.toString();
    }
}