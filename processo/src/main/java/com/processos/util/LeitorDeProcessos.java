
package com.processos.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

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
            arquivoFormatado.append(scanner.next() + "\n");
        }

        return arquivoFormatado.toString();
    }

    public static Processo[] criarProcessos(){

        String arquivoTexto = lerArquivoDeProcessos();

        if (arquivoTexto.isBlank()) {
            throw new IllegalStateException("O arquivo está vazio");
        }
        
        String[] linhas = arquivoTexto.split("\n");

        Processo[] processos = new Processo[linhas.length];

        for(int i = 0; i < linhas.length; i++){
            System.out.println(linhas[i]);
        }

        System.out.println(processos.length);
        
        return null;
    }
}