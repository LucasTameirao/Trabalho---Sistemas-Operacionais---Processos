package com.processos;

import java.util.List;

/**
 * Calcula e exibe as métricas exigidas pelo trabalho:
 *  - Tempo de Espera Médio
 *  - Tempo de Retorno (Turnaround) Médio
 *  - Vazão (Throughput)
 *
 * Para que os cálculos estejam corretos, cada Processo deve ter
 * setInstanteDeFim(tempo) chamado no momento em que ele finaliza.
 */
public class Metricas {

    private final String nomeAlgoritmo;
    private final List<Processo> processos;
    private final int tempoTotal;

    public Metricas(String nomeAlgoritmo, List<Processo> processos, int tempoTotal) {
        this.nomeAlgoritmo = nomeAlgoritmo;
        this.processos     = processos;
        this.tempoTotal    = tempoTotal;
    }

    /** Turnaround médio = média de (instante de fim − instante de chegada) */
    public double turnaroundMedio() {
        double soma = 0;
        for (Processo p : processos) {
            soma += p.getTurnaroundReal();
        }
        return soma / processos.size();
    }

    /**
     * Tempo de espera médio = média de (turnaround real − burst de CPU).
     * O burst de CPU é o burstTotal original do processo (sem contar I/O).
     */
    public double tempoEsperaMedio() {
        double soma = 0;
        for (Processo p : processos) {
            soma += p.getTempoEsperaReal();
        }
        return soma / processos.size();
    }

    /** Vazão = nº de processos concluídos / tempo total de execução */
    public double throughput() {
        return (double) processos.size() / tempoTotal;
    }

    /** Imprime o relatório formatado no console. */
    public void imprimir() {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.printf ("║  Métricas — %-30s║%n", nomeAlgoritmo);
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.printf ("║  Processos concluídos : %-17d║%n", processos.size());
        System.out.printf ("║  Tempo total          : %-17d║%n", tempoTotal);
        System.out.printf ("║  Turnaround médio     : %-17.2f║%n", turnaroundMedio());
        System.out.printf ("║  Tempo de espera méd. : %-17.2f║%n", tempoEsperaMedio());
        System.out.printf ("║  Throughput (proc/ut) : %-17.4f║%n", throughput());
        System.out.println("╚══════════════════════════════════════════╝");
    }

    /**
     * Imprime uma tabela comparativa com os resultados de todos os algoritmos.
     */
    public static void imprimirComparativo(List<Metricas> lista) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    COMPARATIVO DOS ALGORITMOS                        ║");
        System.out.println("╠══════════════╦═══════════════╦═══════════════╦═══════════════════════╣");
        System.out.println("║  Algoritmo   ║ Turnaround M. ║  Espera Méd.  ║  Throughput (p/ut)    ║");
        System.out.println("╠══════════════╬═══════════════╬═══════════════╬═══════════════════════╣");
        for (Metricas m : lista) {
            System.out.printf("║ %-12s ║ %13.2f ║ %13.2f ║ %21.4f ║%n",
                    m.nomeAlgoritmo, m.turnaroundMedio(), m.tempoEsperaMedio(), m.throughput());
        }
        System.out.println("╚══════════════╩═══════════════╩═══════════════╩═══════════════════════╝");
    }
}