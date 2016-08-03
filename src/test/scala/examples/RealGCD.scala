// See LICENSE for license details.

package examples

import Chisel._
import chisel3.iotesters.{SteppedHWIOTester, ChiselFlatSpec, OrderedDecoupledHWIOTester}

object RealGCD {
  val num_width = 16
}

object GCDCalculator {
  def computeGcdResultsAndCycles(a: Int, b: Int, depth: Int = 1): (Int, Int) = {
    if(b == 0) {
      (a, depth)
    }
    else {
      computeGcdResultsAndCycles(b, a%b, depth+1 )
    }
  }
}

class RealGCDInput extends Bundle {
  val a = Bits(width = RealGCD.num_width)
  val b = Bits(width = RealGCD.num_width)
}

class RealGCD extends Module {
  val io  = new Bundle {
    val in  = Decoupled(new RealGCDInput()).flip()
    val out = Valid(UInt(width = RealGCD.num_width))
  }

  val x = Reg(UInt(width = RealGCD.num_width))
  val y = Reg(UInt(width = RealGCD.num_width))
  val p = Reg(init=Bool(false))

  val ti = Reg(init=UInt(0, width = RealGCD.num_width))
  ti := ti + UInt(1)

  io.in.ready := !p

  when (io.in.valid && !p) {
    x := io.in.bits.a
    y := io.in.bits.b
    p := Bool(true)
  }

  when (p) {
    when (x > y)  { x := y; y := x }
      .otherwise    { y := y - x }
  }

  printf("ti %d  x %d y %d  in_ready %d  in_valid %d  out %d  out_ready %d  out_valid %d==============\n",
      ti, x, y, io.in.ready, io.in.valid, io.out.bits, UInt(0), io.out.valid)
//      ti, x, y, io.in.ready, io.in.valid, io.out.bits, io.out.ready, io.out.valid)

  io.out.bits  := x
  io.out.valid := y === Bits(0) && p
  when (io.out.valid) {
    p := Bool(false)
  }
}

class RealGCDTests extends SteppedHWIOTester {
  val device_under_test = Module( new RealGCD )
  val c = device_under_test

  val inputs = List( (48, 32), (7, 3), (100, 10) )
  val outputs = List( 16, 1, 10)

  for ((input_1, input_2) <- inputs) {
    val (output, cycles) = GCDCalculator.computeGcdResultsAndCycles(input_1, input_2)

    poke(c.io.in.bits.a, input_1)
    poke(c.io.in.bits.b, input_2)
    poke(c.io.in.valid, 1)

    step(1)
    expect(c.io.in.ready, 1)
    poke(c.io.in.valid, 0)
    step(1)

    step(cycles - 2)
    expect(c.io.out.bits, output)
  }
}

class DecoupledRealGCDTestHandCodedExample extends OrderedDecoupledHWIOTester {
  val device_under_test = Module(new RealGCD())
  val c = device_under_test

  val a_values = Vec(Array(UInt(12, width = 16), UInt(33, width = 16)))
  val b_values = Vec(Array(UInt(24, width = 16), UInt(24, width = 16)))

  val ti = Reg(init=UInt(0, width = 16))
  val pc = Reg(init=UInt(0, width = 16))
  val oc = Reg(init=UInt(0, width = 16))

  val in_done  = Reg(init=Bool(false))
  val out_done = Reg(init=Bool(false))

  ti := ti + UInt(1)
  when(ti >= UInt(50)) { stop() }
  when(in_done && out_done) { stop() }

  //printf("ti %d pc %d oc %d in_ready %d out_valid %d==============",
  //    ti, pc, oc, c.io.in.ready, c.io.out.valid)
  when(!in_done && c.io.in.ready) {
    //    printf(s"pc %d a %d b %d", pc, a_values(pc), b_values(pc))
    c.io.in.bits.a := a_values(pc)
    c.io.in.bits.b := b_values(pc)
    c.io.in.valid  := Bool(true)
    pc := pc + UInt(1)
    when(pc >= UInt(a_values.length)) {
      in_done := Bool(true)
    }
  }

  val c_values = Vec(Array(UInt(12, width = 16), UInt(3, width = 16)))
//  c.io.out.ready := Bool(true)

  when(!out_done && c.io.out.valid) {
    logPrintfDebug("oc %d   got %d   expected %d\n", oc, c.io.out.bits, c_values(oc))
    assert(c.io.out.bits === c_values(oc))
//    c.io.out.ready := Bool(true)
    oc := oc + UInt(1)
    when(oc >= UInt(c_values.length)) {
      out_done := Bool(true)
    }
  }
  //  finish()
  //  io_info.show_ports("".r)
}

class DecoupledRealGCDTests4 extends OrderedDecoupledHWIOTester {
  val device_under_test = Module(new RealGCD())
  val c = device_under_test

  for {
    i <- 1 to 10
    j <- 1 to 10
  } {
    val (gcd_value, cycles) = GCDCalculator.computeGcdResultsAndCycles(i, j)

    inputEvent(c.io.in.bits.a -> i, c.io.in.bits.b -> j)
    outputEvent(c.io.out.bits -> gcd_value)
  }
}

class DecoupledRealGCDTester extends ChiselFlatSpec {
  "RealGCD using decoupledIO input interface and validIO output interface" should "return good values" in {
    assertTesterPasses { new DecoupledRealGCDTests4 }
  }
}

