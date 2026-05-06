package com.processos;

public class Processo {
    private int pid;
    private int chegada;
    private int burstTotal;
    private int prioridade;
    private int[] instantesIO;
    private int[] temposDeTurnaround = null;
    private final int TEMPO_DE_IO = 5;
    private EEstadoProcesso estado;
    private int tempoDeEspera = 0;
    private int turnaround = 0;
    private int proximoIO = 0;
    private int tempoTotalDeExecucao;

    public Processo(int pid, int chegada, int burstTotal, int prioridade, int[] instantesIO){
        this.pid = pid;
        this.chegada = chegada;
        this.burstTotal = burstTotal;
        this.instantesIO = instantesIO;
        this.prioridade = prioridade;
    }

    public Processo(String[] dados){

        pid = Integer.parseInt(dados[0]);
        chegada = Integer.parseInt(dados[1]);
        burstTotal = Integer.parseInt(dados[2]);
        prioridade = Integer.parseInt(dados[3]);

        instantesIO = converterInstantesParaInteiros(dados);
        tempoTotalDeExecucao = burstTotal;
    }

    public int getPid() {
        return pid;
    }

    public int getChegada() {
        return chegada;
    }

    public int getBurstTotal() {
        return burstTotal;
    }

    public int getPrioridade() {
        return prioridade;
    }

    public int[] getInstantesIO() {
        return instantesIO;
    }

    public int getTEMPO_DE_IO() {
        return TEMPO_DE_IO;
    }

    public int[] setTemposDeTurnaround(int[] turnaround){
        temposDeTurnaround = turnaround;
        return temposDeTurnaround;
    }

    private int[] converterInstantesParaInteiros(String[] dados){
        int instantes[] = null;
        String[] instantesTexto = null;
        
        if (possuiIO(dados)) {
            instantesTexto = dados[4].split(",");
            instantes = new int[instantesTexto.length];
            for(int i = 0; i < instantes.length; i++){
                instantes[i] = Integer.parseInt(instantesTexto[i]);
            }
        }

        return instantes;
    }

    private boolean possuiIO(String[] dados){
        return dados.length == 5;
    }

    public Processo quemTemPrioridade(Processo outro){
        return prioridade <= outro.prioridade ? this : outro;
    }

    @Override
    public String toString() {
        StringBuilder linhaHorizontal = new StringBuilder("============================\n");
        StringBuilder texto = new StringBuilder(linhaHorizontal);
        texto.append("PID: " + pid + "\n");
        texto.append("Chegada: " + chegada + "\n");
        texto.append("Burst Total: " + burstTotal + "\n");
        texto.append("Prioridade: " + prioridade + "\n");

        if (instantesIO == null) {
            texto.append("Instantes de I/O: ---" + "\n");
        }else{
            StringBuilder instantes = new StringBuilder("");
            for(int i = 0; i < instantesIO.length - 1; i++){
                instantes.append(instantesIO[i] + ", ");
            }
            instantes.append(instantesIO[instantesIO.length - 1]);
            texto.append("Instantes de I/O: " + instantes + "\n");
        }

        texto.append(linhaHorizontal);

        return texto.toString();
    }

    public int executarProcesso() {
        return ++turnaround;
    }

    public EEstadoProcesso alterarEstado(EEstadoProcesso e){
        estado = e;
        if(estado == EEstadoProcesso.EM_ESPERA){
            tempoDeEspera = TEMPO_DE_IO;
        }
        return estado;
    }

    public EEstadoProcesso colocarEmEspera(){
        estado = EEstadoProcesso.EM_ESPERA;
        tempoDeEspera = 5;
        return estado;
    }

    public EEstadoProcesso estadoProcesso(){
        return estado;
    }

    public int esperar() {
        if(estado == EEstadoProcesso.EM_ESPERA){
            tempoDeEspera--;
            if(tempoDeEspera == 0){
                alterarEstado(EEstadoProcesso.PRONTO);
            }
        }
        return tempoDeEspera;
    }

    public int[] getTemposDeTurnaround() {
        return temposDeTurnaround;
    }

    public int getTurnaround() {
        return turnaround;
    }

    public int proximoTempoDeIO(){
        return proximoIO;
    }

    public int definirProximoIO(int proximoIO) {
        this.proximoIO = proximoIO;
        if(proximoIO >= instantesIO.length){
            instantesIO = null;
            this.proximoIO = -1;
        }
        return this.proximoIO;
    }

    public int aumentarTempoTotalDeExecucao() {
        tempoTotalDeExecucao += TEMPO_DE_IO;
        return tempoTotalDeExecucao;
    }

    public int tempoTotalDeExecucao(){
        return tempoTotalDeExecucao;
    }

    public int tempoRestante() {
        return tempoTotalDeExecucao - turnaround;
    }

}
