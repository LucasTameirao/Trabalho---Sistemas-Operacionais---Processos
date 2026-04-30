
package com.processos.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import com.processos.Processo;

public class LeitorDeProcessos {

    private static String lerArquivoDeProcessos(){
        File arquivo;
        Scanner scanner;
        StringBuilder arquivoFormatado = new StringBuilder("");
        try{
            arquivo = new File("processo/processos.txt");
        }
        catch(NullPointerException e){
            arquivo = null;
            e.printStackTrace();
        }
        try{
            scanner = new Scanner(arquivo);
        }
        catch(FileNotFoundException e){
            scanner = null;
            e.printStackTrace();
        }

        while (scanner.hasNext()) {
            arquivoFormatado.append(scanner.nextLine() + "\n");
        }

        return arquivoFormatado.toString();
    }

    public static Processo[] criarProcessos(){

        String arquivoTexto = lerArquivoDeProcessos();
        String[] linhas = null;

        if (arquivoTexto.isBlank()) {
            throw new IllegalStateException("O arquivo está vazio");
        }
        
        linhas = arquivoTexto.split("\n");

        Processo[] processos = new Processo[linhas.length];

        for(int i = 0; i < linhas.length; i++){
            String[] dados = linhas[i].split(";");
            processos[i] = new Processo(dados);
        }
        
        return processos;
    }
}