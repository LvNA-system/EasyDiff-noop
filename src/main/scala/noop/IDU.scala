package noop

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._

class IDU extends NOOPModule with HasInstrType {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new CtrlFlowIO))
    val out = Decoupled(new DecodeIO)
  })

  val hasIntr = Wire(Bool())
  val instr = io.in.bits.instr
  val decodeList = ListLookup(instr, Instructions.DecodeDefault, Instructions.DecodeTable)
  val instrType :: fuType :: fuOpType :: Nil = // insert Instructions.DecodeDefault when interrupt comes
    Instructions.DecodeDefault.zip(decodeList).map{case (intr, dec) => Mux(hasIntr, intr, dec)}

  io.out.bits := DontCare

  io.out.bits.ctrl.fuType := fuType
  io.out.bits.ctrl.fuOpType := fuOpType

  val SrcTypeTable = List(
    InstrI -> (SrcType.reg, SrcType.imm),
    InstrR -> (SrcType.reg, SrcType.reg),
    InstrS -> (SrcType.reg, SrcType.reg),
    InstrB -> (SrcType.reg, SrcType.reg),
    InstrU -> (SrcType.pc , SrcType.imm),
    InstrJ -> (SrcType.pc , SrcType.imm),
    InstrN -> (SrcType.pc , SrcType.imm)
  )
  val src1Type = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._1)))
  val src2Type = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._2)))

  val (rs, rt, rd) = (instr(19, 15), instr(24, 20), instr(11, 7))
  // make non-register addressing to zero, since isu.sb.isBusy(0) === false.B
  io.out.bits.ctrl.rfSrc1 := Mux(src1Type === SrcType.pc, 0.U, rs)
  io.out.bits.ctrl.rfSrc2 := Mux(src2Type === SrcType.reg, rt, 0.U)
  io.out.bits.ctrl.rfWen := isrfWen(instrType)
  io.out.bits.ctrl.rfDest := Mux(isrfWen(instrType), rd, 0.U)

  io.out.bits.data := DontCare
  io.out.bits.data.imm  := LookupTree(instrType, List(
    InstrI  -> SignExt(instr(31, 20), XLEN),
    InstrS  -> SignExt(Cat(instr(31, 25), instr(11, 7)), XLEN),
    InstrB  -> SignExt(Cat(instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W)), XLEN),
    InstrU  -> SignExt(Cat(instr(31, 12), 0.U(12.W)), XLEN),//fixed
    InstrJ  -> SignExt(Cat(instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W)), XLEN)
  ))

  when (fuType === FuType.alu) {
    when (rd === 1.U && fuOpType === ALUOpType.jal) { io.out.bits.ctrl.fuOpType := ALUOpType.call }
    when (rs === 1.U && fuOpType === ALUOpType.jalr) { io.out.bits.ctrl.fuOpType := ALUOpType.ret }
  }
  // fix LUI
  io.out.bits.ctrl.src1Type := Mux(instr(6,0) === "b0110111".U, SrcType.reg, src1Type)
  io.out.bits.ctrl.src2Type := src2Type

  io.out.bits.cf <> io.in.bits

  val intrVec = WireInit(0.U(12.W))
  BoringUtils.addSink(intrVec, "intrVecIDU")
  io.out.bits.cf.intrVec.zip(intrVec.asBools).map{ case(x, y) => x := y }
  hasIntr := intrVec.orR

  io.out.bits.cf.exceptionVec.map(_ := false.B)
  io.out.bits.cf.exceptionVec(illegalInstr) := (instrType === InstrN && !hasIntr) && io.in.valid
  io.out.bits.cf.exceptionVec(ecallM) := (instr === RVZicsrInstr.ECALL) && io.in.valid

  io.out.bits.ctrl.isNoopTrap := (instr === NOOPTrap.TRAP) && io.in.valid
  io.out.valid := io.in.valid

  io.in.ready := (!io.in.valid || io.out.fire()) && !hasIntr
}
