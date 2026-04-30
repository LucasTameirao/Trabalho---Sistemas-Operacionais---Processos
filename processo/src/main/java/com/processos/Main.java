package com.processos;

import com.processos.util.LeitorDeProcessos;

public class Main {
    public static void main(String[] args) {
        Processo[] processos = LeitorDeProcessos.criarProcessos();

        for(Processo p : processos){
            System.out.println(p);
        }
    }
}