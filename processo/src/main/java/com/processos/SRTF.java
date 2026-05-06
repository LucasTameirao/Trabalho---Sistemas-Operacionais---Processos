import java.util.ArrayList;
import java.util.List;

public class SRTF {
    private static List<Processo> processos = new ArrayList<>();
    private static int tempo = 0;

    private static List<Processo> processosProntos = new ArrayList<>();
    private static List<Processo> processosEmEspera = new ArrayList<>();

    private static void lerProcessos() {
        processos.addAll(List.of(LeitorDeProcessos.criarProcessos()));
        Processo novoProcesso = processos.getFirst();
        definirProcessoComoPronto(novoProcesso);
        processos.remove(novoProcesso);
    }

    public static int iniciarSimulacao() {
        lerProcessos();
        tempo = executarProcessos();
        return tempo;
    }

    private static int executarProcessos() {
        Processo emExecucao;

        while (temProcessosProntos() || !processosEmEspera.isEmpty()) {
            while (!processosEmEspera.isEmpty() && !temProcessosProntos()) {
                esperar();
                tempo++;

                if (temNovosProcessos()) {
                    Processo novoProcesso = processos.getFirst();
                    if (novoProcesso.getChegada() <= tempo) {
                        definirProcessoComoPronto(novoProcesso);
                        processos.remove(novoProcesso);
                    }
                }
            }

            emExecucao = executaMenorTempoRestante();

            System.out.println("processo " + emExecucao.getPid() + " tempo restante: " + emExecucao.tempoRestante());

            while (emExecucao.tempoRestante() > 0) {

                emExecucao.executarProcesso();
                tempo++;
                esperar();

                System.out.println("tempo " + tempo + " PID em execucao: " + emExecucao.getPid() + " estante: " + emExecucao.tempoRestante());

                if (temNovosProcessos()) {
                    Processo novoProcesso = processos.getFirst();
                    if (novoProcesso.getChegada() == tempo) {
                        definirProcessoComoPronto(novoProcesso);
                        processos.remove(novoProcesso);
                        System.out.println("processo " + novoProcesso.getPid() + " chegou no tempo " + tempo);

                        if (devePreemptar(emExecucao)) {
                            System.out.println("processo " + emExecucao.getPid() + "novo menor: " + menorTempoRestante().getPid());
                            devolverParaProntos(emExecucao);
                            emExecucao = executaMenorTempoRestante();
                        }
                    }
                }

                if (emExecucao.getInstantesIO() != null) {
                    int proximoIO = emExecucao.proximoTempoDeIO();
                    if (emExecucao.getTurnaround() == emExecucao.getInstantesIO()[proximoIO]) {
                        System.out.println("processo " + emExecucao.getPid() + " fez I/O no tempo " + tempo);
                        proximoIO++;
                        emExecucao.definirProximoIO(proximoIO);
                        emExecucao.aumentarTempoTotalDeExecucao();
                        colocarProcessoEmEspera(emExecucao);
                        break;
                    }
                }
            }
        }

        return tempo;
    }

    private static Processo menorTempoRestante() {
        Processo menor = processosProntos.getFirst();
        for (Processo p : processosProntos) {
            if (p.tempoRestante() < menor.tempoRestante()) {
                menor = p;
            }
        }
        return menor;
    }

    private static Processo executaMenorTempoRestante() {
        Processo p = menorTempoRestante();
        p.alterarEstado(EEstadoProcesso.EXECUTANDO);
        processosProntos.remove(p);
        return p;
    }

    private static boolean devePreemptar(Processo processoAtual) {
        if (!temProcessosProntos()) {
            return false;
        }
        Processo candidato = menorTempoRestante();
        return candidato.tempoRestante() < processoAtual.tempoRestante();
    }

    private static void devolverParaProntos(Processo p) {
        p.alterarEstado(EEstadoProcesso.PRONTO);
        processosProntos.add(p);
    }

    private static boolean temNovosProcessos() {
        return !processos.isEmpty();
    }

    private static boolean temProcessosProntos() {
        return !processosProntos.isEmpty();
    }

    private static void colocarProcessoEmEspera(Processo p) {
        if (p.colocarEmEspera() == EEstadoProcesso.EM_ESPERA) {
            processosEmEspera.add(p);
        }
        if (processosProntos.contains(p)) {
            processosProntos.remove(p);
        }
    }

    private static void definirProcessoComoPronto(Processo p) {
        processosProntos.add(p);
        p.alterarEstado(EEstadoProcesso.PRONTO);
        if (processosEmEspera.contains(p)) {
            processosEmEspera.remove(p);
        }
    }

    private static void esperar(){
        for(int i = 0; i < processosEmEspera.size(); i++){
            Processo p = processosEmEspera.get(i);
            System.out.println(p.esperar());
            System.out.println("processo " + p.getPid() + " esperou");
            System.out.println(p.estadoProcesso());
            if (p.estadoProcesso() == EEstadoProcesso.PRONTO) {
                System.out.println("FICOU PRONTO E FOI PRA LISTA DE PRONTOS");
                definirProcessoComoPronto(p);
            }
        }
    }