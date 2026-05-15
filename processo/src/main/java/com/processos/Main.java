package com.processos;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        List<Metricas> resultados = new ArrayList<>();

        // ── 1. FCFS ──────────────────────────────────────────────────────────────
        System.out.println("\n========== FCFS ==========");
        resultados.add(FCFS.iniciarSimulacao());

        // ── 2. SRTF ──────────────────────────────────────────────────────────────
        System.out.println("\n========== SRTF ==========");
        resultados.add(SRTF.iniciarSimulacao());

        // ── 3. Round-Robin com Quantum por Predição ───────────────────────────────
        System.out.println("\n========== Round-Robin Preditivo ==========");
        resultados.add(RRPreditivo.iniciarSimulacao());

        // ── 4. MLQ ───────────────────────────────────────────────────────────────
        System.out.println("\n========== MLQ ==========");
        resultados.add(MLQ.iniciarSimulacao());

        // ── Comparativo final ─────────────────────────────────────────────────────
        Metricas.imprimirComparativo(resultados);
    }
}