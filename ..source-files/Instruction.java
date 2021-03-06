//TFG 2013-2014
package architecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 *
 * @author Belen Bermejo Gonzalez
 */
public class Instruction implements MIPS {

    /*Atributes of all instructions*/
    private final String opcode;
    private final String operands;
    private final int nLine;
    private String[] operandsList;

    private static int error = 0;
    private boolean isJump = false;
    HashMap<String, Integer> registers = Architecture.getRegisters();

    private ArrayList<Dependence> listDependences = new ArrayList();
    private static final Collection<String> load = new ArrayList();
    private static final Collection<String> store = new ArrayList();
    private static final Collection<String> aluIm = new ArrayList();
    private static final Collection<String> alu = new ArrayList();
    private static final Collection<String> shift = new ArrayList();
    private static final Collection<String> mulDiv = new ArrayList();
    private static final Collection<String> jump = new ArrayList();
    private final ArrayList<Integer> signal = new ArrayList(); //a quien hay qye evisar

    /*To execute pipeline -> finish cycle of each stage*/
    private int fetch;
    private int decode;
    private int exec;
    private int mem;
    private int wr;
    private int commit;

    /*Cycle start of each stagr*/
    private int Sfetch;
    private int Sdecode;
    private int Sexec;
    private int Smem;
    private int Swr;
    private int Scommit;

    /*Signals*/
    private boolean RAW = false;
    private boolean WAR = false;
    private boolean WAW = false;

    /*To decode*/
    private final Collection<String> read = new ArrayList();
    private final Collection<String> write = new ArrayList();

    private boolean isSource = false; //the instruction is a master of dependence
    private boolean stop; //the instruction must to be stoped
    private int cycleDecode;
    private boolean flagDecode = false;
    private typeFU FU;

    private instState state; //instruction state

    //List of branch that can speculated one instruction
    ArrayList<Integer> speculate = new ArrayList<>();

    /*What is an instruction*/
    public Instruction(String op, String operands, int nLine) {
        this.opcode = op;
        this.operands = operands;
        this.nLine = nLine;
    }

    /*Method that add a speculate instruction*/
    public void addSpeculation(Instruction in) {
        int instIndex = in.getIDIns() - 1;
        speculate.add(instIndex);
    }

    public boolean containsInsSpec(int idBrach) {
        boolean yes = false;
        if (speculate.contains(idBrach - 1)) { //si contiene el indice del  la ins culpable
            yes = true;
        }
        return yes;
    }

    public void addSignal(int s) {
        signal.add(s);
    }

    public void addDependence(Dependence d) {
        listDependences.add(d);
        switch (d.getType()) {
            case "RAW":
                RAW = true;
                flagDecode = true; //la instruccion tiene que mirar si tiene que leer el ciclo dw write
                break;
            case "WAR":
                WAR = true;
                break;
            case "WAW":
                WAW = true;
                break;
            default:
                System.out.println("Dependence non-define");
                break;
        }
    }

    public void showDep() {
        for (int i = 0; i < listDependences.size(); i++) {
            Dependence dep = listDependences.get(i);
            System.out.println(dep.getIdSrc());
            System.out.println(dep.getIdCmp());
            System.out.println(dep.getType());
        }
    }

    public static void createInsSet() {
        load.addAll(Arrays.asList("LB", "LBU", "LH", "LHU", "LW", "LWL", "LWR"));
        store.addAll(Arrays.asList("SB", "SH", "SW", "SWL", "SWR"));
        aluIm.addAll(Arrays.asList("ADDI", "ADDIU", "SLTI", "SLTIU", "ANDI", "ORI", "XORI", "LUI"));
        alu.addAll(Arrays.asList("ADD", "ADDU", "SUB", "SUBU", "SLT", "SLTU", "AND", "OR", "XOR", "NOR"));
        shift.addAll(Arrays.asList("SLL", "SRL", "SRA", "SLLV", "SRLV", "SRAV"));
        mulDiv.addAll(Arrays.asList("MULT", "MULTU", "DIV", "DIVU", "MFHI", "MTHI", "MFLO", "MTLO"));
        jump.addAll(Arrays.asList("J", "JAL", "JR", "JALR", "JALR", "BEQ", "BNE", "BLEZ", "BGTZ", "BLTZ", "BGEZ",
                "BLTZAL", "BGEZAL"));
    }

    /*Function that divide operands in read or write depending on the opcode
     we have, taking in account the format instruction*/
    public void decodeInstruction() {
        if (load.contains(this.opcode)) {
            /*load rt, offset(base)*/
            read.add(operandsList[1]);
            write.add(operandsList[0]);
            Statistics.setLoad(1);
        } else if (store.contains(this.opcode)) {
            read.add(operandsList[0]);
            write.add(operandsList[1]);
            Statistics.setStore(1);
        } else if (aluIm.contains(this.opcode)) {
            write.add(operandsList[0]);
            read.add(operandsList[1]);
            read.add(operandsList[2]);
            Statistics.setAluIm(1);
        } else if (alu.contains(this.opcode)) {
            write.add(operandsList[0]);
            read.add(operandsList[1]);
            read.add(operandsList[2]);
            Statistics.setAlu(1);
        } else if (shift.contains(this.opcode)) {
            write.add(operandsList[0]);
            read.add(operandsList[1]);
            read.add(operandsList[2]);
            Statistics.setShift(1);
        } else if (mulDiv.contains(this.opcode)) {
            if (this.opcode.equals("MFHI") || this.opcode.equals("MFLO") || this.opcode.equals("MTHI")
                    || this.opcode.equals("MTLO")) { //only move to Rd
                write.add(operandsList[0]);
            } else {
                read.add(operandsList[0]);
                read.add(operandsList[1]);
            }
            Statistics.setMulDiv(1);
        } else if (jump.contains(this.opcode)) {
            if (this.opcode.equals("J") || this.opcode.equals("JAL") || this.opcode.equals("JR")) {
                read.add(operandsList[0]);
            } else if (this.opcode.equals("BEQ") || this.opcode.equals("BNE")) {
                read.add(operandsList[0]);
                read.add(operandsList[1]);
                read.add(operandsList[2]);
            } else {
                read.add(operandsList[0]);
                read.add(operandsList[1]);
            }
            Statistics.setJump(1);
        }
    }

    public boolean checkOpcode() {
        boolean opcodeOK = false;
        if (load.contains(this.opcode) || store.contains(this.opcode) || aluIm.contains(this.opcode)
                || alu.contains(this.opcode) || shift.contains(this.opcode) || mulDiv.contains(this.opcode)
                || jump.contains(this.opcode)) {
            if (jump.contains(this.opcode)) {
                isJump = true;
            }
            opcodeOK = true;

        } else {
            System.out.println("Instruction " + this.nLine + " opcode error " + this.opcode);
            error++;
        }
        return opcodeOK;
    }

    public boolean checkOperand() {
        boolean operandOK = false;
        operandsList = this.operands.split(",");
        for (int i = 0; i < operandsList.length; i++) {
            String ID = (String) operandsList[i].subSequence(0, 1);
            if (ID.equals("r")) {
                if (registers.containsKey(operandsList[i])) {
                    operandOK = true;
                } else {
                    error++;
                    System.out.println("Instruction " + SourceFile.getIdIns() + " operand error " + operandsList[i]);
                }
            } else if (isNumber(operandsList[i])) {
                operandOK = true;
            } else {
                if (isJump) {
                    if (Architecture.checkBrachPoint(operandsList[i])) {
                        operandOK = true;
                    } else {
                        error++;
                        operandOK = false;
                    }
                } else {
                    if (Memory.correctPos(operandsList[i])) {
                        operandOK = true;
                    } else {
                        error++;
                        operandOK = false;
                    }
                }
            }
        }
        return operandOK;
    }

    public ArrayList<Dependence> getListDependences() {
        return listDependences;
    }

    public ArrayList<Integer> getSignal() {
        return signal;
    }

    private boolean isNumber(String number) {
        return number.matches("[01]+") || number.matches("-?\\d+(\\.\\d+)?");
    }

    public Collection<String> getRead() {
        return read;
    }

    public Collection<String> getWrite() {
        return write;
    }

    public static int getError() {
        return error;
    }

    public String[] getListOperands() {
        return operandsList;
    }

    public int getIDIns() {
        return nLine;
    }

    public String getOpcode() {
        return opcode;
    }

    public String getOperands() {
        return operands;
    }

    public int getFetch() {
        return fetch;
    }

    public int getDecode() {
        return decode;
    }

    public int getExec() {
        return exec;
    }

    public int getMem() {
        return mem;
    }

    public int getWr() {
        return wr;
    }

    public int getCommit() {
        return commit;
    }

    public void setCommit(int commit) {
        this.commit = commit;
    }

    public void setFetch(int fetch) {
        this.fetch = fetch;
    }

    public void setDecode(int decode) {
        this.decode = decode;
    }

    public void setExec(int exec) {
        this.exec = exec;
    }

    public void setMem(int mem) {
        this.mem = mem;
    }

    public void setWr(int wr) {
        this.wr = wr;
    }

    public void setRAW(boolean RAW) {
        this.RAW = RAW;
    }

    public void setWAR(boolean WAR) {
        this.WAR = WAR;
    }

    public void setWAW(boolean WAW) {
        this.WAW = WAW;
    }

    public boolean isRAW() {
        return RAW;
    }

    public boolean isWAR() {
        return WAR;
    }

    public boolean isWAW() {
        return WAW;
    }

    public void setIsSource(boolean isSource) {
        this.isSource = isSource;
    }

    public boolean isIsSource() {
        return isSource;
    }

    public int getSfetch() {
        return Sfetch;
    }

    public int getSdecode() {
        return Sdecode;
    }

    public int getSexec() {
        return Sexec;
    }

    public int getSmem() {
        return Smem;
    }

    public int getSwr() {
        return Swr;
    }

    public int getScommit() {
        return Scommit;
    }

    public void setSfetch(int Sfetch) {
        this.Sfetch = Sfetch;
    }

    public void setSdecode(int Sdecode) {
        this.Sdecode = Sdecode;
    }

    public void setSexec(int Sexec) {
        this.Sexec = Sexec;
    }

    public void setSmem(int Smem) {
        this.Smem = Smem;
    }

    public void setSwr(int Swr) {
        this.Swr = Swr;
    }

    public void setScommit(int Scommit) {
        this.Scommit = Scommit;
    }

    public ArrayList<Integer> getSpeculate() {
        return speculate;
    }

    public void setState(instState state) {
        this.state = state;
    }

    public instState getState() {
        return state;
    }

    public boolean isIsJump() {
        return isJump;
    }

    public typeFU getFU() {
        return FU;
    }

    public void setFU(typeFU FU) {
        this.FU = FU;
    }

    public boolean isFlagDecode() {
        return flagDecode;
    }

    public void setFlagDecode(boolean f) {
        flagDecode = f;
    }

    public void setCycleDecode(int cycleDecode) {
        this.cycleDecode = cycleDecode;
    }

    public int getCycleDecode() {
        return cycleDecode;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public boolean isStop() {
        return stop;
    }

    @Override
    public String toString() {
        String msg = opcode +" "+ operands + "\r\n";
        return msg; //To change body of generated methods, choose Tools | Templates.
    }
}
