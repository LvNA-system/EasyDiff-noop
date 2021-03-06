package noop

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._
import bus.simplebus._

trait HasResetVector {
  val resetVector = 0x80000000L//TODO: set reset vec
}

class IFU extends NOOPModule with HasResetVector {
  val io = IO(new Bundle {
    val imem = new SimpleBusUC(userBits = AddrBits*2)
    val out = Decoupled(new CtrlFlowIO)
    val redirect = Flipped(new RedirectIO)
    val flushVec = Output(UInt(4.W))
    val bpFlush = Output(Bool())
  })

  // pc
  val pc = RegInit(resetVector.U(AddrBits.W))
  val pcUpdate = io.redirect.valid || io.imem.req.fire()
  val snpc = pc + 4.U  // sequential next pc

  val bp1 = Module(new BPU1)
  // predicted next pc
  val pnpc = bp1.io.out.target
  val npc = Mux(io.redirect.valid, io.redirect.target, Mux(bp1.io.out.valid, pnpc, snpc))

  bp1.io.in.pc.valid := io.imem.req.fire() // only predict when Icache accepts a request
  bp1.io.in.pc.bits := npc  // predict one cycle early
  bp1.io.flush := io.redirect.valid

  when (pcUpdate) { pc := npc }

  io.flushVec := Mux(io.redirect.valid, "b1111".U, 0.U)
  io.bpFlush := false.B

  io.imem.req.bits.apply(addr = Cat(pc(AddrBits-1,2),0.U(2.W)), //cache will treat it as Cat(pc(63,3),0.U(3.W))
    size = "b11".U, cmd = SimpleBusCmd.read, wdata = 0.U, wmask = 0.U, user = Cat(npc, pc))
  io.imem.req.valid := io.out.ready
  io.imem.resp.ready := io.out.ready || io.flushVec(0)

  io.out.bits := DontCare
    //inst path only uses 32bit inst, get the right inst according to pc(2)
  io.out.bits.instr := (if (XLEN == 64) io.imem.resp.bits.rdata.asTypeOf(Vec(2, UInt(32.W)))(io.out.bits.pc(2))
                       else io.imem.resp.bits.rdata)
  io.imem.resp.bits.user.map{ case x =>
    io.out.bits.pc := x(AddrBits-1,0)
    io.out.bits.pnpc := x(AddrBits*2-1,AddrBits)
  }
  io.out.valid := io.imem.resp.valid && !io.flushVec(0)

  BoringUtils.addSource(BoolStopWatch(io.imem.req.valid, io.imem.resp.fire()), "perfCntCondMimemStall")
  BoringUtils.addSource(io.flushVec.orR, "perfCntCondMifuFlush")
}
