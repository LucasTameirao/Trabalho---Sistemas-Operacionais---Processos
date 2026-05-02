package com.processos;

public class Processo {
    private int pid;
    private int chegada;
    private int burstTotal;
    private int prioridade;
    private int[] instantesIO;
    final int TEMPO_DE_IO = 5;

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
        int tempo = 0;
        if(instantesIO != null){
            for(int i = 0; i < instantesIO.length; i++){
                tempo += TEMPO_DE_IO;
            }
        }
        
        tempo += burstTotal;
        return tempo;
    }
}
