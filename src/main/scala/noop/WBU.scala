package noop

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import utils._

class WBU(implicit val p: NOOPConfig) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new CommitIO))
    val wb = new WriteBackIO
    val redirect = new RedirectIO
  })

  io.wb.rfWen := io.in.bits.decode.ctrl.rfWen && io.in.valid
  io.wb.rfDest := io.in.bits.decode.ctrl.rfDest
  io.wb.rfData := io.in.bits.commits(io.in.bits.decode.ctrl.fuType)
  io.in.ready := true.B

  io.redirect := io.in.bits.decode.cf.redirect
  io.redirect.valid := io.in.bits.decode.cf.redirect.valid && io.in.valid

  BoringUtils.addSource(io.in.valid, "perfCntCondMinstret")
  if (!p.FPGAPlatform) {
    BoringUtils.addSource(RegNext(io.in.valid), "difftestCommit")
    BoringUtils.addSource(RegNext(io.in.bits.decode.cf.pc), "difftestThisPC")
    BoringUtils.addSource(RegNext(io.in.bits.isMMIO), "difftestIsMMIO")
    BoringUtils.addSource(RegNext(io.in.bits.intrNO), "difftestIntrNO")
  }
}
