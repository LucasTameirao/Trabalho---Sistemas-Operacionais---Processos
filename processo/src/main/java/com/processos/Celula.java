package com.processos;

public class Celula<T> {
    private T item;
    private Celula<T> proximo;

    public Celula(T item){
        this.item = item;
        this.proximo = null;
    }

    public T getItem(){
        return item;
    }

    public Celula<T> getProximo(){
        return proximo;
    }

    public Celula<T> setProximo(Celula<T> nova){
        proximo = nova;
        return proximo;
    }
}
