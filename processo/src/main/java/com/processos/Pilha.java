package com.processos;

public class Pilha<T> {
    Celula<T> topo;
    Celula<T> fundo;

    public Pilha(){
        Celula<T> sentinela = new Celula<T>(null);
        topo = fundo = sentinela;
    }

    public boolean estahVazia(){
        return fundo == topo;
    }

    public T empilhar(T item){
        Celula<T> novo = new Celula<T>(item);
        novo.setProximo(topo);
        topo = novo;
        return topo.getItem();
    }

    public T desempilhar(){
        Celula<T> desempilhado = null;
        if(!estahVazia()){
            desempilhado = topo;
            topo = topo.getProximo();
        }
        return desempilhado.getItem();
    }

    @Override
    public String toString() {
        Celula<T> ref = topo;
        StringBuilder s = new StringBuilder("Pilha:\n");

        while (ref != fundo) {
            s.append(ref.getItem() + "\n");
            ref = ref.getProximo();
        }
        
        return s.toString();
    }
}
